/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IndexDigest;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.search.relay.NodeCapabilities;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.event.IceBridgeEvents;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.search.relay.icebridge.NodeMetaPayload;
import com.frostwire.search.relay.icebridge.control.PeerInfo;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Synchronizes {@link PeerDirectory} with the IceBridge mesh registry.
 *
 * <p><b>Push:</b> routes every verified directory peer into the local (or
 * remote) IceBridge {@code PeerRegistry} so {@code /send} can reach them.
 * Also registers this node's identity so peers can route back to us.
 *
 * <p><b>Pull (forwarder-first discovery):</b> imports peers from IceBridge
 * {@code GET /lookup} into {@link PeerDirectory} as verified entries. Mesh
 * registration is already Ed25519-signed at the forwarder; this is the path
 * when direct TCP identity (home NAT) is unreachable — DESIGN_RELAY_REGISTRY
 * hybrid plane + §8 mesh search.
 */
public final class PeerRegistrySync implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PeerRegistrySync.class);

    public static final int ICEBRIDGE_RUDP_PORT = 6889;

    private static final long SYNC_INTERVAL_SEC = 30;
    private static final long INITIAL_DELAY_SEC = 3;
    /**
     * Registry page size per sync. {@code /lookup} clamps to 100; rotation (see
     * {@code PeerRegistry.lookupPeers}) means successive syncs walk different slices, so the
     * directory converges on the whole registry instead of a fixed prefix.
     */
    private static final int LOOKUP_COUNT = 100;
    private static final byte[] WARM_PING = {0x01};
    /**
     * Ignore mesh entries the forwarder has not seen recently. A registry TTL is short; a peer
     * older than this is already gone and must not be re-promoted into the search directory.
     */
    private static final long MAX_LOOKUP_AGE_MS = 10 * 60_000L;
    /** Never rebuild the index digest more often than this (SHA-256 over every name/path). */
    private static final long DIGEST_REBUILD_INTERVAL_MS = 120_000L;
    /** Force a re-announce even when unchanged, so a restarted relay relearns our digest. */
    private static final long DIGEST_REFRESH_INTERVAL_MS = 120_000L;
    /** Bounded fan-out for digest announcements. */
    private static final int DIGEST_TARGETS = 16;
    /** Bounded fan-out for capability/role announcements. */
    private static final int NODE_META_TARGETS = 16;

    private final IceBridgeClient client;
    private final PeerDirectory directory;
    private final String localHost;
    private final int rudpPort;
    private final IdentityKeys identity;
    private final IceBridgeConfig.Role localRole;
    private final byte[] ownPub;
    private final LocalIndex index;
    private final ScheduledExecutorService scheduler;
    private volatile byte[] lastDigest;
    private volatile long lastDigestBuildMs;
    private volatile long lastDigestSendMs;
    private volatile long lastNodeMetaSendMs;

    public PeerRegistrySync(IceBridgeClient client,
                            PeerDirectory directory,
                            String localHost) {
        this(client, directory, localHost, ICEBRIDGE_RUDP_PORT, null, IceBridgeConfig.Role.BOTH);
    }

    public PeerRegistrySync(IceBridgeClient client,
                            PeerDirectory directory,
                            String localHost,
                            int rudpPort) {
        this(client, directory, localHost, rudpPort, null, IceBridgeConfig.Role.BOTH);
    }

    public PeerRegistrySync(IceBridgeClient client,
                            PeerDirectory directory,
                            String localHost,
                            int rudpPort,
                            IdentityKeys identity,
                            IceBridgeConfig.Role localRole) {
        this(client, directory, localHost, rudpPort, identity, localRole, null);
    }

    public PeerRegistrySync(IceBridgeClient client,
                            PeerDirectory directory,
                            String localHost,
                            int rudpPort,
                            IdentityKeys identity,
                            IceBridgeConfig.Role localRole,
                            LocalIndex index) {
        if (client == null) {
            throw new IllegalArgumentException("client is null");
        }
        if (directory == null) {
            throw new IllegalArgumentException("directory is null");
        }
        if (localHost == null || localHost.isBlank()) {
            throw new IllegalArgumentException("localHost is null or blank");
        }
        this.client = client;
        this.directory = directory;
        this.localHost = localHost;
        this.rudpPort = rudpPort > 0 ? rudpPort : ICEBRIDGE_RUDP_PORT;
        this.identity = identity;
        this.localRole = localRole != null ? localRole : IceBridgeConfig.Role.BOTH;
        this.ownPub = identity != null ? identity.ed25519PubRaw() : null;
        this.index = index;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "icebridge-peer-sync");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::sync,
                INITIAL_DELAY_SEC, SYNC_INTERVAL_SEC, TimeUnit.SECONDS);
        LOG.info("PeerRegistrySync started: interval=" + SYNC_INTERVAL_SEC + "s"
                + " localHost=" + localHost
                + " rudpPort=" + rudpPort
                + " registerSelf=" + (identity != null));
    }

    /**
     * One sync cycle: register self, push verified peers to IceBridge, pull
     * mesh peers into the directory (forwarder-first discovery).
     *
     * <p>Public so a fresh install can warm the mesh registry on the startup
     * path instead of waiting for the first scheduled tick.
     */
    public void sync() {
        try {
            registerSelf();
            pushDirectoryToMesh();
            pullMeshIntoDirectory();
            maintainUplinks();
            publishIndexDigest();
            publishNodeMeta();
        } catch (Throwable t) {
            LOG.warn("PeerRegistrySync failed", t);
        }
    }

    private void registerSelf() {
        if (identity == null) {
            return;
        }
        try {
            boolean ok = client.register(identity, localHost, rudpPort, localRole);
            if (ok) {
                LOG.debug("PeerRegistrySync: registered self " + localHost + ":" + rudpPort
                        + " role=" + localRole);
            }
        } catch (Throwable t) {
            LOG.debug("PeerRegistrySync: self-register failed", t);
        }
    }

    private void pushDirectoryToMesh() {
        List<PeerDirectory.PeerInfo> peers = directory.topByTrustVerified(100);
        int registered = 0;
        for (PeerDirectory.PeerInfo peer : peers) {
            if (peer.hostname() == null || peer.hostname().isBlank()) {
                continue;
            }
            int peerRudpPort = peer.rudpPort() > 0 ? peer.rudpPort() : rudpPort;
            // Push the peer's real role, not a blanket BOTH: advertising a leaf as a
            // forwarder makes relays flood through nodes that cannot relay.
            if (client.route(peer.peerPub(), peer.hostname(),
                    peerRudpPort, roleForCaps(peer.capabilities()))) {
                registered++;
                warmMeshPeer(peer.peerPub());
            }
        }
        if (registered > 0 || !peers.isEmpty()) {
            LOG.info("PeerRegistrySync: routed " + registered
                    + "/" + peers.size() + " directory peers to IceBridge");
        }
    }

    /** Derive the registry role label from an advertised capability bitmask. */
    static IceBridgeConfig.Role roleForCaps(long capabilities) {
        if (!NodeCapabilities.has(capabilities, NodeCapabilities.RELAY)) {
            return IceBridgeConfig.Role.CLIENT;
        }
        return NodeCapabilities.has(capabilities, NodeCapabilities.SEARCH)
                ? IceBridgeConfig.Role.BOTH
                : IceBridgeConfig.Role.FORWARDER;
    }

    /**
     * One-byte TELEMETRY ping to a routed peer. Forces the rUDP session
     * (HELLO/HELLO_ACK) so the far side learns our observed endpoint, and
     * doubles as a NAT-mapping keepalive for inbound responses.
     */
    private void warmMeshPeer(byte[] peerPub) {
        try {
            client.send(peerPub, MeshProtocolId.TELEMETRY, WARM_PING);
        } catch (Throwable t) {
            LOG.debug("PeerRegistrySync: mesh warm failed", t);
        }
    }

    /**
     * Announce this node's {@link IndexDigest} to directory peers so a forwarder can route
     * searches to likely holders instead of fanning out blindly.
     *
     * <p>The digest is rebuilt at most every {@link #DIGEST_REBUILD_INTERVAL_MS} and re-sent when it
     * changed or after {@link #DIGEST_REFRESH_INTERVAL_MS} (so a restarted relay relearns it).
     */
    private void publishIndexDigest() {
        LocalIndex localIndex = this.index;
        if (localIndex == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            byte[] digest = lastDigest;
            if (digest == null || now - lastDigestBuildMs > DIGEST_REBUILD_INTERVAL_MS) {
                digest = buildIndexDigest(localIndex);
                lastDigestBuildMs = now;
            }
            if (digest == null) {
                return;
            }
            boolean changed = lastDigest == null || !Arrays.equals(digest, lastDigest);
            boolean stale = now - lastDigestSendMs > DIGEST_REFRESH_INTERVAL_MS;
            if (!changed && !stale) {
                return;
            }
            List<PeerDirectory.PeerInfo> targets =
                    directory.topByTrustVerified(DIGEST_TARGETS, NodeCapabilities.RELAY);
            if (targets.isEmpty()) {
                targets = directory.topByTrustVerified(DIGEST_TARGETS);
            }
            int sent = 0;
            for (PeerDirectory.PeerInfo peer : targets) {
                if (ownPub != null && Arrays.equals(peer.peerPub(), ownPub)) {
                    continue;
                }
                if (client.send(peer.peerPub(), MeshProtocolId.INDEX_DIGEST, digest)) {
                    sent++;
                }
            }
            if (sent > 0) {
                lastDigest = digest;
                lastDigestSendMs = now;
                LOG.debug("PeerRegistrySync: announced index digest to " + sent + "/"
                        + targets.size() + " peers");
                IceBridgeEvents.digest("", "announced index digest to " + sent + "/"
                        + targets.size() + " peers");
            }
        } catch (Throwable t) {
            LOG.debug("PeerRegistrySync: digest announce failed", t);
        }
    }

    private static byte[] buildIndexDigest(LocalIndex index) {
        List<LocalSharedTorrent> rows = index.listAll();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        List<String> texts = new ArrayList<>(rows.size() * 2);
        for (LocalSharedTorrent torrent : rows) {
            if (torrent == null) {
                continue;
            }
            texts.add(torrent.name());
            String files = torrent.filesJson();
            if (files != null && !files.isEmpty()) {
                texts.add(files);
            }
        }
        if (texts.isEmpty()) {
            return null;
        }
        return IndexDigest.build(texts).toBytes();
    }

    /**
     * Announce this node's capability bitmask to directory peers.
     *
     * <p>The mesh registry only learns a peer's role from an explicit {@code /register} or
     * {@code /route}; a relay that only ever observed the peer's authenticated session records it
     * as a bare CLIENT. Announcing capabilities over the mesh lets those relays correct the peer's
     * advertised role/capabilities instead of treating it as capability-less forever.
     */
    private void publishNodeMeta() {
        try {
            long now = System.currentTimeMillis();
            if (now - lastNodeMetaSendMs < DIGEST_REFRESH_INTERVAL_MS) {
                return;
            }
            byte[] meta = NodeMetaPayload.encode(NodeCapabilities.fromRole(localRole.name()));
            List<PeerDirectory.PeerInfo> targets =
                    directory.topByTrustVerified(NODE_META_TARGETS, NodeCapabilities.RELAY);
            if (targets.isEmpty()) {
                targets = directory.topByTrustVerified(NODE_META_TARGETS);
            }
            int sent = 0;
            for (PeerDirectory.PeerInfo peer : targets) {
                if (ownPub != null && Arrays.equals(peer.peerPub(), ownPub)) {
                    continue;
                }
                if (client.send(peer.peerPub(), MeshProtocolId.NODE_META, meta)) {
                    sent++;
                }
            }
            if (sent > 0) {
                lastNodeMetaSendMs = now;
                LOG.debug("PeerRegistrySync: announced node meta to " + sent + "/"
                        + targets.size() + " peers");
            }
        } catch (Throwable t) {
            LOG.debug("PeerRegistrySync: node meta announce failed", t);
        }
    }

    private void pullMeshIntoDirectory() {
        List<PeerInfo> mesh;
        try {
            mesh = client.lookup(LOOKUP_COUNT);
        } catch (Throwable t) {
            LOG.debug("PeerRegistrySync: lookup failed", t);
            return;
        }
        if (mesh == null || mesh.isEmpty()) {
            return;
        }
        int imported = 0;
        for (PeerInfo info : mesh) {
            if (info == null || info.pub == null || info.host == null || info.host.isBlank()) {
                continue;
            }
            if (info.rudpPort <= 0 || info.rudpPort > 65535) {
                continue;
            }
            byte[] pub;
            try {
                pub = Base64.getUrlDecoder().decode(info.pub);
            } catch (IllegalArgumentException e) {
                try {
                    pub = Base64.getDecoder().decode(info.pub);
                } catch (IllegalArgumentException e2) {
                    continue;
                }
            }
            if (pub.length != 32) {
                continue;
            }
            if (ownPub != null && Arrays.equals(pub, ownPub)) {
                continue;
            }
            // Stale registry rows describe peers the forwarder has not heard from in a long time;
            // importing them would re-add dead routes to the search directory.
            if (info.lastSeenMs > 0
                    && System.currentTimeMillis() - info.lastSeenMs > MAX_LOOKUP_AGE_MS) {
                IceBridgeEvents.peer(Hex.encode(pub), "skipped stale /lookup entry");
                continue;
            }
            // Identity TCP port unknown from mesh registry; use rUDP port as
            // contact hint. Search uses peerPub + rudpPort via IceBridge.
            // utpPort is the identity-plane port slot; prefer rudp for mesh data.
            IceBridgeConfig.Role role =
                    info.role != null ? info.role : IceBridgeConfig.Role.BOTH;
            long caps = com.frostwire.search.relay.NodeCapabilities.fromRole(
                    role != null ? role.name() : "BOTH");
            directory.upsertVerified(pub, info.host, info.rudpPort, info.rudpPort,
                    caps, info.icebridgeVersion);
            client.route(pub, info.host, info.rudpPort, role);
            // Seed host cache for Settings → Refresh/Ping (TCP identity on 6888).
            // Skip loopback USE_REMOTE self-registrations; only public/remote hosts.
            if (!isLoopbackHost(info.host)
                    && (role == IceBridgeConfig.Role.FORWARDER
                    || role == IceBridgeConfig.Role.BOTH)) {
                try {
                    com.frostwire.search.relay.icebridge.IceBridgeHostCache.getInstance()
                            .addOrUpdate(info.host,
                                    com.frostwire.search.relay.RelayConstants.RELAY_LISTEN_PORT,
                                    role.name());
                } catch (Throwable ignored) {
                }
            }
            imported++;
        }
        if (imported > 0) {
            LOG.info("PeerRegistrySync: imported " + imported
                    + " mesh peers into PeerDirectory (forwarder-first discovery)");
        }
    }

    /**
     * Keep a Gnutella-shaped set of sessions to powerful nodes. A leaf warms at most
     * {@link com.frostwire.search.relay.icebridge.IceBridgeTopology#LEAF_MAX_UPLINKS}
     * RELAY peers (and every one it knows, once that is at least the minimum). An
     * ultrapeer warms up to {@link com.frostwire.search.relay.icebridge.IceBridgeTopology#ULTRAPEER_FANOUT}.
     * The set is resampled each sync so 100k leaves do not all pin the same hubs.
     */
    private void maintainUplinks() {
        int degree = com.frostwire.search.relay.icebridge.IceBridgeTopology.uplinkDegree(localRole);
        java.util.Set<String> exclude = new java.util.HashSet<>();
        if (ownPub != null) {
            exclude.add(Hex.encode(ownPub));
        }
        List<PeerDirectory.PeerInfo> hubs = directory.sampleVerified(
                degree, exclude, NodeCapabilities.RELAY, java.util.concurrent.ThreadLocalRandom.current());
        int warmed = 0;
        for (PeerDirectory.PeerInfo hub : hubs) {
            warmMeshPeer(hub.peerPub());
            warmed++;
        }
        if (warmed > 0) {
            LOG.debug("PeerRegistrySync: uplinks " + warmed + "/" + degree + " role=" + localRole);
        }
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null || host.isEmpty()) {
            return true;
        }
        String h = host.trim().toLowerCase(java.util.Locale.ROOT);
        return "127.0.0.1".equals(h)
                || "localhost".equals(h)
                || "::1".equals(h)
                || "0.0.0.0".equals(h)
                || h.startsWith("127.");
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        LOG.info("PeerRegistrySync stopped");
    }
}
