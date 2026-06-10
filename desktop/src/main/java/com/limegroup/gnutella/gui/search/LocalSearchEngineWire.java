/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.relay.LocalIndex;

/**
 * Tiny installer that hands a {@link LocalIndex} to the desktop
 * {@code SearchEngine.LOCAL} so the user-facing "Local" search can
 * read from the local distributed-search index.
 *
 * <p>Mirrors {@code SharedTorrentIndexerInstaller} in spirit but for
 * the search-engine side of the relay stack.
 */
public final class LocalSearchEngineWire {
    private LocalSearchEngineWire() {
    }

    public static void setIndex(LocalIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        SearchEngine local = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.LOCAL_ID);
        if (local == null) {
            throw new IllegalStateException("LOCAL search engine is not registered");
        }
        local.setLocalIndex(index);
    }
}
