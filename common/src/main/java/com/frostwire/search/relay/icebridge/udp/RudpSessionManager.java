/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.RateLimiter;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTopology;
import com.frostwire.search.relay.icebridge.control.InboundMessageQueue;
import com.frostwire.search.relay.icebridge.peer.PeerRecord;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.util.Logger;
import io.netty.channel.Channel;
import io.netty.util.NetUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bounded version-2 signed rUDP transport; see {@link RudpAuth} for the wire
 * security contract and limitations. All mutation is serialized by this manager.
 * Listener callbacks MUST be bounded/nonblocking; the built-in listener only
 * reserves a queue slot. Netty writes are nonblocking, never waited on here.
 *
 * <p>A transport ACK means bounded next-hop ownership, not application processing
 * or HTTP recipient delivery. New work is acknowledged only after admission;
 * accepted duplicates are re-ACKed without delivery. A flood is accepted when at
 * least one next hop owns it (not a promise that every candidate accepted it).
 * Retry/assembly timeout terminates the entire association, never just an ACKed
 * prefix. End-to-end requests still need application deadlines and retry policy.
 */
public final class RudpSessionManager {
    private static final Logger LOG = Logger.getLogger(RudpSessionManager.class);
    private static final long RETRANSMIT_INTERVAL_MS = 500;
    private static final long RETRANSMIT_TIMEOUT_MS = 15_000;
    private static final long SESSION_IDLE_MS = 120_000;
    private static final int MAX_RETRIES = 5;
    private static final int SEND_WINDOW = 32;
    private static final int MAX_SESSIONS_PER_SUBNET_24 = 256;
    private static final long MAX_PENDING_BYTES = 16L * 1024 * 1024;
    private static final int MAX_PENDING_PACKETS = 16_384;
    private static final int MAX_GLOBAL_SESSIONS = 4096;
    private static final int FRAG_HEADER_SIZE = 12;

    private final IdentityKeys identity;
    private final PeerRegistry registry;
    private final IceBridgeMetrics metrics;
    private final RudpMessageListener messageListener;
    private final FragmentReassembler reassembler = new FragmentReassembler();
    private final RateLimiter helloPerIp = new RateLimiter(10, 2, 4096, 60_000);
    private final RateLimiter helloGlobal = new RateLimiter(200, 50, 2, 60_000);
    private final RateLimiter packetGlobal = new RateLimiter(2000, 2000, 1, 60_000);
    // A CID has exactly one owner and exactly one map entry, including simultaneous open.
    private final Map<Long, RudpSession> sessionsByRemoteId = new HashMap<>();
    private final Map<InetSocketAddress, RudpSession> sessionsByAddress = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final ScheduledExecutorService scheduler;
    private Channel channel;
    private boolean closed;
    private int maxSessions = IceBridgeConfig.DEFAULT_MAX_SESSIONS;

    public RudpSessionManager(IdentityKeys identity, PeerRegistry registry,
                              IceBridgeMetrics metrics, RudpMessageListener listener) {
        this.identity = identity;
        this.registry = registry;
        this.metrics = metrics;
        this.messageListener = listener;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "icebridge-rudp-manager");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::retransmitAndEvict,
                RETRANSMIT_INTERVAL_MS, RETRANSMIT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void setChannel(Channel channel) {
        this.channel = channel;
    }

    /** True reserves bounded next-hop ownership; false means no work was accepted. */
    public synchronized boolean deliver(byte[] targetPub, byte[] payload) {
        if (closed || targetPub == null || targetPub.length != 32 || !validPayload(payload)) {
            return false;
        }
        if (Arrays.equals(targetPub, identity.ed25519PubRaw())) {
            return notifyListener(identity.ed25519PubRaw(), payload);
        }
        PeerRecord target = registry.lookup(targetPub);
        if (target != null) {
            InetSocketAddress address = literalAddress(target.host(), target.rudpPort());
            if (isLocalRudpEndpoint(address)) {
                return deliverToLocalPollClient(targetPub, new byte[0], payload);
            }
            RudpSession live = findSessionByPub(targetPub);
            if (live != null) {
                return queueData(live, payload);
            }
            if (address != null) {
                RudpSession session = connectSession(address, targetPub);
                return session != null && queueData(session, payload);
            }
        }
        if (payload.length > RelayFrame.MAX_APP_PAYLOAD) {
            return false;
        }
        return forward(targetPub, payload, IceBridgeTopology.get().meshHopTtl(), null);
    }

    /**
     * Explicit identity discovery when no expected peer is known. Callers with a
     * known identity use deliver(), which pins that identity in the transcript.
     * Unresolved addresses are rejected: no DNS executes on the receive loop.
     */
    public synchronized long connect(InetSocketAddress remoteAddress) {
        RudpSession session = connectSession(remoteAddress, expectedPeer(remoteAddress));
        return session == null ? -1 : session.localConnectionId();
    }

    private RudpSession connectSession(InetSocketAddress address, byte[] expectedPub) {
        if (closed || !validAddress(address)) {
            return null;
        }
        RudpSession existing = sessionsByAddress.get(address);
        if (existing != null && !existing.hasSequenceCapacity(1) && existing.pending().isEmpty()) {
            dropSession(existing);
            existing = null;
        }
        if (existing != null) {
            byte[] peer = existing.remotePub();
            if (expectedPub != null && (peer == null || !Arrays.equals(peer, expectedPub))) {
                return null;
            }
            return existing;
        }
        if (!canCreate(address) || !canQueue(null, 1, 2L * (RudpAuth.HELLO_PAYLOAD_LENGTH + 128))
                || !helloGlobal.tryAcquire("outbound")) {
            return null;
        }
        long cid;
        do {
            cid = random.nextLong();
        } while (cid == 0 || cid == -1 || sessionsByRemoteId.containsKey(cid));
        RudpSession session = new RudpSession(cid, cid, address, expectedPub, true);
        try {
            session.helloPayload = RudpAuth.createHelloPayload(identity, cid, expectedPub);
            sessionsByRemoteId.put(cid, session);
            sessionsByAddress.put(address, session);
            enqueue(session, new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, session.helloPayload));
            flush(session);
            return session;
        } catch (Exception e) {
            dropSession(session);
            LOG.warn("Unable to create rUDP handshake", e);
            return null;
        }
    }

    public synchronized boolean sendData(InetSocketAddress address, byte[] payload) {
        if (!validPayload(payload)) {
            return false;
        }
        RudpSession session = connectSession(address, expectedPeer(address));
        return session != null && queueData(session, payload);
    }

    private boolean queueData(RudpSession session, byte[] payload) {
        int count = (payload.length + RudpPacket.MAX_FRAGMENT_PAYLOAD - 1) / RudpPacket.MAX_FRAGMENT_PAYLOAD;
        long bytes = 2L * (payload.length + (long) count * (RudpPacket.HEADER_SIZE + FRAG_HEADER_SIZE + RudpAuth.SIGNATURE_LENGTH));
        if (!canQueue(session, count, bytes)) {
            return false;
        }
        if (count == 1) {
            enqueue(session, session.data(payload));
        } else {
            int group = random.nextInt();
            for (int i = 0; i < count; i++) {
                int offset = i * RudpPacket.MAX_FRAGMENT_PAYLOAD;
                int length = Math.min(RudpPacket.MAX_FRAGMENT_PAYLOAD, payload.length - offset);
                byte[] part = ByteBuffer.allocate(FRAG_HEADER_SIZE + length)
                        .putInt(group).putInt(i).putInt(count).put(payload, offset, length).array();
                enqueue(session, new RudpPacket(i == count - 1 ? RudpPacket.Type.DATA_END : RudpPacket.Type.DATA_FRAG,
                        session.remoteConnectionId(), session.nextLocalSequence(), session.receivedThroughRemote(), part));
            }
        }
        flush(session);
        return true;
    }

    public boolean sendRelay(InetSocketAddress address, byte[] targetPub, byte[] payload) {
        return sendRelay(address, targetPub, payload, IceBridgeTopology.get().meshHopTtl());
    }

    public synchronized boolean sendRelay(InetSocketAddress address, byte[] targetPub, byte[] payload, int ttl) {
        if (targetPub == null || targetPub.length != 32 || !validPayload(payload)
                || payload.length > RelayFrame.MAX_APP_PAYLOAD || ttl < 0
                || ttl > IceBridgeTopology.MAX_MESH_HOP_TTL) {
            return false;
        }
        byte[] frame;
        try {
            frame = RelayFrame.encode(identity.ed25519PubRaw(), targetPub, ttl, payload);
        } catch (IllegalArgumentException e) {
            return false;
        }
        RudpSession session = connectSession(address, expectedPeer(address));
        return session != null && queueReliable(session, RudpPacket.Type.RELAY, frame);
    }

    private boolean queueReliable(RudpSession session, RudpPacket.Type type, byte[] payload) {
        if (payload.length + RudpAuth.SIGNATURE_LENGTH > RudpPacket.MAX_WIRE_PAYLOAD
                || !canQueue(session, 1, 2L * (payload.length + RudpPacket.HEADER_SIZE + RudpAuth.SIGNATURE_LENGTH))) {
            return false;
        }
        enqueue(session, new RudpPacket(type, session.remoteConnectionId(), session.nextLocalSequence(),
                session.receivedThroughRemote(), payload));
        flush(session);
        return true;
    }

    private boolean canQueue(RudpSession session, int count, long bytes) {
        if (closed || (session != null && (!session.hasSequenceCapacity(count)
                || session.pending().size() + count > RudpSession.MAX_PENDING_PACKETS
                || session.retainedBytes() > RudpSession.MAX_RETAINED_BYTES - bytes))) {
            return false;
        }
        long retained = 0;
        int packets = 0;
        for (RudpSession current : sessionsByRemoteId.values()) {
            retained += current.retainedBytes();
            packets += current.pending().size();
        }
        return retained <= MAX_PENDING_BYTES - bytes && packets <= MAX_PENDING_PACKETS - count;
    }

    private void enqueue(RudpSession session, RudpPacket packet) {
        if (!session.addPending(packet.sequence(), new PendingPacket(packet, session.remoteAddress(), nowMs()))) {
            throw new IllegalStateException("rUDP reservation invariant");
        }
    }

    private void flush(RudpSession session) {
        if (channel == null || !channel.isOpen() || !channel.isWritable()) {
            return;
        }
        int window = 0;
        for (PendingPacket pending : session.pending().values()) {
            if (pending.packet.sequence() != 0 && !session.isAuthenticated()) {
                continue;
            }
            if (window++ >= SEND_WINDOW) {
                break;
            }
            if (!pending.sent) {
                try {
                    pending.wirePacket = handshakeUnsigned(pending.packet.type()) ? pending.packet
                            : RudpAuth.protect(identity, session.remotePub(), session.transcript, pending.packet);
                    pending.sent = true;
                    pending.lastSentMs = nowMs();
                    session.markSent(pending.packet.sequence());
                    write(session.remoteAddress(), pending.wirePacket);
                } catch (Exception e) {
                    LOG.warn("Unable to sign rUDP packet", e);
                    dropSession(session);
                    return;
                }
            }
        }
    }

    public synchronized void onPacket(RudpPacketEnvelope envelope) {
        if (closed || envelope == null || envelope.packet() == null || !validAddress(envelope.sender())) {
            return;
        }
        RudpPacket packet = envelope.packet();
        InetSocketAddress sender = envelope.sender();
        if (packet.size() > RudpPacket.HEADER_SIZE + RudpPacket.MAX_WIRE_PAYLOAD) {
            return;
        }
        metrics.rudpPacketIn(packet.size());
        // Introductions are disabled in v2. There is no authenticated request/
        // target-consent protocol yet; never resolve or dial an unsolicited hint.
        if (packet.type() == RudpPacket.Type.HOLE_PUNCH || packet.type() == RudpPacket.Type.HOLE_PUNCH_RESPONSE) {
            return;
        }
        if (handshakeUnsigned(packet.type())) {
            if (packet.sequence() != 0 || packet.ackThrough() != 0 || !allowHello(sender)) {
                return;
            }
            if (packet.type() == RudpPacket.Type.HELLO) {
                handleHello(packet, sender);
            } else {
                handleHelloAck(packet, sender);
            }
            return;
        }
        RudpSession session = sessionsByRemoteId.get(packet.connectionId());
        if (session == null || session.transcript == null || !session.validAck(packet.ackThrough())
                || !packetGlobal.tryAcquire("packets")) {
            return;
        }
        packet = RudpAuth.unprotect(session.remotePub(), identity.ed25519PubRaw(), session.transcript, packet);
        if (packet == null) {
            return;
        }
        if (packet.type() == RudpPacket.Type.HELLO_FINISH || packet.type() == RudpPacket.Type.HELLO_READY) {
            finishHandshake(session, packet, sender);
            return;
        }
        if (!session.isAuthenticated()) {
            return;
        }
        if (packet.type() == RudpPacket.Type.PATH_RESPONSE) {
            if (packet.sequence() == 0 && packet.ackThrough() == 0 && sender.equals(session.candidateAddress)
                    && Arrays.equals(packet.payload(), session.pathChallenge)
                    && System.nanoTime() - session.pathChallengeNanos < TimeUnit.SECONDS.toNanos(5)) {
                rebindSessionAddress(session, sender);
                session.candidateAddress = null;
                session.pathChallenge = null;
            }
            return;
        }
        if (!sender.equals(session.remoteAddress())) {
            challengePath(session, sender);
            return;
        }
        if (packet.type() == RudpPacket.Type.PATH_CHALLENGE) {
            if (packet.sequence() == 0 && packet.ackThrough() == 0 && packet.payload().length == 32) {
                sendProtected(session, sender, new RudpPacket(RudpPacket.Type.PATH_RESPONSE,
                        packet.connectionId(), 0, 0, packet.payload()));
            }
            return;
        }
        if (packet.type() == RudpPacket.Type.DATA_ACK) {
            int previous = session.ackedThroughLocal();
            if (packet.sequence() == 0 && packet.payload().length == 0 && session.ackLocal(packet.ackThrough())) {
                if (Integer.compareUnsigned(packet.ackThrough(), previous) > 0) {
                    session.markActivity();
                }
                flush(session);
            }
            return;
        }
        if (packet.sequence() == 0) {
            return;
        }
        if (Integer.compareUnsigned(packet.sequence(), session.receivedThroughRemote()) <= 0) {
            sendProtected(session, sender, session.dataAck());
            return;
        }
        if (packet.sequence() != session.receivedThroughRemote() + 1) {
            return;
        }
        if (session.fragmentKey != null && packet.type() != RudpPacket.Type.DATA_FRAG
                && packet.type() != RudpPacket.Type.DATA_END) {
            return;
        }
        boolean accepted = false;
        switch (packet.type()) {
            case DATA:
                accepted = validPayload(packet.payload()) && notifyListener(session.remotePub(), packet.payload());
                break;
            case DATA_FRAG:
            case DATA_END:
                accepted = handleFragment(session, packet);
                break;
            case RELAY:
                accepted = handleRelay(session, packet.payload());
                break;
            case RELAY_RESPONSE:
                byte[] response = packet.payload();
                if (response.length > 32 && response.length - 32 <= RelayFrame.MAX_APP_PAYLOAD) {
                    // Preserve authenticated hop attribution. Origin identity is
                    // carried and verified independently by the application.
                    accepted = notifyListener(session.remotePub(), Arrays.copyOfRange(response, 32, response.length));
                }
                break;
            default:
                return;
        }
        if (accepted) {
            session.receiveRemote(packet.sequence());
            session.ackLocal(packet.ackThrough());
            sendProtected(session, sender, session.dataAck());
            flush(session);
        }
    }

    private void handleHello(RudpPacket packet, InetSocketAddress sender) {
        byte[] hello = packet.payload();
        RudpSession known = sessionsByRemoteId.get(packet.connectionId());
        if (known != null) {
            if (!known.weAreInitiator() && sender.equals(known.remoteAddress())
                    && Arrays.equals(known.helloPayload, hello)) {
                write(sender, new RudpPacket(RudpPacket.Type.HELLO_ACK, packet.connectionId(), 0, 0, known.ackPayload));
            }
            return;
        }
        if (!RudpAuth.verifyHello(packet.connectionId(), hello) || !RudpAuth.intendedFor(hello, identity.ed25519PubRaw())) {
            return;
        }
        byte[] peer = Arrays.copyOf(hello, 32);
        if (Arrays.equals(peer, identity.ed25519PubRaw())) {
            return;
        }
        RudpSession outbound = sessionsByAddress.get(sender);
        if (outbound != null) {
            // Simultaneous open deterministically keeps the lower pub as initiator.
            // The responder transfers queued work, never discards it or adds aliases.
            if (outbound.isAuthenticated() || !outbound.weAreInitiator()
                    || (outbound.remotePub() != null && !Arrays.equals(outbound.remotePub(), peer))
                    || comparePub(identity.ed25519PubRaw(), peer) < 0) {
                return;
            }
        }
        if (outbound == null && !canCreate(sender)) {
            metrics.helloRejected();
            return;
        }
        RudpSession session = new RudpSession(packet.connectionId(), packet.connectionId(), sender, peer, false);
        try {
            session.helloPayload = hello;
            session.ackPayload = RudpAuth.createAckPayload(identity, packet.connectionId(), hello);
            session.transcript = RudpAuth.transcript(hello, session.ackPayload);
            if (outbound != null) {
                for (PendingPacket pending : outbound.pending().values()) {
                    if (pending.packet.sequence() != 0) {
                        RudpPacket old = pending.packet;
                        RudpPacket transferred = new RudpPacket(old.type(), packet.connectionId(),
                                session.nextLocalSequence(), 0, old.payload());
                        if (!session.addPending(transferred.sequence(), new PendingPacket(transferred,
                                sender, pending.firstSentMs))) {
                            throw new IllegalStateException("rUDP transfer reservation invariant");
                        }
                    }
                }
                dropSession(outbound);
            }
            sessionsByRemoteId.put(packet.connectionId(), session);
            sessionsByAddress.put(sender, session);
            write(sender, new RudpPacket(RudpPacket.Type.HELLO_ACK, packet.connectionId(), 0, 0, session.ackPayload));
        } catch (Exception e) {
            dropSession(session);
            LOG.warn("Unable to sign rUDP response", e);
        }
    }

    private void handleHelloAck(RudpPacket packet, InetSocketAddress sender) {
        RudpSession session = sessionsByRemoteId.get(packet.connectionId());
        byte[] ack = packet.payload();
        if (session == null || !session.weAreInitiator() || !sender.equals(session.remoteAddress())
                || session.isAuthenticated() || !RudpAuth.verifyAck(packet.connectionId(), session.helloPayload, ack)) {
            return;
        }
        byte[] peer = Arrays.copyOf(ack, 32);
        if (session.remotePub() != null && !Arrays.equals(session.remotePub(), peer)) {
            return;
        }
        if (session.ackPayload != null && !Arrays.equals(session.ackPayload, ack)) {
            return;
        }
        session.setRemotePub(peer);
        session.ackPayload = ack;
        session.transcript = RudpAuth.transcript(session.helloPayload, ack);
        if (session.pending().get(0) != null && session.pending().get(0).packet.type() == RudpPacket.Type.HELLO_FINISH) {
            return;
        }
        session.pending().remove(0);
        enqueue(session, new RudpPacket(RudpPacket.Type.HELLO_FINISH, packet.connectionId(), 0, 0, new byte[0]));
        flush(session);
    }

    private void finishHandshake(RudpSession session, RudpPacket packet, InetSocketAddress sender) {
        if (!sender.equals(session.remoteAddress()) || packet.sequence() != 0 || packet.ackThrough() != 0
                || packet.payload().length != 0) {
            return;
        }
        if (packet.type() == RudpPacket.Type.HELLO_FINISH && !session.weAreInitiator()) {
            if (!session.isAuthenticated()) {
                session.authenticate();
                learnEndpoint(session);
            }
            sendProtected(session, sender, new RudpPacket(RudpPacket.Type.HELLO_READY, packet.connectionId(), 0, 0, new byte[0]));
            flush(session);
        } else if (packet.type() == RudpPacket.Type.HELLO_READY && session.weAreInitiator()) {
            if (!session.isAuthenticated()) {
                session.authenticate();
                session.pending().remove(0);
                learnEndpoint(session);
            }
            flush(session);
        }
    }

    private boolean handleFragment(RudpSession session, RudpPacket packet) {
        byte[] raw = packet.payload();
        if (raw.length <= FRAG_HEADER_SIZE) {
            return false;
        }
        ByteBuffer header = ByteBuffer.wrap(raw);
        int groupId = header.getInt();
        int index = header.getInt();
        int total = header.getInt();
        String key = groupPrefix(session) + groupId;
        if ((session.fragmentKey != null && !session.fragmentKey.equals(key))
                || index != session.nextFragmentIndex) {
            return false;
        }
        FragmentReassembler.Result result = reassembler.accept(key, index, total,
                packet.type() == RudpPacket.Type.DATA_END, Arrays.copyOfRange(raw, FRAG_HEADER_SIZE, raw.length));
        if (result.state == FragmentReassembler.State.REJECTED) {
            return false;
        }
        session.fragmentKey = key;
        if (result.state == FragmentReassembler.State.RETAINED) {
            session.nextFragmentIndex++;
            return true;
        }
        // Final sequence remains unadvanced while blocked. Its ordinary retry
        // finds the retained COMPLETE result, not a new prefix-less assembly.
        if (!notifyListener(session.remotePub(), result.payload)) {
            return false;
        }
        reassembler.release(key);
        session.fragmentKey = null;
        session.nextFragmentIndex = 0;
        return true;
    }

    private boolean handleRelay(RudpSession sender, byte[] bytes) {
        RelayFrame frame;
        try {
            frame = RelayFrame.decode(bytes);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!Arrays.equals(frame.sourcePub(), sender.remotePub())
                || frame.hopTtl() > IceBridgeTopology.MAX_MESH_HOP_TTL) {
            return false;
        }
        if (Arrays.equals(frame.targetPub(), identity.ed25519PubRaw())) {
            return notifyListener(sender.remotePub(), frame.appPayload());
        }
        PeerRecord target = registry.lookup(frame.targetPub());
        if (target != null) {
            InetSocketAddress address = literalAddress(target.host(), target.rudpPort());
            if (isLocalRudpEndpoint(address)) {
                return deliverToLocalPollClient(frame.targetPub(), sender.remotePub(), frame.appPayload());
            }
            RudpSession next = findSessionByPub(frame.targetPub());
            if (next == null) {
                next = connectSession(address, frame.targetPub());
            }
            return next != null && queueReliable(next, RudpPacket.Type.RELAY_RESPONSE,
                    ByteBuffer.allocate(32 + frame.appPayload().length).put(frame.sourcePub()).put(frame.appPayload()).array());
        }
        // TTL counts additional forwarding edges. A terminal recipient still
        // accepts its own target at zero; non-target nodes never forward zero.
        return frame.hopTtl() > 0 && forward(frame.targetPub(), frame.appPayload(), frame.hopTtl() - 1, sender.remotePub());
    }

    private boolean forward(byte[] target, byte[] payload, int ttl, byte[] exclude) {
        int accepted = 0;
        int fanout = IceBridgeTopology.get().meshBroadcastFanout();
        for (PeerRecord peer : registry.lookupForwarders(fanout)) {
            if (Arrays.equals(peer.ed25519Pub(), identity.ed25519PubRaw()) || Arrays.equals(peer.ed25519Pub(), exclude)) {
                continue;
            }
            if (sendRelay(literalAddress(peer.host(), peer.rudpPort()), target, payload, ttl)) {
                accepted++;
            }
        }
        return accepted > 0;
    }

    private boolean notifyListener(byte[] source, byte[] payload) {
        if (messageListener == null) {
            return false;
        }
        try {
            if (messageListener instanceof InboundMessageQueue) {
                return ((InboundMessageQueue) messageListener).offerFromRudp(identity.ed25519PubRaw(), source, payload);
            }
            messageListener.onMessage(source.clone(), payload.clone());
            return true;
        } catch (RuntimeException e) {
            LOG.warn("rUDP listener rejected delivery", e);
            return false;
        }
    }

    private boolean deliverToLocalPollClient(byte[] target, byte[] source, byte[] payload) {
        return messageListener instanceof InboundMessageQueue
                && ((InboundMessageQueue) messageListener).offerForTarget(target, source, payload);
    }

    private boolean allowHello(InetSocketAddress sender) {
        // Global budget before allocating an IP bucket, including known-peer
        // retries. Limiter exceptions fail closed rather than bypassing crypto admission.
        try {
            boolean allowed = helloGlobal.tryAcquire("inbound") && helloPerIp.tryAcquire(sender.getAddress().getHostAddress());
            if (!allowed) {
                metrics.helloRejected();
            }
            return allowed;
        } catch (RuntimeException e) {
            metrics.helloRejected();
            return false;
        }
    }

    private boolean canCreate(InetSocketAddress address) {
        if (sessionsByRemoteId.size() >= Math.min(maxSessions, MAX_GLOBAL_SESSIONS)) {
            return false;
        }
        return withinSubnetLimit(address, null);
    }

    private boolean withinSubnetLimit(InetSocketAddress address, RudpSession migrating) {
        byte[] ip = address.getAddress().getAddress();
        int count = 0;
        for (RudpSession session : sessionsByRemoteId.values()) {
            if (session == migrating) {
                continue;
            }
            byte[] other = session.remoteAddress().getAddress().getAddress();
            // IPv4 /24 or IPv6 /64, same cap on every creation path.
            int prefix = ip.length == 4 ? 3 : 8;
            if (ip.length == other.length && Arrays.equals(Arrays.copyOf(ip, prefix), Arrays.copyOf(other, prefix))) {
                count++;
            }
        }
        return count < MAX_SESSIONS_PER_SUBNET_24;
    }

    private void challengePath(RudpSession session, InetSocketAddress candidate) {
        long now = System.nanoTime();
        if (session.pathChallenge != null && now - session.pathChallengeNanos < TimeUnit.SECONDS.toNanos(5)) {
            return;
        }
        if (sessionsByAddress.containsKey(candidate) || !withinSubnetLimit(candidate, session)) {
            return;
        }
        session.pathChallenge = new byte[32];
        random.nextBytes(session.pathChallenge);
        session.candidateAddress = candidate;
        session.pathChallengeNanos = now;
        sendProtected(session, candidate, new RudpPacket(RudpPacket.Type.PATH_CHALLENGE,
                session.remoteConnectionId(), 0, 0, session.pathChallenge));
    }

    private void rebindSessionAddress(RudpSession session, InetSocketAddress address) {
        RudpSession owner = sessionsByAddress.get(address);
        if ((owner != null && owner != session) || !withinSubnetLimit(address, session)) {
            return;
        }
        sessionsByAddress.remove(session.remoteAddress(), session);
        session.setRemoteAddress(address);
        sessionsByAddress.put(address, session);
        learnEndpoint(session);
    }

    private void learnEndpoint(RudpSession session) {
        InetSocketAddress address = session.remoteAddress();
        if (!address.getAddress().isLoopbackAddress()) {
            registry.learnObservedEndpoint(session.remotePub(), address.getAddress().getHostAddress(), address.getPort());
        }
    }

    private void sendProtected(RudpSession session, InetSocketAddress recipient, RudpPacket packet) {
        try {
            write(recipient, RudpAuth.protect(identity, session.remotePub(), session.transcript, packet));
        } catch (Exception e) {
            LOG.warn("Unable to sign rUDP control packet", e);
        }
    }

    private void write(InetSocketAddress recipient, RudpPacket packet) {
        if (channel != null && channel.isOpen() && channel.isWritable()) {
            metrics.rudpPacketOut(packet.size());
            channel.writeAndFlush(new RudpPacketEnvelope(packet, null, recipient));
        }
    }

    private synchronized void retransmitAndEvict() {
        if (closed) {
            return;
        }
        long now = nowMs();
        for (RudpSession session : new ArrayList<>(sessionsByRemoteId.values())) {
            if (now - session.lastActivityMs() > (session.isAuthenticated() ? SESSION_IDLE_MS : RETRANSMIT_TIMEOUT_MS)
                    || reassembler.hasExpired(groupPrefix(session))) {
                dropSession(session);
                continue;
            }
            boolean expired = false;
            for (PendingPacket pending : session.pending().values()) {
                if (now - pending.firstSentMs >= RETRANSMIT_TIMEOUT_MS || pending.retries >= MAX_RETRIES) {
                    expired = true;
                    break;
                }
                long backoff = RETRANSMIT_INTERVAL_MS << Math.min(pending.retries, 3);
                if (pending.sent && now - pending.lastSentMs >= backoff
                        && channel != null && channel.isOpen() && channel.isWritable()) {
                    pending.retries++;
                    pending.lastSentMs = now;
                    // Current path, never the stale tuple stored at enqueue time.
                    write(session.remoteAddress(), pending.wirePacket);
                }
            }
            if (expired) {
                dropSession(session);
            } else {
                flush(session);
            }
        }
        helloPerIp.evictIdle(60_000);
        helloGlobal.evictIdle(60_000);
        packetGlobal.evictIdle(60_000);
    }

    private void dropSession(RudpSession session) {
        sessionsByRemoteId.values().removeIf(value -> value == session);
        sessionsByAddress.values().removeIf(value -> value == session);
        reassembler.removeSession(groupPrefix(session));
        session.clear();
    }

    public synchronized void shutdown() {
        closed = true;
        scheduler.shutdownNow();
        for (RudpSession session : new ArrayList<>(sessionsByRemoteId.values())) {
            dropSession(session);
        }
    }

    public synchronized int sessionCount() {
        return sessionsByRemoteId.size();
    }

    public synchronized void setMaxSessions(int maxSessions) {
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("maxSessions must be > 0");
        }
        this.maxSessions = maxSessions;
    }

    public synchronized int maxSessions() {
        return maxSessions;
    }

    private RudpSession findSessionByPub(byte[] pub) {
        for (RudpSession session : sessionsByRemoteId.values()) {
            if (Arrays.equals(pub, session.remotePub())) {
                return session;
            }
        }
        return null;
    }

    private byte[] expectedPeer(InetSocketAddress address) {
        if (address != null) {
            for (PeerRecord peer : registry.lookupPeers(Integer.MAX_VALUE, false)) {
                if (address.equals(literalAddress(peer.host(), peer.rudpPort()))) {
                    return peer.ed25519Pub();
                }
            }
        }
        return null;
    }

    private boolean isLocalRudpEndpoint(InetSocketAddress address) {
        if (address == null || channel == null || !(channel.localAddress() instanceof InetSocketAddress)) {
            return false;
        }
        InetSocketAddress local = (InetSocketAddress) channel.localAddress();
        return local.getPort() == address.getPort() && (address.getAddress().isLoopbackAddress()
                || address.getAddress().isAnyLocalAddress() || address.getAddress().equals(local.getAddress()));
    }

    private static InetSocketAddress literalAddress(String host, int port) {
        if (host == null || port <= 0 || port > 65535) {
            return null;
        }
        byte[] bytes = NetUtil.createByteArrayFromIpAddressString(host);
        if (bytes == null) {
            return null;
        }
        try {
            return new InetSocketAddress(InetAddress.getByAddress(bytes), port);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean validAddress(InetSocketAddress address) {
        return address != null && address.getAddress() != null && address.getPort() > 0
                && !address.getAddress().isAnyLocalAddress() && !address.getAddress().isMulticastAddress();
    }

    private static boolean validPayload(byte[] payload) {
        return payload != null && payload.length > 0 && payload.length <= FragmentReassembler.MAX_ASSEMBLED_SIZE;
    }

    private static boolean handshakeUnsigned(RudpPacket.Type type) {
        return type == RudpPacket.Type.HELLO || type == RudpPacket.Type.HELLO_ACK;
    }

    private static String groupPrefix(RudpSession session) {
        return session.remoteConnectionId() + ":";
    }

    private static int comparePub(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            int difference = (a[i] & 255) - (b[i] & 255);
            if (difference != 0) {
                return difference;
            }
        }
        return 0;
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000;
    }

    synchronized long createInitiatorSessionForTest(InetSocketAddress address) {
        RudpSession session = connectSession(address, null);
        return session == null ? -1 : session.remoteConnectionId();
    }

    synchronized boolean hasRemotePubForTest(InetSocketAddress address) {
        RudpSession session = sessionsByAddress.get(address);
        return session != null && session.isAuthenticated();
    }

    synchronized long remoteConnectionIdForTest(InetSocketAddress address) {
        RudpSession session = sessionsByAddress.get(address);
        return session == null ? -1 : session.remoteConnectionId();
    }

    synchronized int pendingCountForTest(InetSocketAddress address) {
        RudpSession session = sessionsByAddress.get(address);
        return session == null ? 0 : session.pending().size();
    }

    synchronized InetSocketAddress remoteAddressForTest(long cid) {
        RudpSession session = sessionsByRemoteId.get(cid);
        return session == null ? null : session.remoteAddress();
    }
}
