/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.util.Hex;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1418: every karma indicator shows {@link PeerDirectory#displayKarma}, and
 * renderers read it from {@link KarmaDisplayCache}, which loads it off the calling (UI) thread.
 */
class KarmaDisplayCacheTest {

  private static final long REFRESH_MS = 1_000;

  private final RecordingKarmaCache karmaCache = new RecordingKarmaCache();
  private final PeerDirectory directory = new PeerDirectory(karmaCache);
  private final ManualExecutor loader = new ManualExecutor();
  private final ManualExecutor ui = new ManualExecutor();
  private final AtomicLong now = new AtomicLong(10_000);

  @Test
  void displayKarmaIsTheReceivedKarma() {
    byte[] known = pub(1);
    byte[] spammer = pub(2);
    directory.upsert(known, "known.example", 6881);
    directory.upsert(spammer, "spam.example", 6881);
    directory.markSpam(spammer);
    karmaCache.score = 3;

    // Received karma alone (#1417), not the trust score's 1 direct trust + 3.
    assertEquals(3.0, directory.displayKarma(known));
    assertEquals(4.0, directory.trustScore(known));
    assertEquals(-1.0, directory.displayKarma(spammer));
    // A peer outside the directory, such as this node itself, has received karma too.
    assertEquals(3.0, directory.displayKarma(pub(3)));
    assertEquals(0.0, directory.displayKarma(null));
  }

  @Test
  void displayKarmaTextAndOrder() {
    assertEquals(String.format(Locale.getDefault(), "%.2f", 1.5), DisplayKarma.of(1.5).text());
    assertEquals(DisplayKarma.of(1.5).text(), DisplayKarma.of(1.5).toString());
    assertEquals(DisplayKarma.of(0), DisplayKarma.of(Double.NaN));
    assertEquals(DisplayKarma.of(0), DisplayKarma.of(Double.POSITIVE_INFINITY));
    assertTrue(DisplayKarma.of(2).compareTo(DisplayKarma.of(10)) < 0);
    assertNotEquals(DisplayKarma.of(1), DisplayKarma.of(2));
    assertNull(DisplayKarma.forPeer(null, pub(1)));
    assertNull(DisplayKarma.forPeer(directory, null));
    assertEquals(DisplayKarma.of(0), DisplayKarma.forPeer(directory, pub(1)));
  }

  @Test
  void getNeverReadsTheDirectoryOnTheCallingThread() {
    byte[] peer = pub(1);
    directory.upsert(peer, "peer.example", 6881);
    karmaCache.score = 2;
    KarmaDisplayCache cache = newCache(directory);

    assertNull(cache.get(Hex.encode(peer)), "the first read must not wait for the load");
    assertTrue(karmaCache.threads.isEmpty(), "the directory was read on the calling thread");
    assertEquals(1, loader.size());

    Thread worker = loader.runAllOn("karma-loader");

    assertEquals(List.of(worker), karmaCache.threads);
    assertEquals(DisplayKarma.of(directory.displayKarma(peer)), cache.get(Hex.encode(peer)));
    assertEquals(2.0, cache.get(Hex.encode(peer)).value());
    assertEquals(0, loader.size(), "a fresh value is not loaded again");
  }

  @Test
  void aPeerIsQueuedOnceUntilItsLoadRuns() {
    KarmaDisplayCache cache = newCache(directory);
    String peer = Hex.encode(pub(1));

    for (int i = 0; i < 100; i++) {
      cache.get(peer); // a table refresh reads every row
    }

    assertEquals(1, loader.size());
  }

  @Test
  void listenersRunOnTheUiExecutorOncePerBatchOfChanges() {
    directory.upsert(pub(1), "a.example", 6881);
    directory.upsert(pub(2), "b.example", 6881);
    KarmaDisplayCache cache = newCache(directory);
    AtomicInteger notified = new AtomicInteger();
    cache.addListener(notified::incrementAndGet);

    cache.get(Hex.encode(pub(1)));
    cache.get(Hex.encode(pub(2)));
    loader.runAll();

    assertEquals(0, notified.get(), "listeners ran on the loader thread");
    assertEquals(1, ui.size(), "two changes, one repaint");
    ui.runAll();
    assertEquals(1, notified.get());
  }

  @Test
  void aStaleValueIsShownWhileItReloadsAndOnlyAChangeNotifies() {
    byte[] peer = pub(1);
    directory.upsert(peer, "peer.example", 6881);
    KarmaDisplayCache cache = newCache(directory);
    AtomicInteger notified = new AtomicInteger();
    cache.addListener(notified::incrementAndGet);
    cache.get(Hex.encode(peer));
    loader.runAll();
    ui.runAll();
    DisplayKarma first = cache.get(Hex.encode(peer));

    now.addAndGet(REFRESH_MS);
    assertEquals(first, cache.get(Hex.encode(peer)), "the old value shows until the reload ends");
    assertEquals(1, loader.size());
    loader.runAll();
    assertEquals(0, ui.size(), "an unchanged value must not repaint");

    karmaCache.score = 5;
    now.addAndGet(REFRESH_MS);
    cache.get(Hex.encode(peer));
    loader.runAll();
    ui.runAll();
    assertEquals(DisplayKarma.of(5), cache.get(Hex.encode(peer)));
    assertEquals(2, notified.get());
  }

  @Test
  void withoutADirectoryThereIsNoValueAndItIsRetriedLater() {
    AtomicReference<PeerDirectory> wired = new AtomicReference<>();
    KarmaDisplayCache cache = newCache(wired::get);
    String peer = Hex.encode(pub(1));
    directory.upsert(pub(1), "peer.example", 6881);

    cache.get(peer);
    loader.runAll();
    assertNull(cache.get(peer));
    assertEquals(0, loader.size(), "not retried on every paint");

    karmaCache.score = 1;
    wired.set(directory);
    now.addAndGet(REFRESH_MS);
    cache.get(peer);
    loader.runAll();
    assertEquals(DisplayKarma.of(1), cache.get(peer));
  }

  @Test
  void clearForgetsTheValuesOfAReplacedDirectory() {
    directory.upsert(pub(1), "peer.example", 6881);
    KarmaDisplayCache cache = newCache(directory);
    cache.get(Hex.encode(pub(1)));
    loader.runAll();

    cache.clear();

    assertNull(cache.get(Hex.encode(pub(1))));
    assertEquals(1, loader.size());
  }

  @Test
  void theOldestPeerIsDroppedPastTheBound() {
    KarmaDisplayCache cache =
        new KarmaDisplayCache(() -> directory, loader, ui, now::get, REFRESH_MS, 2);
    for (int i = 1; i <= 3; i++) {
      cache.get(Hex.encode(pub(i)));
      loader.runAll();
      now.incrementAndGet();
    }

    assertNull(cache.get(Hex.encode(pub(1))));
    assertEquals(DisplayKarma.of(0), cache.get(Hex.encode(pub(3))));
  }

  @Test
  void aRejectedLoadIsQueuedAgain() {
    AtomicInteger attempts = new AtomicInteger();
    Executor rejecting =
        r -> {
          attempts.incrementAndGet();
          throw new RejectedExecutionException("full");
        };
    KarmaDisplayCache cache =
        new KarmaDisplayCache(() -> directory, rejecting, ui, now::get, REFRESH_MS, 10);

    assertNull(cache.get(Hex.encode(pub(1))));
    assertNull(cache.get(Hex.encode(pub(1))));
    assertEquals(2, attempts.get());
  }

  @Test
  void sourcePeerHexIsOnlyForDistributedResults() {
    byte[] peer = pub(7);
    assertEquals(
        Hex.encode(peer),
        KarmaDisplayCache.sourcePeerHex(result(DistributedSearchPerformer.SOURCE_NAME, peer)));
    assertNull(
        KarmaDisplayCache.sourcePeerHex(result(DistributedSearchPerformer.SOURCE_NAME, null)));
    assertNull(KarmaDisplayCache.sourcePeerHex(result("1337x", peer)));
    assertNull(KarmaDisplayCache.sourcePeerHex(null));
  }

  @Test
  void constructorRejectsBadArguments() {
    assertThrows(IllegalArgumentException.class, () -> new KarmaDisplayCache(null, loader, ui));
    assertThrows(
        IllegalArgumentException.class,
        () -> new KarmaDisplayCache(() -> directory, loader, ui, now::get, 0, 1));
  }

  private KarmaDisplayCache newCache(PeerDirectory d) {
    return newCache(() -> d);
  }

  private KarmaDisplayCache newCache(java.util.function.Supplier<PeerDirectory> d) {
    return new KarmaDisplayCache(d, loader, ui, now::get, REFRESH_MS, 100);
  }

  private static CompositeFileSearchResult result(String source, byte[] peer) {
    return CompositeFileSearchResult.builder()
        .displayName("ubuntu")
        .filename("ubuntu.torrent")
        .size(1)
        .detailsUrl("magnet:?xt=urn:btih:" + "a".repeat(40))
        .source(source)
        .distributedSourcePeerPub(peer)
        .build();
  }

  private static byte[] pub(int n) {
    byte[] pub = new byte[32];
    pub[31] = (byte) n;
    return pub;
  }

  /** Records the threads that read karma through the directory. */
  private static final class RecordingKarmaCache extends PeerKarmaCache {
    final List<Thread> threads = new ArrayList<>();
    volatile long score;

    RecordingKarmaCache() {
      super(new RemoteKarmaChainFetcher(peerPub -> null));
    }

    @Override
    public double receivedKarma(byte[] peerPub) {
      synchronized (threads) {
        threads.add(Thread.currentThread());
      }
      return score;
    }
  }

  /** Queues tasks until the test runs them. */
  private static final class ManualExecutor implements Executor {
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    @Override
    public synchronized void execute(Runnable r) {
      tasks.add(r);
    }

    synchronized int size() {
      return tasks.size();
    }

    void runAll() {
      Runnable r;
      while ((r = poll()) != null) {
        r.run();
      }
    }

    /** Runs the queued tasks on a new thread, and returns it. */
    Thread runAllOn(String name) {
      Thread t = new Thread(this::runAll, name);
      t.start();
      try {
        t.join(10_000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return t;
    }

    private synchronized Runnable poll() {
      return tasks.poll();
    }
  }
}
