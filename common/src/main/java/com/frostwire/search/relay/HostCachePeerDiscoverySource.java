/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.search.relay.icebridge.IceBridgeHostCache;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link PeerDiscoverySource} that returns the {@link IceBridgeHostCache} entries and the built-in
 * seed servers as discovery candidates.
 *
 * <p>Cold-start bootstrap: a client that knows a server (it verified it before, an operator added it,
 * or it is a built-in seed) rejoins the mesh through it without waiting for DHT discovery. Every
 * candidate is {@link DiscoveredEndpoint#preferred preferred}, so it is probed before the crowd the DHT
 * returns, and it is retried with a short back-off. Entries that never answered are still returned:
 * the host cache evicts them after repeated failures, and a server that was down for a deploy
 * must come back on its own. The endpoints still go through {@link PeerDiscovery}'s identity
 * handshake, self-skip, and dedup.
 */
public final class HostCachePeerDiscoverySource implements PeerDiscoverySource {

    private final IceBridgeHostCache cache;
    private final List<String> seeds;

    /** The shared host cache plus the built-in seed servers. */
    public HostCachePeerDiscoverySource() {
        this(IceBridgeHostCache.getInstance(), RelayConstants.seedHosts());
    }

    public HostCachePeerDiscoverySource(IceBridgeHostCache cache) {
        this(cache, new ArrayList<>());
    }

    public HostCachePeerDiscoverySource(IceBridgeHostCache cache, List<String> seeds) {
        if (cache == null) {
            throw new IllegalArgumentException("cache is null");
        }
        this.cache = cache;
        this.seeds = seeds != null ? new ArrayList<>(seeds) : new ArrayList<>();
    }

    @Override
    public List<DiscoveredEndpoint> fetchEndpoints() {
        List<DiscoveredEndpoint> out = new ArrayList<>();
        for (IceBridgeHostCache.Entry e : cache.getAll()) {
            if (e.host != null && !e.host.isEmpty() && e.port > 0) {
                out.add(new DiscoveredEndpoint(e.host, e.port, true));
            }
        }
        for (String seed : seeds) {
            int colon = seed.lastIndexOf(':');
            if (colon <= 0) continue;
            try {
                int port = Integer.parseInt(seed.substring(colon + 1).trim());
                if (port > 0 && port <= 65535) {
                    out.add(new DiscoveredEndpoint(seed.substring(0, colon).trim(), port, true));
                }
            } catch (NumberFormatException ignored) {
                // malformed seed
            }
        }
        return out;
    }

    @Override
    public Entry fetchIdentityEntry(byte[] peerPub) {
        return null;
    }
}
