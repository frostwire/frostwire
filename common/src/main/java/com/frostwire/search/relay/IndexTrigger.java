/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Reason why a {@link SharedTorrentIndexer} pass ran. Used in logs
 * and (eventually) in the distributed-search event bus to label
 * {@code LOCAL_INDEX_UPDATED} events.
 */
public enum IndexTrigger {
    ADDED,
    UPDATE
}
