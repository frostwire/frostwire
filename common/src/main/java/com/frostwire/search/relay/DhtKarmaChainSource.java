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
 * BEP 46 mutable DHT lookup for karma chain manifests.
 *
 * <p>Calls {@link SessionManager#dhtGetItem(byte[], byte[], int)}
 * with the peer's raw Ed25519 public key and the karma salt
 * ({@link KarmaConstants#BEP46_SALT_KARMA}). jlibtorrent verifies
 * the publisher signature on the returned item; we trust the
 * manifest to be authentic.
 */
public final class DhtKarmaChainSource implements KarmaChainSource {

    private static final Logger LOG = Logger.getLogger(DhtKarmaChainSource.class);

    /** 5 seconds — same as {@code HttpBlockHeaderFetcher}'s HTTP timeout. */
    static final int DEFAULT_DHT_TIMEOUT_MS = 5000;

    private final SessionManager session;
    private final int dhtTimeoutMs;

    public DhtKarmaChainSource(SessionManager session) {
        this(session, DEFAULT_DHT_TIMEOUT_MS);
    }

    public DhtKarmaChainSource(SessionManager session, int dhtTimeoutMs) {
        if (session == null) {
            throw new IllegalArgumentException("session is null");
        }
        if (dhtTimeoutMs <= 0) {
            throw new IllegalArgumentException("dhtTimeoutMs must be > 0");
        }
        this.session = session;
        this.dhtTimeoutMs = dhtTimeoutMs;
    }

    @Override
    public Entry fetchManifest(byte[] peerPub) {
        if (peerPub == null || peerPub.length != 32) {
            return null;
        }
        try {
            byte[] salt = KarmaConstants.BEP46_SALT_KARMA.getBytes(StandardCharsets.US_ASCII);
            SessionManager.MutableItem item = session.dhtGetItem(peerPub, salt, dhtTimeoutMs);
            if (item == null) {
                return null;
            }
            return item.item;
        } catch (Throwable t) {
            LOG.debug("DHT karma fetch failed for peer " +
                    com.frostwire.util.Hex.encode(peerPub), t);
            return null;
        }
    }
}
