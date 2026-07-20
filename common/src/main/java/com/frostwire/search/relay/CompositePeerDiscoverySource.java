/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Concatenates several {@link PeerDiscoverySource}s (e.g. host cache first,
 * then DHT) behind a single source. A source that throws does not fail the
 * others — discovery is best-effort by design.
 */
public final class CompositePeerDiscoverySource implements PeerDiscoverySource {

    private static final Logger LOG = Logger.getLogger(CompositePeerDiscoverySource.class);

    private final List<PeerDiscoverySource> sources;

    public CompositePeerDiscoverySource(PeerDiscoverySource... sources) {
        this(Arrays.asList(sources));
    }

    public CompositePeerDiscoverySource(List<PeerDiscoverySource> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("sources is empty");
        }
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
    }

    @Override
    public List<DiscoveredEndpoint> fetchEndpoints() {
        List<DiscoveredEndpoint> out = new ArrayList<>();
        for (PeerDiscoverySource source : sources) {
            if (source == null) {
                continue;
            }
            try {
                List<DiscoveredEndpoint> endpoints = source.fetchEndpoints();
                if (endpoints != null) {
                    out.addAll(endpoints);
                }
            } catch (Throwable t) {
                LOG.debug("CompositePeerDiscoverySource: source failed", t);
            }
        }
        return out;
    }

    @Override
    public Entry fetchIdentityEntry(byte[] peerPub) {
        for (PeerDiscoverySource source : sources) {
            if (source == null) {
                continue;
            }
            try {
                Entry e = source.fetchIdentityEntry(peerPub);
                if (e != null) {
                    return e;
                }
            } catch (Throwable t) {
                LOG.debug("CompositePeerDiscoverySource: identity fetch failed", t);
            }
        }
        return null;
    }
}
