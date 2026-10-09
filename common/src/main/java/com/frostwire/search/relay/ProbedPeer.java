/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.Arrays;
import java.util.Optional;

/**
 * A peer that proved possession of its Ed25519 key during the rUDP handshake with an endpoint.
 * Nothing else is guaranteed to be known: its role and capabilities come from its signed {@link
 * IdentityRecord} (published on the DHT, see {@link IdentityRecordEnrichingProber}) or, later, from
 * its NODE_META message.
 */
public final class ProbedPeer {
    private final byte[] pub;
    private final IdentityRecord record;

    public ProbedPeer(byte[] pub) {
        this(pub, null);
    }

    /** @param record the peer's signed identity record, or null; its key must be {@code pub} */
    public ProbedPeer(byte[] pub, IdentityRecord record) {
        if (pub == null || pub.length != 32) {
            throw new IllegalArgumentException("pub must be 32 bytes");
        }
        if (record != null && !Arrays.equals(record.ed25519Pub(), pub)) {
            throw new IllegalArgumentException("record belongs to another key");
        }
        this.pub = pub.clone();
        this.record = record;
    }

    /** The authenticated 32-byte Ed25519 public key. */
    public byte[] pub() {
        return pub.clone();
    }

    /** The peer's role/capabilities/version, when its identity record could be fetched. */
    public Optional<IdentityRecord> record() {
        return Optional.ofNullable(record);
    }
}
