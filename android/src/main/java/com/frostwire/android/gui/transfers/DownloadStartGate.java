/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.transfers;

import java.util.concurrent.atomic.AtomicInteger;

/** Linearizes cancellation against committing a fetched torrent to the engine. */
final class DownloadStartGate {
    private final AtomicInteger state = new AtomicInteger();

    boolean tryStart() {
        return state.compareAndSet(0, 1);
    }

    boolean cancel() {
        return state.compareAndSet(0, 2);
    }
}
