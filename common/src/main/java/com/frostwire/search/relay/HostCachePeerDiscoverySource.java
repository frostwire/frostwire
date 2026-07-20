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
 * {@link PeerDiscoverySource} that returns previously verified IceBridge
 * servers from the {@link IceBridgeHostCache} as discovery candidates.
 *
 * <p>Cold-start bootstrap: a client that has successfully pinged a server
 * before (visible in Settings → Distributed Search) should rejoin the mesh
 * through it without waiting for DHT discovery — and without any control
 * plane access, which stays strictly local. The endpoints still go through
 * {@link PeerDiscovery}'s identity handshake, self-skip, and dedup.
 */
public final class HostCachePeerDiscoverySource implements PeerDiscoverySource {

    private final IceBridgeHostCache cache;

    public HostCachePeerDiscoverySource() {
        this(IceBridgeHostCache.getInstance());
    }

    public HostCachePeerDiscoverySource(IceBridgeHostCache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("cache is null");
        }
        this.cache = cache;
    }

    @Override
    public List<DiscoveredEndpoint> fetchEndpoints() {
        List<DiscoveredEndpoint> out = new ArrayList<>();
        for (IceBridgeHostCache.Entry e : cache.getPingable(0)) {
            if (e.host != null && !e.host.isEmpty() && e.port > 0) {
                out.add(new DiscoveredEndpoint(e.host, e.port));
            }
        }
        return out;
    }

    @Override
    public Entry fetchIdentityEntry(byte[] peerPub) {
        return null;
    }
}
