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
 * other threads.
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

    /**
     * Load the chain for the {@link KarmaChainWriter} that will append to it. Like
     * {@link #loadChain}, but a persisted chain that fails verification is first moved aside,
     * because the fresh chain's appends would otherwise overwrite it. Only the writer calls this;
     * readers use {@link #loadChain}, which never changes the store.
     *
     * @throws IllegalStateException if a rejected chain can't be moved aside
     */
    default KarmaChain loadChainForWriting(byte[] ownerPub) {
        return loadChain(ownerPub);
    }
}
