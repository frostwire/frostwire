/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.relay.DistributedSearchPerformer;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.limegroup.gnutella.gui.tables.AbstractTableMediator;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Distributed search results have no seed count, so under the default Seeds ranking they all tied
 * at 0 and kept their arrival order. The Seeds sort now breaks ties by the karma of the peer behind
 * each result, without ever ranking a result above one with more seeds (frostwire/frostwire#1418).
 */
class SearchResultKarmaRankingTest {

  private final ScoredKarmaCache karmaCache = new ScoredKarmaCache();
  private final PeerDirectory directory = new PeerDirectory(karmaCache);
  private final DistributedSearchTransport transport = proxy(DistributedSearchTransport.class);
  private final byte[] lowPeer = peer(1);
  private final byte[] highPeer = peer(2);
  private final byte[] spamPeer = peer(3);

  @BeforeEach
  void wire() throws Exception {
    directory.upsert(lowPeer, "low.example", 6881);
    directory.upsert(highPeer, "high.example", 6881);
    directory.upsert(spamPeer, "spam.example", 6881);
    directory.markSpam(spamPeer);
    karmaCache.scores.put(lowPeer[31], 1L);
    karmaCache.scores.put(highPeer[31], 7L);
    DistributedSearchEngineWire.wire(
        proxy(LocalIndex.class), directory, IdentityKeys.generate(0), transport);
  }

  @AfterEach
  void unwire() {
    DistributedSearchEngineWire.unwire(transport);
  }

  @Test
  void theSeedsRankingOrdersDistributedResultsByTheirPeersKarma() throws Exception {
    ResultPanelModel model = new ResultPanelModel();
    model.add(distributed("from-low-karma", lowPeer));
    model.add(distributed("from-spammer", spamPeer));
    model.add(distributed("from-high-karma", highPeer));
    model.add(torrent("seedless-web-result", 0));
    model.add(torrent("seeded-web-result", 3));
    awaitKarmaOf(model);

    assertEquals(
        List.of(
            "seeded-web-result",
            "from-high-karma",
            "from-low-karma",
            "seedless-web-result",
            "from-spammer"),
        rankedBySeeds(model));
  }

  @Test
  void karmaNeverOutranksMoreSeeds() {
    ResultPanelModel.SeedsThenKarma oneSeed = new ResultPanelModel.SeedsThenKarma(1, 0);
    ResultPanelModel.SeedsThenKarma hugeKarma = new ResultPanelModel.SeedsThenKarma(0, 1e9);
    assertTrue(AbstractTableMediator.compare(oneSeed, hugeKarma) > 0);
  }

  @Test
  void sortingBySeedsIsRankingByKarma() {
    ResultPanelModel model = new ResultPanelModel();
    assertFalse(model.ranksByKarma());
    model.sort(SearchTableColumns.COUNT_IDX);
    assertTrue(model.ranksByKarma());
    model.sort(SearchTableColumns.NAME_IDX);
    assertFalse(model.ranksByKarma());
  }

  /** Names in the order the default ranking (Seeds, descending) shows them. */
  private static List<String> rankedBySeeds(ResultPanelModel model) {
    List<SearchResultDataLine> lines = new ArrayList<>();
    for (int i = 0; i < model.getRowCount(); i++) {
      lines.add(model.get(i));
    }
    Comparator<SearchResultDataLine> bySeeds =
        (a, b) ->
            AbstractTableMediator.compare(
                model.sortKey(a, SearchTableColumns.COUNT_IDX),
                model.sortKey(b, SearchTableColumns.COUNT_IDX));
    lines.sort(bySeeds.reversed());
    List<String> names = new ArrayList<>();
    for (SearchResultDataLine line : lines) {
      names.add(line.getDisplayName());
    }
    return names;
  }

  private static void awaitKarmaOf(ResultPanelModel model) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    for (int i = 0; i < model.getRowCount(); i++) {
      SearchResultDataLine line = model.get(i);
      if (!line.hasKarma()) {
        continue;
      }
      while (line.getKarma() == null && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
      assertNotNull(line.getKarma(), "the karma never loaded");
    }
  }

  private static byte[] peer(int id) {
    byte[] pub = new byte[32];
    pub[31] = (byte) id;
    return pub;
  }

  private static FileSearchResultUIWrapper distributed(String name, byte[] peer) {
    return wrapper(name, DistributedSearchPerformer.SOURCE_NAME, 0, peer);
  }

  private static FileSearchResultUIWrapper torrent(String name, int seeds) {
    return wrapper(name, "1337x", seeds, null);
  }

  private static FileSearchResultUIWrapper wrapper(
      String name, String source, int seeds, byte[] peer) {
    String infoHash = String.format("%040x", Math.abs(name.hashCode()));
    String magnet = "magnet:?xt=urn:btih:" + infoHash + "&dn=" + name;
    CompositeFileSearchResult.Builder builder =
        CompositeFileSearchResult.builder()
            .displayName(name)
            .filename(name + ".torrent")
            .size(1024)
            .detailsUrl(magnet)
            .source(source)
            .creationTime(1_700_000_000L)
            .torrent(magnet, infoHash, seeds, magnet);
    if (peer != null) {
      builder.distributedSourcePeerPub(peer);
    }
    return new FileSearchResultUIWrapper(
        builder.build(),
        SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID),
        name);
  }

  private static <T> T proxy(Class<T> type) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (p, method, args) -> {
              switch (method.getName()) {
                case "equals":
                  return p == args[0];
                case "hashCode":
                  return System.identityHashCode(p);
                default:
                  Class<?> r = method.getReturnType();
                  return r == boolean.class ? Boolean.FALSE : r == int.class ? 0 : null;
              }
            }));
  }

  /** A karma score per peer, keyed by the last byte of its key. */
  private static final class ScoredKarmaCache extends PeerKarmaCache {
    final Map<Byte, Long> scores = new ConcurrentHashMap<>();

    ScoredKarmaCache() {
      super(new RemoteKarmaChainFetcher(peerPub -> null));
    }

    @Override
    public long getCachedKarma(byte[] peerPub) {
      return scores.getOrDefault(peerPub[31], 0L);
    }
  }
}
