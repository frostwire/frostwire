/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Strategy for resolving Bitcoin block references by height.
 *
 * <p>Implementations may read from a local cache, HTTP API, or
 * the Bitcoin P2P network. The karma chain uses this abstraction
 * to anchor epoch commitments to real Bitcoin blocks, so the
 * source must return real, verifiable data.
 */
public interface BlockHeaderSource {

    /**
     * Returns the block reference at the given height, or null if
     * the height is unknown or the source is unavailable.
     */
    BitcoinBlockReference getBlock(long height);

    /**
     * Returns the current chain tip height, or -1 if unknown.
     */
    long getChainTipHeight();
}
