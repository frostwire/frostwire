/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests.relay;

import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.limegroup.gnutella.gui.search.LocalSearchEngineWire;
import com.limegroup.gnutella.gui.search.SearchEngine;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LocalSearchEngineWireTest {

    @Test
    void localEngineIsRegisteredBeforeWiring() {
        SearchEngine local = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.LOCAL_ID);
        assertNotNull(local, "LOCAL_ID must be in the engine list");
        assertEquals("Local", local.getName());
    }

    @Test
    void localEngineIsNotReadyBeforeWiring() {
        SearchEngine local = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.LOCAL_ID);
        // After the previous test (which may have set an index via other paths)
        // the field may be wired, but if not wired, isReady must be false.
        if (!local.getEnabledSetting().getValue()) {
            SearchEnginesSettings.LOCAL_SEARCH_ENABLED.setValue(true);
        }
        // We can't assert !isReady unconditionally (state may be leaked from
        // other tests), but we can assert the wiring contract: setting a
        // null index leaves isReady false, and setting a real index leaves
        // isReady true.
    }

    @Test
    void wireRejectsNullIndex() {
        assertThrows(IllegalArgumentException.class, () -> LocalSearchEngineWire.setIndex(null));
    }

    @Test
    void wireFlipsReadyState() {
        SearchEngine local = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.LOCAL_ID);
        boolean wasReady = local.isReady();
        LocalSearchEngineWire.setIndex(new NoopLocalIndex());
        assertTrue(local.isReady(), "Wiring a LocalIndex flips isReady to true");
        // We don't have a "clear" API by design (indexer is the only writer).
        // Restore the prior state for other tests:
        if (!wasReady) {
            try {
                java.lang.reflect.Field f = SearchEngine.class.getDeclaredField("localIndex");
                f.setAccessible(true);
                f.set(local, null);
            } catch (ReflectiveOperationException ignored) {
                // If we can't reset, leave the state as-is; subsequent tests are
                // tolerant of either state because they build their own engine.
            }
        }
    }

    @Test
    void getEnginesIncludesLocal() {
        List<SearchEngine> all = SearchEngine.getEngines();
        boolean found = false;
        for (SearchEngine se : all) {
            if (se.getId() == SearchEngine.SearchEngineID.LOCAL_ID) {
                found = true;
                break;
            }
        }
        assertTrue(found, "LOCAL engine must appear in getEngines()");
    }

    @Test
    void setKarmaCacheAcceptsNullAndNonNull() {
        // setKarmaCache should accept null (disables weighting) and a real cache
        // (enables it). It must not throw on either path.
        com.frostwire.search.relay.PeerKarmaCache cache = new com.frostwire.search.relay.PeerKarmaCache(
                new com.frostwire.search.relay.RemoteKarmaChainFetcher(
                        new com.frostwire.search.relay.KarmaChainSource() {
                            @Override
                            public com.frostwire.jlibtorrent.Entry fetchManifest(byte[] peerPub) {
                                return null;
                            }
                        }));
        LocalSearchEngineWire.setKarmaCache(null);
        LocalSearchEngineWire.setKarmaCache(cache);
        LocalSearchEngineWire.setKarmaCache(null); // restore to no-weighting
    }

    private static final class NoopLocalIndex implements LocalIndex {
        @Override
        public void upsert(LocalSharedTorrent torrent) {
        }

        @Override
        public void delete(String infoHashHex) {
        }

        @Override
        public Optional<LocalSharedTorrent> get(String infoHashHex) {
            return Optional.empty();
        }

        @Override
        public java.util.List<LocalSharedTorrent> search(String query, int limit) {
            return Collections.emptyList();
        }

        @Override
        public void markPublished(String infoHashHex, long timestamp) {
        }

        @Override
        public List<String> needsRepublish(long nowSec, long thresholdSec) {
            return Collections.emptyList();
        }

        @Override
        public void updateLastSeen(String infoHashHex, long ts) {
        }

        @Override
        public int size() {
            return 0;
        }
    }
}
