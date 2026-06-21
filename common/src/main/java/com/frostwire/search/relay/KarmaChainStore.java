/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.List;

/**
 * Storage boundary for the local karma chain.
 *
 * <p>Desktop uses a JDBC SQLite implementation ({@code KarmaChainTable}).
 * Android uses an {@code android.database.sqlite} implementation.
 * Tests may use an in-memory implementation.
 *
 * <p>Implementations MUST be thread-safe. The {@link KarmaChainWriter}
 * calls {@link #append} and {@link #loadChain} under a
 * {@code ReentrantLock}, but the store itself may be accessed from
 * other threads (e.g. {@code getTopPeers} from the UI).
 */
public interface KarmaChainStore extends AutoCloseable {

    /**
     * Append a signed karma chain entry to this node's chain.
     *
     * @param entry the entry to append; must be pre-verified by the caller
     */
    void append(KarmaChainEntry entry);

    /**
     * Load the full chain for the given owner public key.
     *
     * @param ownerPub the 32-byte Ed25519 public key of the chain owner
     * @return the verified chain, or an empty chain if none exists
     */
    KarmaChain loadChain(byte[] ownerPub);
}
