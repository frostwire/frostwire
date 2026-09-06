/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.KarmaChainSource;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.limegroup.gnutella.gui.search.DistributedSearchEngineWire;
import com.limegroup.gnutella.gui.search.SearchEngine;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DistributedSearchEngineWireTest {

  @Test
  void distributedEngineIsRegistered() {
    SearchEngine engine =
        SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);
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
    SearchEngine engine =
        SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);
    assertFalse(engine.isReady(), "DISTRIBUTED engine is not ready before wiring");
  }

  @Test
  void wireFlipsReadyState() throws Exception {
    SearchEngine engine =
        SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);

    LocalIndex index = new InMemoryLocalIndex();
    IdentityKeys identity = IdentityKeys.generate(0);
    DistributedSearchTransport transport = new NoopTransport();
    try (PeerKarmaCache cache =
        new PeerKarmaCache(new RemoteKarmaChainFetcher(new NoOpKarmaSource()))) {
      DistributedSearchEngineWire.wire(index, new PeerDirectory(cache), identity, transport);
      assertTrue(engine.isReady(), "DISTRIBUTED engine is ready after wiring");
    } finally {
      DistributedSearchEngineWire.unwire(transport);
    }
    assertFalse(engine.isReady());
    assertNull(DistributedSearchEngineWire.getPeerDirectory());
  }

  @Test
  void wireRejectsNullArguments() throws Exception {
    LocalIndex index = new InMemoryLocalIndex();
    IdentityKeys identity = IdentityKeys.generate(0);
    DistributedSearchTransport transport = new NoopTransport();

    try (PeerKarmaCache cache =
        new PeerKarmaCache(new RemoteKarmaChainFetcher(new NoOpKarmaSource()))) {
      PeerDirectory directory = new PeerDirectory(cache);
      assertThrows(
          IllegalArgumentException.class,
          () -> DistributedSearchEngineWire.wire(null, directory, identity, transport));
      assertThrows(
          IllegalArgumentException.class,
          () -> DistributedSearchEngineWire.wire(index, null, identity, transport));
      assertThrows(
          IllegalArgumentException.class,
          () -> DistributedSearchEngineWire.wire(index, directory, null, transport));
      assertThrows(
          IllegalArgumentException.class,
          () -> DistributedSearchEngineWire.wire(index, directory, identity, null));
    }
  }

  @Test
  void stoppingAnOldStackDoesNotUnwireItsReplacement() throws Exception {
    LocalIndex index = new InMemoryLocalIndex();
    IdentityKeys identity = IdentityKeys.generate(0);
    DistributedSearchTransport oldTransport = new NoopTransport();
    DistributedSearchTransport replacement = new NoopTransport();
    SearchEngine engine =
        SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);
    try (PeerKarmaCache cache =
        new PeerKarmaCache(new RemoteKarmaChainFetcher(new NoOpKarmaSource()))) {
      PeerDirectory oldDirectory = new PeerDirectory(cache);
      PeerDirectory newDirectory = new PeerDirectory(cache);
      DistributedSearchEngineWire.wire(index, oldDirectory, identity, oldTransport);
      DistributedSearchEngineWire.wire(index, newDirectory, identity, replacement);
      DistributedSearchEngineWire.unwire(oldTransport);
      DistributedSearchEngineWire.unwire(null);
      assertTrue(engine.isReady());
      assertSame(replacement, engine.getSearchTransport());
      assertSame(newDirectory, DistributedSearchEngineWire.getPeerDirectory());
      DistributedSearchEngineWire.unwire(replacement);
      DistributedSearchEngineWire.unwire(replacement);
      assertFalse(engine.isReady());
      assertNull(engine.identityKeys());
      assertNull(DistributedSearchEngineWire.getPeerDirectory());
    } finally {
      DistributedSearchEngineWire.unwire(replacement);
      DistributedSearchEngineWire.unwire(oldTransport);
    }
  }

  private static final class InMemoryLocalIndex implements LocalIndex {
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

  private static final class NoOpKarmaSource implements KarmaChainSource {
    @Override
    public com.frostwire.jlibtorrent.Entry fetchManifest(byte[] peerPub) {
      return null;
    }
  }

  /** Minimal no-op transport for wiring tests. */
  private static final class NoopTransport implements DistributedSearchTransport {
    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      return false;
    }

    @Override
    public void addListener(PayloadListener listener) {}

    @Override
    public void removeListener(PayloadListener listener) {}
  }
}
