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
        List<DiscoveredEndpoint> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        try {
            // Always aggressively discover dedicated relayers first (via frostwire-relays-v1),
            // then peers. This ensures desktop finds remote IceBridge relays easily via DHT
            // and can use them for routing search/index commands over the rUDP mesh.
            ArrayList<TcpEndpoint> relays = DhtRendezvous.findRelays(session, discoveryTimeoutSec);
            addEndpoints(relays, result, seen);

            ArrayList<TcpEndpoint> endpoints = DhtRendezvous.findPeers(session, discoveryTimeoutSec);
            addEndpoints(endpoints, result, seen);

            if (result.isEmpty()) {
                ArrayList<TcpEndpoint> bootstrap = DhtRendezvous.findBootstrapNodes(session, discoveryTimeoutSec);
                addEndpoints(bootstrap, result, seen);
            }
        } catch (Throwable t) {
            LOG.debug("DHT discovery failed", t);
        }
        return result;
    }

    private static void addEndpoints(ArrayList<TcpEndpoint> endpoints,
                                     List<DiscoveredEndpoint> result,
                                     java.util.Set<String> seen) {
        if (endpoints == null || endpoints.isEmpty()) {
            return;
        }
        // Snapshot before iterating — live DHT lists can change mid-walk and
        // ArrayList's iterator throws ConcurrentModificationException.
        TcpEndpoint[] snapshot = endpoints.toArray(new TcpEndpoint[0]);
        for (TcpEndpoint ep : snapshot) {
            if (ep == null) {
                continue;
            }
            String host = ep.address() == null ? null : ep.address().toString();
            int port = ep.port();
            if (host == null || host.isEmpty() || port <= 0) {
                continue;
            }
            String key = host + ":" + port;
            if (seen.add(key)) {
                result.add(new DiscoveredEndpoint(host, port));
            }
        }
    }

    /**
     * Package-private helper for unit tests of concurrent-safe collection.
     */
    static List<DiscoveredEndpoint> collectEndpointsForTest(ArrayList<TcpEndpoint> endpoints) {
        List<DiscoveredEndpoint> result = new ArrayList<>();
        addEndpoints(endpoints, result, new java.util.HashSet<>());
        return result;
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
