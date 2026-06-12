/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;

import java.util.List;

/**
 * Pluggable transport for {@link PeerDiscovery}. The default
 * implementation, {@link DhtPeerDiscoverySource}, queries the
 * BEP 5 peer topic and BEP 46 identity records over the DHT.
 * Tests can substitute a fake source to avoid spinning up a
 * real DHT cluster.
 */
public interface PeerDiscoverySource {
    /**
     * Run a BEP 5 find_peers lookup against the peer topic.
     * Returns the discovered (host, port) endpoints.
     */
    List<DiscoveredEndpoint> fetchEndpoints();

    /**
     * Fetch the BEP 46 identity record entry for a known pubkey.
     * Returns null on any failure or if no record is published.
     */
    Entry fetchIdentityEntry(byte[] peerPub);
}
