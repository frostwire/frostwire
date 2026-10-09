/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Logger;

import java.util.Arrays;

/** Fetches a peer's signed {@link IdentityRecord} (BEP 46) from a discovery source. */
final class IdentityRecordLookup {
    private static final Logger LOG = Logger.getLogger(IdentityRecordLookup.class);

    private IdentityRecordLookup() {
    }

    /** The record published by {@code peerPub}, or null on any failure or key mismatch. */
    static IdentityRecord fetch(PeerDiscoverySource source, byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32 || Thread.currentThread().isInterrupted()) {
            return null;
        }
        try {
            Entry entry = source.fetchIdentityEntry(peerPub);
            if (entry == null || Thread.currentThread().isInterrupted()) {
                return null;
            }
            IdentityRecord record = IdentityRecord.fromEntry(entry);
            return Arrays.equals(record.ed25519Pub(), peerPub) ? record : null;
        } catch (Throwable t) {
            LOG.debug("Identity record fetch failed for " + com.frostwire.util.Hex.encode(peerPub), t);
            return null;
        }
    }
}
