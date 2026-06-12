/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;

/**
 * Pluggable transport for fetching a remote peer's karma chain
 * manifest. The default implementation, {@link DhtKarmaChainSource},
 * queries the BEP 46 mutable DHT. Tests can substitute a fake
 * source to avoid spinning up a real DHT cluster.
 *
 * <p>Implementations MUST verify the publisher's signature
 * before returning; callers trust the returned manifest to
 * genuinely come from {@code peerPub}.
 */
public interface KarmaChainSource {

    /**
     * Fetch the manifest for {@code peerPub}, or null if the peer
     * has no published chain or the lookup failed.
     */
    Entry fetchManifest(byte[] peerPub);
}
