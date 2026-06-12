/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.util.Logger;

import java.nio.charset.StandardCharsets;

/**
 * Publishes the node's {@link IdentityRecord} as a BEP 46
 * mutable DHT item so other peers can discover our full identity
 * (Ed25519 pub, X25519 pub, uTP port, node ID) by our Ed25519
 * pub key alone.
 *
 * <p>The BEP 46 target is derived from
 * {@code SHA1(ed25519_pub || BEP46_SALT)} — the same as any other
 * mutable item published by us. jlibtorrent verifies the
 * publisher signature on the value when peers fetch it; the
 * internal {@link IdentityRecord#verifySignature()} provides a
 * second layer of integrity over the canonical bytes.
 *
 * <p>Publication is throttled: {@link #publishIfNeeded} returns
 * immediately if the last publish was within
 * {@link RelayConstants#IDENTITY_REPUBLISH_INTERVAL_SEC}. The
 * uTP port and last_seen fields are refreshed on each actual
 * publish so a port change propagates within one interval.
 *
 * <p>Fail-closed: any error is logged and returns 0.
 */
public final class IdentityRecordPublisher {

    private static final Logger LOG = Logger.getLogger(IdentityRecordPublisher.class);

    private final IdentityKeys identity;
    private final int utpPort;
    private long lastPublishEpochSec;

    public IdentityRecordPublisher(IdentityKeys identity, int utpPort) {
        if (identity == null) {
            throw new IllegalArgumentException("identity is null");
        }
        if (utpPort < 0 || utpPort > 65535) {
            throw new IllegalArgumentException("utpPort out of range: " + utpPort);
        }
        this.identity = identity;
        this.utpPort = utpPort;
    }

    /**
     * Publish the identity to the DHT if the throttle window has
     * elapsed. Returns the number of items published (0 or 1).
     */
    public int publishIfNeeded(SessionManager session) {
        if (session == null) {
            return 0;
        }
        try {
            long now = System.currentTimeMillis() / 1000L;
            if (lastPublishEpochSec > 0
                    && (now - lastPublishEpochSec)
                    < RelayConstants.IDENTITY_REPUBLISH_INTERVAL_SEC) {
                return 0;
            }
            return publish(session);
        } catch (Throwable t) {
            LOG.warn("IdentityRecordPublisher failed", t);
            return 0;
        }
    }

    /**
     * Force a publish now, ignoring the throttle. Returns 1 if
     * published, 0 on failure.
     */
    public int publish(SessionManager session) {
        if (session == null) {
            return 0;
        }
        try {
            // Build a fresh record each time so last_seen is current
            // and any rotation in the Ed25519 secret still picks up
            // (the persisted identity stays stable on disk; we just
            // re-sign on every publish).
            IdentityRecord record = IdentityRecord.createSigned(
                    identity.nodeId(),
                    identity.ed25519(),
                    identity.x25519PubRaw(),
                    utpPort);
            Entry entry = record.toEntry();
            byte[] pubKey = identity.ed25519PubRaw();
            byte[] privKey = identity.ed25519SecretKeyNaCl();
            byte[] salt = IdentityRecord.BEP46_SALT
                    .getBytes(StandardCharsets.US_ASCII);
            session.dhtPutItem(pubKey, privKey, entry, salt);
            lastPublishEpochSec = System.currentTimeMillis() / 1000L;
            LOG.info("Published identity record (Ed25519 pub=" +
                    com.frostwire.util.Hex.encode(pubKey).substring(0, 12) +
                    "...)");
            return 1;
        } catch (Throwable t) {
            LOG.warn("IdentityRecordPublisher.publish failed", t);
            return 0;
        }
    }

    public int utpPort() {
        return utpPort;
    }

    public long lastPublishEpochSec() {
        return lastPublishEpochSec;
    }
}
