/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests.relay;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.KarmaChainSource;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.search.relay.OutgoingRelayClient;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.limegroup.gnutella.gui.search.DistributedSearchEngineWire;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DistributedSearchEngineWireTest {

    @Test
    void distributedEngineIsRegistered() {
        SearchEngine engine = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);
        assertNotNull(engine);
        assertEquals("Distributed", engine.getName());
    }

    @Test
    void getEnginesIncludesDistributed() {
        boolean found = false;
        for (SearchEngine engine : SearchEngine.getEngines()) {
            if (engine.getId() == SearchEngine.SearchEngineID.DISTRIBUTED_ID) {
                found = true;
                break;
            }
        }
        assertTrue(found, "DISTRIBUTED engine is in the engine list");
    }

    @Test
    void distributedEngineIsNotReadyBeforeWiring() {
        SearchEngine engine = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);
        assertFalse(engine.isReady(), "DISTRIBUTED engine is not ready before wiring");
    }

    @Test
    void wireFlipsReadyState() throws Exception {
        SearchEngine engine = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);

        LocalIndex index = new InMemoryLocalIndex();
        PeerDirectory directory = new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(new NoOpKarmaSource())));
        IdentityKeys identity = IdentityKeys.generate();
        OutgoingRelayClient client = new OutgoingRelayClient();

        DistributedSearchEngineWire.wire(index, directory, identity, client);

        assertTrue(engine.isReady(), "DISTRIBUTED engine is ready after wiring");

        // Reset to avoid leaking state into other tests.
        engine.setLocalIndex(null).setPeerDirectory(null).setIdentityKeys(null).setOutgoingRelayClient(null);
        assertFalse(engine.isReady());
    }

    @Test
    void wireRejectsNullArguments() throws Exception {
        LocalIndex index = new InMemoryLocalIndex();
        PeerDirectory directory = new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(new NoOpKarmaSource())));
        IdentityKeys identity = IdentityKeys.generate();
        OutgoingRelayClient client = new OutgoingRelayClient();

        assertThrows(IllegalArgumentException.class,
                () -> DistributedSearchEngineWire.wire(null, directory, identity, client));
        assertThrows(IllegalArgumentException.class,
                () -> DistributedSearchEngineWire.wire(index, null, identity, client));
        assertThrows(IllegalArgumentException.class,
                () -> DistributedSearchEngineWire.wire(index, directory, null, client));
        assertThrows(IllegalArgumentException.class,
                () -> DistributedSearchEngineWire.wire(index, directory, identity, null));
    }

    private static final class InMemoryLocalIndex implements LocalIndex {
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
        public List<LocalSharedTorrent> search(String query, int limit) {
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

    private static final class NoOpKarmaSource implements KarmaChainSource {
        @Override
        public com.frostwire.jlibtorrent.Entry fetchManifest(byte[] peerPub) {
            return null;
        }
    }
}
