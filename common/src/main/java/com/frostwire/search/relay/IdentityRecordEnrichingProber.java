/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Decorates a {@link PeerProber} with the role, capabilities and version of the peer it found.
 * The rUDP handshake proves who answers but says nothing about what the node is; its signed
 * {@link IdentityRecord} on the DHT (BEP 46) does, so a dedicated forwarder is recognized as one
 * the moment it is discovered instead of being treated as a leaf until its next NODE_META.
 *
 * <p>The record is best effort and never gates discovery: a peer whose record cannot be fetched is
 * still returned, and a record is only looked up for peers {@code needsRecord} says are new.
 */
public final class IdentityRecordEnrichingProber implements PeerProber {
    private final PeerProber delegate;
    private final PeerDiscoverySource records;
    private final Predicate<byte[]> needsRecord;

    public IdentityRecordEnrichingProber(PeerProber delegate, PeerDiscoverySource records,
                                         Predicate<byte[]> needsRecord) {
        if (delegate == null || records == null || needsRecord == null) {
            throw new IllegalArgumentException("delegate, records and needsRecord are required");
        }
        this.delegate = delegate;
        this.records = records;
        this.needsRecord = needsRecord;
    }

    @Override
    public Optional<ProbedPeer> probe(String host, int port, long deadlineNanos) {
        Optional<ProbedPeer> peer = delegate.probe(host, port, deadlineNanos);
        if (peer.isEmpty() || peer.get().record().isPresent() || System.nanoTime() - deadlineNanos >= 0) {
            return peer;
        }
        byte[] pub = peer.get().pub();
        if (!needsRecord.test(pub)) {
            return peer;
        }
        IdentityRecord record = IdentityRecordLookup.fetch(records, pub);
        return record == null ? peer : Optional.of(new ProbedPeer(pub, record));
    }
}
