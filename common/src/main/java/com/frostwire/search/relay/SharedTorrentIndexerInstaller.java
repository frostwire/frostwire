/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;

/**
 * Tiny installer that wires {@link SharedTorrentIndexer} into a
 * {@link BTEngine} without disturbing any existing listener.
 */
public final class SharedTorrentIndexerInstaller {
    private SharedTorrentIndexerInstaller() {
    }

    /** Install with placeholder identity (for tests or headless nodes). */
    public static SharedTorrentIndexer install(BTEngine engine, LocalIndex index) {
        SharedTorrentIndexer indexer = new SharedTorrentIndexer(index);
        BTEngineListenerChain.install(engine, indexer);
        return indexer;
    }

    /** Install with a real node identity. */
    public static SharedTorrentIndexer install(BTEngine engine, LocalIndex index, IdentityKeys identity) {
        SharedTorrentIndexer indexer = new SharedTorrentIndexer(index, identity);
        BTEngineListenerChain.install(engine, indexer);
        return indexer;
    }
}
