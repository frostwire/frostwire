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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.relay.DisplayKarma;
import com.frostwire.search.relay.DistributedSearchPerformer;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1418: distributed search results show their peer's karma in a Karma column.
 * The column reads {@link DistributedSearchEngineWire#karmaDisplay()}, which loads {@link
 * PeerDirectory#displayKarma} off the EDT; getValueAt never walks the trust graph itself.
 */
class SearchResultKarmaColumnTest {

  private static final String INFO_HASH = "0123456789abcdef0123456789abcdef01234567";

  private final RecordingKarmaCache karmaCache = new RecordingKarmaCache();
  private final PeerDirectory directory = new PeerDirectory(karmaCache);
  private final DistributedSearchTransport transport = proxy(DistributedSearchTransport.class);
  private final byte[] peer = new byte[32];

  @BeforeEach
  void wire() throws Exception {
    peer[31] = 9;
    directory.upsert(peer, "peer.example", 6881);
    karmaCache.score = 2;
    DistributedSearchEngineWire.wire(
        proxy(LocalIndex.class), directory, IdentityKeys.generate(0), transport);
  }

  @AfterEach
  void unwire() {
    DistributedSearchEngineWire.unwire(transport);
  }

  @Test
  void distributedRowsLoadTheirPeersKarmaOffTheEdt() throws Exception {
    SearchResultDataLine line = line(DistributedSearchPerformer.SOURCE_NAME, peer);
    AtomicReference<Object> firstPaint = new AtomicReference<>("unset");

    SwingUtilities.invokeAndWait(
        () -> firstPaint.set(line.getValueAt(SearchTableColumns.KARMA_IDX)));

    assertNull(firstPaint.get(), "the EDT must not wait for the karma");
    DisplayKarma karma = awaitKarma(line);
    assertEquals(DisplayKarma.of(directory.displayKarma(peer)), karma);
    assertEquals(DisplayKarma.of(3), karma); // 1 direct trust + 2 karma
    synchronized (karmaCache.onEdt) {
      assertFalse(karmaCache.onEdt.isEmpty());
      assertFalse(karmaCache.onEdt.contains(Boolean.TRUE), "karma read on the EDT");
    }
    assertNotNull(line.getToolTipArray(SearchTableColumns.KARMA_IDX));
    assertTrue(line.isTooltipRequired(SearchTableColumns.KARMA_IDX));
  }

  @Test
  void otherResultsShowNoKarma() {
    SearchResultDataLine line = line("1337x", peer);

    assertNull(line.getValueAt(SearchTableColumns.KARMA_IDX));
    assertNull(line.getToolTipArray(SearchTableColumns.KARMA_IDX));
    assertFalse(line.isTooltipRequired(SearchTableColumns.KARMA_IDX));
    assertTrue(karmaCache.onEdt.isEmpty());
  }

  @Test
  void theKarmaColumnIsRegistered() {
    ResultPanelModel model = new ResultPanelModel();
    assertEquals(SearchTableColumns.KARMA_IDX + 1, model.getColumnCount());
    assertEquals("Karma", model.getColumnName(SearchTableColumns.KARMA_IDX));
    assertEquals(DisplayKarma.class, model.getColumnClass(SearchTableColumns.KARMA_IDX));
  }

  @Test
  void aKarmaChangeRepaintsOnlyTheDistributedRows() {
    ResultPanelModel model = new ResultPanelModel();
    model.add(wrapper("1337x", peer, "a"));
    model.add(wrapper(DistributedSearchPerformer.SOURCE_NAME, peer, "b"));
    model.add(wrapper(DistributedSearchPerformer.SOURCE_NAME, peer, "c"));
    model.add(wrapper("1337x", peer, "d"));
    List<int[]> updated = new ArrayList<>();
    model.addTableModelListener(e -> updated.add(new int[] {e.getFirstRow(), e.getLastRow()}));

    model.karmaChanged();

    assertEquals(1, updated.size());
    assertEquals(1, updated.get(0)[0]);
    assertEquals(2, updated.get(0)[1]);
  }

  private static DisplayKarma awaitKarma(SearchResultDataLine line) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    Object value;
    while ((value = line.getValueAt(SearchTableColumns.KARMA_IDX)) == null
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertNotNull(value, "the karma never loaded");
    return (DisplayKarma) value;
  }

  private static SearchResultDataLine line(String source, byte[] peer) {
    SearchResultDataLine line = new SearchResultDataLine(new SearchTableColumns());
    line.initialize(wrapper(source, peer, "ubuntu"));
    return line;
  }

  private static FileSearchResultUIWrapper wrapper(String source, byte[] peer, String name) {
    String magnet = "magnet:?xt=urn:btih:" + INFO_HASH + "&dn=" + name;
    CompositeFileSearchResult result =
        CompositeFileSearchResult.builder()
            .displayName(name)
            .filename(name + ".torrent")
            .size(1024)
            .detailsUrl(magnet)
            .source(source)
            .creationTime(1_700_000_000L)
            .torrent(magnet, INFO_HASH, 0, magnet)
            .distributedSourcePeerPub(peer)
            .build();
    return new FileSearchResultUIWrapper(
        result, SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID), name);
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

  /** Records whether each karma read through the directory ran on the EDT. */
  private static final class RecordingKarmaCache extends PeerKarmaCache {
    final List<Boolean> onEdt = new ArrayList<>();
    volatile long score;

    RecordingKarmaCache() {
      super(new RemoteKarmaChainFetcher(peerPub -> null));
    }

    @Override
    public long getCachedKarma(byte[] peerPub) {
      synchronized (onEdt) {
        onEdt.add(SwingUtilities.isEventDispatchThread());
      }
      return score;
    }
  }
}
