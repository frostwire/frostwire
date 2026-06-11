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
    public static BTEngineListener install(BTEngine engine, LocalIndex index) {
        return BTEngineListenerChain.install(engine, new SharedTorrentIndexer(index));
    }

    /** Install with a real node identity. */
    public static BTEngineListener install(BTEngine engine, LocalIndex index, IdentityKeys identity) {
        return BTEngineListenerChain.install(engine, new SharedTorrentIndexer(index, identity));
    }
}
