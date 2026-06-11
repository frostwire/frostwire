/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Outcome of a single {@link SharedTorrentIndexer#indexIfReady} pass.
 *
 * <p>Returned to the caller so the future
 * {@code DistributedSearchEventBus} can route
 * {@code LOCAL_INDEX_UPDATED} events without parsing logs.
 */
public enum IndexResult {
    /** The torrent row was inserted or replaced in the local index. */
    UPSERTED,

    /** The torrent metadata was not yet available (magnet still resolving). */
    NO_METADATA,

    /** One or more required parameters were null; skipped. */
    NULL_INPUT,

    /** An unexpected exception prevented indexing. */
    ERROR
}
