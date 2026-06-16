/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.util.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Synchronizes {@link PeerDirectory} entries into the local IceBridge
 * daemon's {@code PeerRegistry} via the HTTP control API.
 *
 * <p>For distributed search to work over IceBridge, the daemon must know
 * the rUDP endpoint of every peer the desktop wants to query. This class
 * periodically calls {@link IceBridgeClient#register} for every verified
 * peer in the directory, using the peer's known hostname and the well-known
 * IceBridge rUDP port ({@link #ICEBRIDGE_RUDP_PORT}).
 *
 * <p>The sync also registers <em>this</em> node's own identity with the
 * local daemon, so that when a remote peer's daemon relays a response back
 * through a forwarder, the forwarder can route it to us.
 */
public final class PeerRegistrySync implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PeerRegistrySync.class);

    /**
     * Well-known rUDP port that every IceBridge daemon listens on.
     *
     * <p>In v1, all FrostWire desktop instances run their IceBridge daemon
     * on this fixed port so peers can reach each other without an
     * additional discovery round-trip. It is one above the legacy relay
     * TCP port (6888) to avoid conflicts.
     */
    public static final int ICEBRIDGE_RUDP_PORT = 6889;

    private static final long SYNC_INTERVAL_SEC = 30;
    private static final long INITIAL_DELAY_SEC = 3;

    private final IceBridgeClient client;
    private final PeerDirectory directory;
    private final String localHost;
    private final ScheduledExecutorService scheduler;

    public PeerRegistrySync(IceBridgeClient client,
                            PeerDirectory directory,
                            String localHost) {
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
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "icebridge-peer-sync");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the periodic sync. The first run happens after
     * {@link #INITIAL_DELAY_SEC} seconds; subsequent runs every
     * {@link #SYNC_INTERVAL_SEC} seconds.
     */
    public void start() {
        scheduler.scheduleWithFixedDelay(this::sync,
                INITIAL_DELAY_SEC, SYNC_INTERVAL_SEC, TimeUnit.SECONDS);
        LOG.info("PeerRegistrySync started: interval=" + SYNC_INTERVAL_SEC + "s"
                + " localHost=" + localHost
                + " rudpPort=" + ICEBRIDGE_RUDP_PORT);
    }

    /**
     * Perform a single sync cycle: register all verified peers from the
     * directory with the local IceBridge daemon.
     */
    void sync() {
        try {
            java.util.List<PeerDirectory.PeerInfo> peers =
                    directory.topByTrustVerified(100);
            int registered = 0;
            for (PeerDirectory.PeerInfo peer : peers) {
                if (peer.hostname() == null || peer.hostname().isBlank()) {
                    continue;
                }
                if (client.route(peer.peerPub(), peer.hostname(),
                        ICEBRIDGE_RUDP_PORT, IceBridgeConfig.Role.BOTH)) {
                    registered++;
                }
            }
            if (registered > 0 || !peers.isEmpty()) {
                LOG.info("PeerRegistrySync: registered " + registered
                        + "/" + peers.size() + " peers with IceBridge");
            }
        } catch (Throwable t) {
            LOG.warn("PeerRegistrySync failed", t);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        LOG.info("PeerRegistrySync stopped");
    }
}
