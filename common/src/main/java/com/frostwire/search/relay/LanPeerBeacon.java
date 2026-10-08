/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Finds FrostWire nodes on the same local network.
 *
 * <p>The DHT only knows a node by its public address, and a router normally refuses to connect a
 * machine to its own public address (hairpin NAT), so two computers on the same wifi never find each
 * other through it. Each node therefore periodically multicasts a tiny datagram carrying its TCP
 * identity port; receivers take the sender's private address and offer {@code address:port} as a
 * {@link DiscoveredEndpoint#preferred preferred} candidate. The beacon is only a hint: the endpoint
 * still has to pass the authenticated identity handshake in {@link PeerDiscovery} before anything
 * is trusted, and nothing is ever answered.
 */
public final class LanPeerBeacon implements PeerDiscoverySource, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(LanPeerBeacon.class);

    /** Administratively scoped multicast group (never routed beyond the local network). */
    public static final String GROUP = "239.255.70.88";
    public static final int UDP_PORT = 6890;
    public static final long ANNOUNCE_INTERVAL_MS = 10_000;
    public static final long ENTRY_TTL_MS = 120_000;
    static final int MAX_ENTRIES = 128;
    private static final String MAGIC = "FWIB1";
    private static final int MAX_DATAGRAM = 64;

    private final int identityPort;
    private final int udpPort;
    private final List<InetSocketAddress> directTargets;
    private final Predicate<InetAddress> acceptSender;
    private final long announceIntervalMs;
    private final LongSupplier clockMs;
    private final boolean multicast;
    private final Map<String, Long> seen = new LinkedHashMap<>();
    private volatile boolean closed;
    private MulticastSocket socket;
    private Thread sender;
    private Thread receiver;

    /** Production beacon: multicast on every local interface, accepting private senders only. */
    public LanPeerBeacon(int identityPort) {
        this(identityPort, UDP_PORT, Collections.emptyList(), LanPeerBeacon::isPrivateLanAddress,
                ANNOUNCE_INTERVAL_MS, System::currentTimeMillis, true);
    }

    /** Test seam: unicast {@code directTargets} instead of (or besides) the multicast group. */
    LanPeerBeacon(int identityPort, int udpPort, List<InetSocketAddress> directTargets,
                  Predicate<InetAddress> acceptSender, long announceIntervalMs,
                  LongSupplier clockMs, boolean multicast) {
        if (identityPort <= 0 || identityPort > 65535) {
            throw new IllegalArgumentException("identityPort out of range: " + identityPort);
        }
        this.identityPort = identityPort;
        this.udpPort = udpPort;
        this.directTargets = new ArrayList<>(directTargets);
        this.acceptSender = acceptSender;
        this.announceIntervalMs = announceIntervalMs;
        this.clockMs = clockMs;
        this.multicast = multicast;
    }

    /** Opens the socket and starts announcing/listening. Failure is non-fatal (wifi without multicast). */
    public synchronized void start() {
        if (socket != null || closed) return;
        try {
            MulticastSocket s = new MulticastSocket(null);
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(udpPort));
            s.setSoTimeout(1000);
            if (multicast) {
                InetAddress group = InetAddress.getByName(GROUP);
                int joined = 0;
                for (NetworkInterface iface : lanInterfaces()) {
                    try {
                        s.joinGroup(new InetSocketAddress(group, 0), iface);
                        joined++;
                    } catch (IOException ignored) {
                        // interface without multicast support
                    }
                }
                if (joined == 0 && directTargets.isEmpty()) {
                    LOG.info("LanPeerBeacon: no multicast-capable network interface, LAN discovery is off");
                    s.close();
                    return;
                }
            }
            socket = s;
            receiver = daemon("lan-beacon-receive", this::receiveLoop);
            sender = daemon("lan-beacon-announce", this::announceLoop);
            receiver.start();
            sender.start();
            LOG.info("LanPeerBeacon started, identity port " + identityPort);
        } catch (IOException | RuntimeException e) {
            LOG.info("LanPeerBeacon unavailable, LAN discovery is off: " + e);
        }
    }

    @Override
    public List<DiscoveredEndpoint> fetchEndpoints() {
        long cutoff = clockMs.getAsLong() - ENTRY_TTL_MS;
        List<DiscoveredEndpoint> out = new ArrayList<>();
        synchronized (seen) {
            seen.values().removeIf(lastSeen -> lastSeen < cutoff);
            for (String key : seen.keySet()) {
                int colon = key.lastIndexOf(':');
                out.add(new DiscoveredEndpoint(key.substring(0, colon),
                        Integer.parseInt(key.substring(colon + 1)), true));
            }
        }
        return out;
    }

    @Override
    public Entry fetchIdentityEntry(byte[] peerPub) {
        return null;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        for (Thread t : new Thread[]{sender, receiver}) {
            if (t != null) t.interrupt();
        }
    }

    // ---- wire format ----

    static byte[] encode(int port) {
        return (MAGIC + " " + port).getBytes(StandardCharsets.US_ASCII);
    }

    /** The advertised identity port, or -1 for anything that is not a well-formed beacon. */
    static int decode(byte[] data, int length) {
        if (length <= 0 || length > MAX_DATAGRAM) return -1;
        String text = new String(data, 0, length, StandardCharsets.US_ASCII);
        if (!text.startsWith(MAGIC + " ")) return -1;
        String number = text.substring(MAGIC.length() + 1);
        if (number.isEmpty() || number.length() > 5 || !number.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        int port = Integer.parseInt(number);
        return port > 0 && port <= 65535 ? port : -1;
    }

    /** RFC 1918 private IPv4 space: the only senders a LAN beacon can honestly come from. */
    static boolean isPrivateLanAddress(InetAddress address) {
        return address instanceof java.net.Inet4Address && address.isSiteLocalAddress();
    }

    // ---- loops ----

    private void announceLoop() {
        byte[] payload = encode(identityPort);
        while (!closed) {
            MulticastSocket s = socket;
            if (s == null) return;
            try {
                if (multicast) {
                    InetSocketAddress group = new InetSocketAddress(GROUP, udpPort);
                    for (NetworkInterface iface : lanInterfaces()) {
                        try {
                            s.setNetworkInterface(iface);
                            s.send(new DatagramPacket(payload, payload.length, group));
                        } catch (IOException ignored) {
                            // this interface cannot send multicast right now
                        }
                    }
                }
                for (InetSocketAddress target : directTargets) {
                    s.send(new DatagramPacket(payload, payload.length, target));
                }
            } catch (IOException | RuntimeException e) {
                if (closed) return;
                LOG.debug("LanPeerBeacon announce failed: " + e);
            }
            try {
                Thread.sleep(announceIntervalMs);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM + 1];
        while (!closed) {
            MulticastSocket s = socket;
            if (s == null) return;
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                s.receive(packet);
            } catch (SocketTimeoutException timeout) {
                continue;
            } catch (IOException e) {
                if (closed) return;
                continue;
            }
            int port = decode(packet.getData(), packet.getLength());
            InetAddress sender = packet.getAddress();
            if (port < 0 || sender == null || !acceptSender.test(sender)) continue;
            remember(sender.getHostAddress(), port);
        }
    }

    void remember(String host, int port) {
        synchronized (seen) {
            String key = host + ":" + port;
            seen.remove(key);
            seen.put(key, clockMs.getAsLong());
            while (seen.size() > MAX_ENTRIES) {
                seen.remove(seen.keySet().iterator().next());
            }
        }
    }

    private static List<NetworkInterface> lanInterfaces() {
        List<NetworkInterface> out = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
                 e != null && e.hasMoreElements(); ) {
                NetworkInterface iface = e.nextElement();
                try {
                    if (iface.isUp() && iface.supportsMulticast() && !iface.isLoopback()
                            && !iface.isPointToPoint() && hasIpv4(iface)) {
                        out.add(iface);
                    }
                } catch (IOException ignored) {
                    // skip
                }
            }
        } catch (IOException ignored) {
            // no interfaces
        }
        return out;
    }

    private static boolean hasIpv4(NetworkInterface iface) {
        for (Enumeration<InetAddress> a = iface.getInetAddresses(); a.hasMoreElements(); ) {
            if (a.nextElement() instanceof java.net.Inet4Address) return true;
        }
        return false;
    }

    private static Thread daemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        return thread;
    }
}
