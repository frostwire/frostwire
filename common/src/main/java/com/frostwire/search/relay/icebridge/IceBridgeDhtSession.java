/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.util.Logger;

/**
 * Minimal jlibtorrent session for standalone IceBridge DHT announce/lookup.
 *
 * <p>Does not download torrents. Enables DHT only, with public bootstrap nodes,
 * so pure FORWARDER servents can appear on {@code frostwire-relays-v1} without
 * a FrostWire desktop {@code BTEngine} process.
 *
 * <p>Fail-closed: construction that cannot start the native session throws;
 * callers should log and continue without DHT rather than crash the mesh.
 */
public final class IceBridgeDhtSession implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(IceBridgeDhtSession.class);

    /** Same public DHT bootstrap set used by BTEngine. */
    static final String DHT_BOOTSTRAP_NODES =
            "dht.libtorrent.org:25401,"
                    + "router.bittorrent.com:6881,"
                    + "dht.transmissionbt.com:6881,"
                    + "router.silotis.us:6881";

    private final SessionManager session;

    private IceBridgeDhtSession(SessionManager session) {
        this.session = session;
    }

    /**
     * Start a DHT-only session listening on {@code host:0} (ephemeral UDP)
     * for libtorrent DHT traffic. Mesh rUDP stays on IceBridge's own port.
     *
     * @param host bind host from {@link IceBridgeConfig#host()} (e.g. 0.0.0.0)
     */
    public static IceBridgeDhtSession start(String host) {
        if (host == null || host.isEmpty()) {
            host = "0.0.0.0";
        }
        SessionManager sm = new SessionManager(false);
        SettingsPack sp = new SettingsPack();
        sp.setEnableDht(true);
        sp.setEnableLsd(false);
        sp.setDhtBootstrapNodes(DHT_BOOTSTRAP_NODES);
        // Ephemeral UDP for DHT only — does not steal IceBridge rUDP port.
        String listen = host + ":0";
        sp.listenInterfaces(listen);
        sp.activeDownloads(0);
        sp.activeSeeds(0);
        sm.start(new SessionParams(sp));
        LOG.info("IceBridge DHT session started (listen=" + listen + ", dht enabled)");
        return new IceBridgeDhtSession(sm);
    }

    public SessionManager session() {
        return session;
    }

    public boolean isDhtRunning() {
        try {
            return session != null && session.isDhtRunning();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void close() {
        if (session == null) {
            return;
        }
        try {
            session.stop();
            LOG.info("IceBridge DHT session stopped");
        } catch (Throwable t) {
            LOG.warn("IceBridge DHT session stop failed", t);
        }
    }
}
