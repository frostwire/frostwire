/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.peer.PeerRecord;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages rUDP sessions, hole punching, and relay forwarding for an IceBridge
 * servent.
 *
 * <p>All packets received by {@link RudpServer} are dispatched here. The
 * manager maintains a table of sessions keyed by remote connection id, sends
 * acknowledgements and retransmissions, and uses the {@link PeerRegistry} to
 * resolve relay/hole-punch targets.
 */
public final class RudpSessionManager {

    private static final Logger LOG = Logger.getLogger(RudpSessionManager.class);

    private static final long RETRANSMIT_INTERVAL_MS = 500;
    private static final long RETRANSMIT_TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 5;
    private static final long SESSION_IDLE_MS = 120_000;

    /**
     * Fragment header prepended to each DATA_FRAG / DATA_END payload:
     * [groupId(4)][fragIndex(4)][totalFrags(4)].
     */
    private static final int FRAG_HEADER_SIZE = 12;

    private final IdentityKeys identity;
    private final PeerRegistry registry;
    private final IceBridgeMetrics metrics;
    private final RudpMessageListener messageListener;
    private final FragmentReassembler reassembler = new FragmentReassembler();

    private final Map<Long, RudpSession> sessionsByRemoteId = new ConcurrentHashMap<>();
    private final Map<InetSocketAddress, RudpSession> sessionsByAddress = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final ScheduledExecutorService scheduler;

    private Channel channel;

    public RudpSessionManager(IdentityKeys identity,
                              PeerRegistry registry,
                              IceBridgeMetrics metrics,
                              RudpMessageListener messageListener) {
        this.identity = identity;
        this.registry = registry;
        this.metrics = metrics;
        this.messageListener = messageListener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "icebridge-rudp-manager");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleWithFixedDelay(this::retransmitAndEvict,
                RETRANSMIT_INTERVAL_MS, RETRANSMIT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    /**
     * Send application data to a remote endpoint identified by public key.
     *
     * <p>If the registry knows the target's rUDP endpoint, a direct packet is
     * sent; otherwise the data is relayed through the first known forwarder.
     */
    public void deliver(byte[] targetPub, byte[] payload) {
        if (targetPub == null || targetPub.length != 32 || payload == null || payload.length == 0) {
            return;
        }
        PeerRecord target = registry.lookup(targetPub);
        if (target != null) {
            sendData(new InetSocketAddress(target.host(), target.rudpPort()), payload);
            return;
        }
        List<PeerRecord> forwarders = registry.lookupForwarders(1);
        if (forwarders.isEmpty()) {
            LOG.debug("RudpSessionManager: no route to target " + Hex.encode(targetPub));
            return;
        }
        PeerRecord forwarder = forwarders.get(0);
        sendRelay(new InetSocketAddress(forwarder.host(), forwarder.rudpPort()), targetPub, payload);
    }

    /**
     * Initiate a direct rUDP session with a remote servent.
     *
     * @return the local connection id
     */
    public long connect(InetSocketAddress remoteAddress) {
        long localCid = randomConnectionId();
        long remoteCid = randomConnectionId();
        RudpSession session = new RudpSession(localCid, remoteCid, remoteAddress, null, true);
        sessionsByRemoteId.put(remoteCid, session);
        sessionsByAddress.put(remoteAddress, session);
        byte[] payload;
        try {
            payload = RudpAuth.createHelloPayload(identity, remoteCid);
        } catch (Exception e) {
            LOG.error("Failed to sign rUDP hello", e);
            sessionsByRemoteId.remove(remoteCid);
            sessionsByAddress.remove(remoteAddress);
            return -1;
        }
        RudpPacket hello = new RudpPacket(RudpPacket.Type.HELLO, remoteCid, 0, 0, payload);
        send(session, hello);
        return localCid;
    }

    /**
     * Send application data reliably to a remote endpoint. Payloads larger
     * than {@link RudpPacket#MAX_FRAGMENT_PAYLOAD} are split into chunks,
     * each sent as a separate reliable {@code DATA_FRAG} packet followed by
     * a {@code DATA_END} packet. The receiver reassembles them.
     */
    public void sendData(InetSocketAddress remoteAddress, byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        RudpSession session = sessionsByAddress.get(remoteAddress);
        if (session == null) {
            connect(remoteAddress);
            session = sessionsByAddress.get(remoteAddress);
            if (session == null) {
                return;
            }
        }
        if (payload.length <= RudpPacket.MAX_FRAGMENT_PAYLOAD) {
            // Single packet — no fragmentation needed.
            RudpPacket packet = session.data(payload);
            sendReliable(session, packet);
        } else {
            sendFragmented(session, payload);
        }
    }

    /**
     * Split a payload into fragments and send each as a reliable packet.
     * Each fragment payload carries a 12-byte header:
     * [groupId(4)][fragIndex(4)][totalFrags(4)] followed by the chunk bytes.
     * Intermediate fragments use type {@code DATA_FRAG}; the last uses
     * {@code DATA_END}.
     */
    private void sendFragmented(RudpSession session, byte[] payload) {
        int totalFrags = (payload.length + RudpPacket.MAX_FRAGMENT_PAYLOAD - 1)
                / RudpPacket.MAX_FRAGMENT_PAYLOAD;
        int groupId = random.nextInt();
        int offset = 0;
        for (int i = 0; i < totalFrags; i++) {
            int chunkLen = Math.min(RudpPacket.MAX_FRAGMENT_PAYLOAD, payload.length - offset);
            byte[] fragPayload = new byte[FRAG_HEADER_SIZE + chunkLen];
            writeIntBE(fragPayload, 0, groupId);
            writeIntBE(fragPayload, 4, i);
            writeIntBE(fragPayload, 8, totalFrags);
            System.arraycopy(payload, offset, fragPayload, FRAG_HEADER_SIZE, chunkLen);
            offset += chunkLen;

            boolean isLast = (i == totalFrags - 1);
            RudpPacket.Type type = isLast ? RudpPacket.Type.DATA_END : RudpPacket.Type.DATA_FRAG;
            RudpPacket packet = new RudpPacket(type, session.remoteConnectionId(),
                    session.nextLocalSequence(), session.receivedThroughRemote(), fragPayload);
            sendReliable(session, packet);
        }
    }

    /**
     * Send data through a forwarder to a target identified by public key.
     */
    public void sendRelay(InetSocketAddress forwarderAddress, byte[] targetPub, byte[] payload) {
        if (targetPub == null || targetPub.length != 32 || payload == null || payload.length == 0) {
            return;
        }
        // Relay frame: [sourcePub 32][targetPub 32][appPayload...]
        byte[] relayPayload = new byte[32 + 32 + payload.length];
        System.arraycopy(identity.ed25519PubRaw(), 0, relayPayload, 0, 32);
        System.arraycopy(targetPub, 0, relayPayload, 32, 32);
        System.arraycopy(payload, 0, relayPayload, 64, payload.length);
        sendData(forwarderAddress, relayPayload);
    }

    /**
     * Process an inbound packet from the UDP channel.
     */
    public void onPacket(RudpPacketEnvelope envelope) {
        RudpPacket packet = envelope.packet();
        InetSocketAddress sender = envelope.sender();
        metrics.rudpPacketIn(packet.size());

        switch (packet.type()) {
            case HELLO:
                handleHello(packet, sender);
                break;
            case HELLO_ACK:
                handleHelloAck(packet, sender);
                break;
            case DATA:
                handleData(packet, sender);
                break;
            case DATA_ACK:
                handleDataAck(packet);
                break;
            case DATA_FRAG:
                handleDataFrag(packet, sender, false);
                break;
            case DATA_END:
                handleDataFrag(packet, sender, true);
                break;
            case HOLE_PUNCH:
                handleHolePunch(packet, sender);
                break;
            case HOLE_PUNCH_RESPONSE:
                handleHolePunchResponse(packet, sender);
                break;
            case RELAY:
                handleRelay(packet, sender);
                break;
            case RELAY_RESPONSE:
                handleRelayResponse(packet);
                break;
            default:
                LOG.debug("RudpSessionManager: unknown packet type " + packet.type());
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public int sessionCount() {
        return sessionsByRemoteId.size();
    }

    // ---- outbound helpers ----

    private void send(RudpSession session, RudpPacket packet) {
        write(session.remoteAddress(), packet);
    }

    private void sendReliable(RudpSession session, RudpPacket packet) {
        PendingPacket pending = new PendingPacket(packet, session.remoteAddress(), System.currentTimeMillis());
        session.addPending(packet.sequence(), pending);
        send(session, packet);
    }

    private void write(InetSocketAddress recipient, RudpPacket packet) {
        metrics.rudpPacketOut(packet.size());
        Channel ch = channel;
        if (ch == null || !ch.isOpen()) {
            return;
        }
        ch.writeAndFlush(new RudpPacketEnvelope(packet, null, recipient));
    }

    private long randomConnectionId() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        long id = 0;
        for (int i = 0; i < 8; i++) {
            id = (id << 8) | (bytes[i] & 0xffL);
        }
        return id;
    }

    private static final int MAX_SESSIONS = 256;

    // ---- packet handlers ----

    private void handleHello(RudpPacket packet, InetSocketAddress sender) {
        long remoteCid = packet.connectionId();
        if (!RudpAuth.verifyHello(remoteCid, packet.payload())) {
            LOG.debug("RudpSessionManager: dropped HELLO with bad auth from " + sender);
            return;
        }
        RudpSession session = sessionsByRemoteId.get(remoteCid);
        if (session == null) {
            if (sessionsByRemoteId.size() >= MAX_SESSIONS) {
                LOG.warn("RudpSessionManager: rejected HELLO from " + sender
                        + " — max sessions (" + MAX_SESSIONS + ") reached");
                return;
            }
            long localCid = randomConnectionId();
            byte[] remotePub = Arrays.copyOfRange(packet.payload(), 0, 32);
            session = new RudpSession(localCid, remoteCid, sender, remotePub, false);
            sessionsByRemoteId.put(remoteCid, session);
            sessionsByAddress.put(sender, session);
        }
        session.markActivity();
        send(session, session.helloAck());
    }

    private void handleHelloAck(RudpPacket packet, InetSocketAddress sender) {
        RudpSession session = sessionsByRemoteId.get(packet.connectionId());
        if (session == null) {
            return;
        }
        session.markActivity();
        LOG.debug("RudpSessionManager: session " + packet.connectionId() + " acknowledged with " + sender);
    }

    private void handleData(RudpPacket packet, InetSocketAddress sender) {
        RudpSession session = sessionsByRemoteId.get(packet.connectionId());
        if (session == null) {
            return;
        }
        if (session.receiveRemote(packet.sequence())) {
            notifyListener(session.remotePub(), packet.payload());
        }
        send(session, session.dataAck());
    }

    private void handleDataAck(RudpPacket packet) {
        RudpSession session = sessionsByRemoteId.get(packet.connectionId());
        if (session == null) {
            return;
        }
        session.ackLocal(packet.ackThrough());
        session.markActivity();
    }

    /**
     * Handle a fragment packet (DATA_FRAG or DATA_END). Ack the packet,
     * feed it to the reassembler, and deliver the completed payload to the
     * listener when all fragments have arrived.
     */
    private void handleDataFrag(RudpPacket packet, InetSocketAddress sender, boolean isLast) {
        RudpSession session = sessionsByRemoteId.get(packet.connectionId());
        if (session == null) {
            return;
        }

        // Validate the fragment header BEFORE acking so that malformed
        // fragments are not silently dropped after the sender believes
        // they were delivered.
        byte[] raw = packet.payload();
        if (raw == null || raw.length < FRAG_HEADER_SIZE) {
            LOG.debug("RudpSessionManager: dropping fragment with invalid header");
            return; // do not ack — sender will retransmit
        }

        // Ack every fragment so the sender can clear its pending queue.
        if (session.receiveRemote(packet.sequence())) {
            send(session, session.dataAck());
        } else {
            // Duplicate — re-ack so the sender stops retransmitting.
            send(session, session.dataAck());
            return;
        }

        int groupId = readIntBE(raw, 0);
        int fragIndex = readIntBE(raw, 4);
        int totalFrags = readIntBE(raw, 8);
        byte[] chunk = new byte[raw.length - FRAG_HEADER_SIZE];
        System.arraycopy(raw, FRAG_HEADER_SIZE, chunk, 0, chunk.length);

        // Key by sender address + groupId to prevent cross-session collision.
        String groupKey = sender.toString() + ":" + groupId;
        byte[] assembled = reassembler.addFragment(groupKey, fragIndex, isLast, chunk);
        if (assembled != null) {
            notifyListener(session.remotePub(), assembled);
        }
    }

    private void handleHolePunch(RudpPacket packet, InetSocketAddress sender) {
        byte[] payload = packet.payload();
        if (payload == null || payload.length < 32) {
            return;
        }
        byte[] targetPub = Arrays.copyOfRange(payload, 0, 32);
        PeerRecord target = registry.lookup(targetPub);
        if (target == null) {
            return;
        }
        InetSocketAddress targetAddress = new InetSocketAddress(target.host(), target.rudpPort());

        // Tell target about initiator.
        byte[] initiatorInfo = (sender.getHostString() + ":" + sender.getPort())
                .getBytes(StandardCharsets.UTF_8);
        RudpPacket toTarget = new RudpPacket(RudpPacket.Type.HOLE_PUNCH_RESPONSE,
                0, 0, 0, initiatorInfo);
        write(targetAddress, toTarget);

        // Tell initiator about target.
        byte[] targetInfo = (target.host() + ":" + target.rudpPort())
                .getBytes(StandardCharsets.UTF_8);
        RudpPacket toInitiator = new RudpPacket(RudpPacket.Type.HOLE_PUNCH_RESPONSE,
                0, 0, 0, targetInfo);
        write(sender, toInitiator);
    }

    private void handleHolePunchResponse(RudpPacket packet, InetSocketAddress sender) {
        LOG.debug("RudpSessionManager: received hole-punch response from " + sender);
    }

    private void handleRelay(RudpPacket packet, InetSocketAddress sender) {
        byte[] payload = packet.payload();
        if (payload == null || payload.length < 64) {
            return;
        }
        byte[] sourcePub = Arrays.copyOfRange(payload, 0, 32);
        byte[] targetPub = Arrays.copyOfRange(payload, 32, 64);
        byte[] appPayload = Arrays.copyOfRange(payload, 64, payload.length);

        PeerRecord target = registry.lookup(targetPub);
        if (target == null) {
            return;
        }
        InetSocketAddress targetAddress = new InetSocketAddress(target.host(), target.rudpPort());

        // Relay response frame: [sourcePub 32][appPayload...]
        byte[] responsePayload = new byte[32 + appPayload.length];
        System.arraycopy(sourcePub, 0, responsePayload, 0, 32);
        System.arraycopy(appPayload, 0, responsePayload, 32, appPayload.length);

        RudpPacket forward = new RudpPacket(RudpPacket.Type.RELAY_RESPONSE, 0, 0, 0, responsePayload);
        write(targetAddress, forward);
    }

    private void handleRelayResponse(RudpPacket packet) {
        byte[] payload = packet.payload();
        if (payload == null || payload.length < 32) {
            return;
        }
        byte[] sourcePub = Arrays.copyOfRange(payload, 0, 32);
        byte[] appPayload = Arrays.copyOfRange(payload, 32, payload.length);
        notifyListener(sourcePub, appPayload);
    }

    private void notifyListener(byte[] sourcePub, byte[] payload) {
        if (messageListener != null) {
            try {
                messageListener.onMessage(sourcePub == null ? new byte[0] : sourcePub, payload);
            } catch (Throwable t) {
                LOG.warn("RudpSessionManager: message listener failed", t);
            }
        }
    }

    // ---- maintenance ----

    private void retransmitAndEvict() {
        long now = System.currentTimeMillis();
        for (RudpSession session : sessionsByRemoteId.values()) {
            // Retransmit unacked packets that are due, and purge packets
            // that have exhausted retries or exceeded the timeout.
            var pendingMap = session.pending();
            var iter = pendingMap.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                PendingPacket pp = entry.getValue();
                boolean exhausted = pp.retries >= MAX_RETRIES
                        || (now - pp.firstSentMs) >= RETRANSMIT_TIMEOUT_MS;
                if (exhausted) {
                    iter.remove();
                    continue;
                }
                if (now - pp.lastSentMs > RETRANSMIT_INTERVAL_MS) {
                    pp.retries++;
                    pp.lastSentMs = now;
                    write(pp.recipient, pp.packet);
                }
            }
            if (now - session.lastActivityMs() > SESSION_IDLE_MS) {
                sessionsByRemoteId.remove(session.remoteConnectionId());
                sessionsByAddress.remove(session.remoteAddress());
            }
        }
        reassembler.evictStale();
    }

    // ---- utilities ----

    private static void writeIntBE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value >>> 24);
        buf[offset + 1] = (byte) (value >>> 16);
        buf[offset + 2] = (byte) (value >>> 8);
        buf[offset + 3] = (byte) value;
    }

    private static int readIntBE(byte[] buf, int offset) {
        return ((buf[offset] & 0xff) << 24)
                | ((buf[offset + 1] & 0xff) << 16)
                | ((buf[offset + 2] & 0xff) << 8)
                | (buf[offset + 3] & 0xff);
    }
}