/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class RelaySearchWiringTest {

    @Test
    public void defaultsAreNull() {
        RelaySearchWiring w = new RelaySearchWiring();
        assertNull(w.localIndex());
        assertNull(w.karmaCache());
        assertNull(w.peerDirectory());
        assertNull(w.identity());
        assertNull(w.searchTransport());
    }

    @Test
    public void fluentSettersReturnSameInstance() {
        RelaySearchWiring w = new RelaySearchWiring();
        RelaySearchWiring returned = w.localIndex(null)
                .karmaCache(null)
                .peerDirectory(null)
                .identity(null)
                .searchTransport(null);
        assertEquals(w, returned);
    }

    @Test
    public void distributedEngine_notReady_beforeWiring() {
        assertFalse("DISTRIBUTED should not be ready before wiring",
                SearchEngine.DISTRIBUTED.isReady());
    }

    @Test
    public void distributedEngine_getPerformerReturnsNull_whenNotReady() {
        assertNull("getPerformer should return null when not ready",
                SearchEngine.DISTRIBUTED.getPerformer(1L, "test"));
    }

    @Test
    public void distributedEngine_notReady_withOnlyLocalIndex() {
        LocalIndex stubIndex = new StubLocalIndex();
        java.util.Optional<LocalIndex> savedLi = java.util.Optional.ofNullable(
                SearchEngine.DISTRIBUTED_WIRING.localIndex());
        try {
            SearchEngine.DISTRIBUTED_WIRING.localIndex(stubIndex);
            assertFalse("DISTRIBUTED should not be ready with only localIndex",
                    SearchEngine.DISTRIBUTED.isReady());
        } finally {
            SearchEngine.DISTRIBUTED_WIRING.localIndex(savedLi.orElse(null));
        }
    }

    private static final class StubLocalIndex implements LocalIndex {
        @Override
        public void upsert(LocalSharedTorrent torrent) {}

        @Override
        public void delete(String infoHashHex) {}

        @Override
        public Optional<LocalSharedTorrent> get(String infoHashHex) {
            return Optional.empty();
        }

        @Override
        public List<LocalSharedTorrent> search(String query, int limit) {
            return Collections.emptyList();
        }

        @Override
        public void markPublished(String infoHashHex, long timestamp) {}

        @Override
        public List<String> needsRepublish(long nowSec, long thresholdSec) {
            return Collections.emptyList();
        }

        @Override
        public void updateLastSeen(String infoHashHex, long ts) {}

        @Override
        public int size() {
            return 0;
        }
    }
}
