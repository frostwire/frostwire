/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.util.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link PeerDiscoverySource} backed by the DHT. Resolves
 * the {@link SessionManager} (typically from
 * {@code BTEngine.getInstance()}) lazily on each call so the
 * source can be constructed before the engine is fully up.
 *
 * <p>For each {@link #fetchEndpoints()} call, runs a BEP 5
 * {@code dhtGetPeers} on the peer topic. For each
 * {@link #fetchIdentityEntry(byte[])} call, runs a BEP 46
 * {@code dhtGetItem} with the {@link IdentityRecord#BEP46_SALT}.
 */
public final class DhtPeerDiscoverySource implements PeerDiscoverySource {

    private static final Logger LOG = Logger.getLogger(DhtPeerDiscoverySource.class);

    /** BEP 5 lookup timeout. */
    public static final int DEFAULT_DISCOVERY_TIMEOUT_SEC = 5;

    private final SessionManager session;
    private final int discoveryTimeoutSec;
    private final int identityTimeoutMs;

    public DhtPeerDiscoverySource(SessionManager session) {
        this(session, DEFAULT_DISCOVERY_TIMEOUT_SEC, PeerDiscovery.DEFAULT_IDENTITY_TIMEOUT_MS);
    }

    public DhtPeerDiscoverySource(SessionManager session,
                                  int discoveryTimeoutSec,
                                  int identityTimeoutMs) {
        if (discoveryTimeoutSec <= 0) {
            throw new IllegalArgumentException("discoveryTimeoutSec must be > 0");
        }
        if (identityTimeoutMs <= 0) {
            throw new IllegalArgumentException("identityTimeoutMs must be > 0");
        }
        this.session = session;
        this.discoveryTimeoutSec = discoveryTimeoutSec;
        this.identityTimeoutMs = identityTimeoutMs;
    }

    @Override
    public List<DiscoveredEndpoint> fetchEndpoints() {
        if (session == null) {
            return new ArrayList<>();
        }
        try {
            ArrayList<TcpEndpoint> endpoints = DhtRendezvous.findPeers(session, discoveryTimeoutSec);
            List<DiscoveredEndpoint> result = new ArrayList<>(endpoints.size());
            for (TcpEndpoint ep : endpoints) {
                String host = ep.address() == null ? null : ep.address().toString();
                int port = ep.port();
                if (host == null || host.isEmpty() || port <= 0) {
                    continue;
                }
                result.add(new DiscoveredEndpoint(host, port));
            }
            return result;
        } catch (Throwable t) {
            LOG.debug("DHT findPeers failed", t);
            return new ArrayList<>();
        }
    }

    @Override
    public Entry fetchIdentityEntry(byte[] peerPub) {
        if (session == null || peerPub == null || peerPub.length != 32) {
            return null;
        }
        try {
            byte[] salt = IdentityRecord.BEP46_SALT
                    .getBytes(StandardCharsets.US_ASCII);
            SessionManager.MutableItem item = session.dhtGetItem(peerPub, salt, identityTimeoutMs);
            if (item == null) {
                return null;
            }
            return item.item;
        } catch (Throwable t) {
            LOG.debug("DHT getItem failed for " +
                    com.frostwire.util.Hex.encode(peerPub), t);
            return null;
        }
    }
}
