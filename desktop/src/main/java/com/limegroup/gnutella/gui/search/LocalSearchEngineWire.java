/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.PeerKarmaCache;

/**
 * Tiny installer that hands a {@link LocalIndex} and an optional
 * {@link PeerKarmaCache} to the desktop {@code SearchEngine.LOCAL}
 * so the user-facing "Local" search can read from the local
 * distributed-search index and weight results by karma.
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

    /**
     * Wire an optional karma cache so the LOCAL engine sorts results
     * by the publisher's karma score. Pass {@code null} to disable
     * karma weighting. Idempotent; the most recent call wins.
     */
    public static void setKarmaCache(PeerKarmaCache karmaCache) {
        SearchEngine local = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.LOCAL_ID);
        if (local == null) {
            throw new IllegalStateException("LOCAL search engine is not registered");
        }
        local.setKarmaCache(karmaCache);
    }
}
