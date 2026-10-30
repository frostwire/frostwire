/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineAdapter;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Hex;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A download from a distributed-search peer endorses that peer when it completes, once, and no one
 * else is endorsed automatically (frostwire/frostwire#1414).
 */
class KarmaEndorsementTriggerCompletionTest {

  private static final byte[] OWN = key(1);
  private static final byte[] PREVIOUS_OWN = key(2);
  private static final byte[] SOURCE = key(3);
  private static final byte[] PROBE_SOURCE = key(4);
  private static final String HASH = hash(10);
  private static final String PROBE_HASH = hash(99);

  private final List<KarmaEndorsementTrigger> triggers = new ArrayList<>();

  @AfterEach
  void closeTriggers() throws InterruptedException {
    for (KarmaEndorsementTrigger trigger : triggers) {
      trigger.close();
      trigger.awaitStopped(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void completedDistributedDownloadEndorsesItsSourceOnce() throws Exception {
    RecordingSink sink = new RecordingSink();
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);

    for (int i = 0; i < 5; i++) {
      trigger.offer(new FakeTransfer(TransferState.SEEDING, true, SOURCE, HASH));
    }
    awaitProbe(trigger, sink);

    assertEquals(
        List.of(SOURCE.length + ":" + Hex.encode(SOURCE) + "/" + HASH), sink.without(PROBE_SOURCE));
  }

  @Test
  void finishedStateEndorsesEvenBeforeTheCachedStatusSaysFinished() throws Exception {
    RecordingSink sink = new RecordingSink();
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);

    trigger.offer(new FakeTransfer(TransferState.FINISHED, false, SOURCE, HASH));
    awaitProbe(trigger, sink);

    assertEquals(1, sink.without(PROBE_SOURCE).size());
  }

  @Test
  void unfinishedDistributedDownloadEndorsesNoOne() throws Exception {
    RecordingSink sink = new RecordingSink();
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);

    trigger.offer(new FakeTransfer(TransferState.DOWNLOADING, false, SOURCE, HASH));
    awaitProbe(trigger, sink);

    assertEquals(List.of(), sink.without(PROBE_SOURCE));
  }

  @Test
  void completedDownloadWithoutADistributedSourceEndorsesNoOne() throws Exception {
    RecordingSink sink = new RecordingSink();
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);

    trigger.offer(new FakeTransfer(TransferState.SEEDING, true, null, HASH));
    awaitProbe(trigger, sink);

    assertEquals(List.of(), sink.without(PROBE_SOURCE));
  }

  /**
   * The local index describes torrents this node shares, not where a download came from: its
   * publisher key is never a remote source to endorse. Rejected (it never named a real peer).
   */
  @Test
  void localIndexPublisherOfALocalDownloadIsNotEndorsed() throws Exception {
    RecordingSink sink = new RecordingSink();
    KarmaEndorsementTrigger trigger = trigger(new SingleTorrentIndex(HASH, key(7)), sink);

    trigger.offer(new FakeTransfer(TransferState.SEEDING, true, null, HASH));
    awaitProbe(trigger, sink);

    assertEquals(List.of(), sink.without(PROBE_SOURCE));
  }

  @Test
  void ownKeyIsNeverEndorsed() throws Exception {
    RecordingSink sink = new RecordingSink();
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);

    trigger.offer(new FakeTransfer(TransferState.SEEDING, true, OWN, HASH));
    awaitProbe(trigger, sink);

    assertEquals(List.of(), sink.without(PROBE_SOURCE));
  }

  @Test
  void previousIdentityKeyIsNeverEndorsed() throws Exception {
    RecordingSink sink = new RecordingSink();
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);

    trigger.offer(new FakeTransfer(TransferState.SEEDING, true, PREVIOUS_OWN, HASH));
    awaitProbe(trigger, sink);

    assertEquals(List.of(), sink.without(PROBE_SOURCE));
  }

  @Test
  void zeroKeyIsNeverEndorsed() throws Exception {
    RecordingSink sink = new RecordingSink();
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);

    trigger.offer(new FakeTransfer(TransferState.SEEDING, true, new byte[32], HASH));
    awaitProbe(trigger, sink);

    assertEquals(List.of(), sink.without(PROBE_SOURCE));
  }

  @Test
  void failedAppendIsRetriedOnTheNextCompletionEvent() throws Exception {
    AtomicInteger tipCalls = new AtomicInteger();
    BlockHeaderSource blocks =
        new BlockHeaderSource() {
          @Override
          public long getChainTipHeight() {
            // Bitcoin unreachable on the first attempt.
            return tipCalls.incrementAndGet() == 1 ? -1 : 144L * 5;
          }

          @Override
          public BitcoinBlockReference getBlock(long height) {
            return new BitcoinBlockReference(height, new byte[32]);
          }
        };
    IdentityKeys identity = IdentityKeys.generate(4);
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, new MemoryStore());
    KarmaEndorsementTrigger trigger =
        track(new KarmaEndorsementTrigger(List.of(identity.ed25519PubRaw()), writer, () -> true));
    byte[] infoHash = Hex.decode(HASH);

    trigger.offer(new FakeTransfer(TransferState.SEEDING, true, SOURCE, HASH));
    awaitTrue(() -> tipCalls.get() >= 1);
    // The next completion event (a later alert, or the transfer restored at startup) retries.
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!writer.chain().hasEndorsement(SOURCE, infoHash) && System.nanoTime() < deadline) {
      trigger.offer(new FakeTransfer(TransferState.SEEDING, true, SOURCE, HASH));
      Thread.sleep(50);
    }

    assertTrue(writer.chain().hasEndorsement(SOURCE, infoHash));
    assertEquals(2, writer.chain().entries().size(), "one commitment and one endorsement");
  }

  @Test
  void aBurstOfUpdatesForManyTransfersIsCoalescedNotDropped() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    RecordingSink sink = new RecordingSink(release);
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);
    int transfers = 20;

    for (int round = 0; round < 3; round++) {
      for (int i = 0; i < transfers; i++) {
        trigger.offer(new FakeTransfer(TransferState.SEEDING, true, SOURCE, hash(i)));
      }
    }
    release.countDown();
    awaitTrue(() -> sink.calls.size() >= transfers);
    awaitProbe(trigger, sink);

    Set<String> endorsed = ConcurrentHashMap.newKeySet();
    endorsed.addAll(sink.without(PROBE_SOURCE));
    assertEquals(transfers, endorsed.size(), "every transfer endorsed");
    assertEquals(transfers, sink.without(PROBE_SOURCE).size(), "each exactly once");
  }

  @Test
  void offeringNeverWaitsForASlowEndorsement() {
    CountDownLatch release = new CountDownLatch(1);
    RecordingSink sink = new RecordingSink(release);
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);
    try {
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () -> {
            for (int i = 0; i < 1_000; i++) {
              trigger.offer(new FakeTransfer(TransferState.SEEDING, true, SOURCE, hash(i % 50)));
            }
          });
    } finally {
      release.countDown();
    }
  }

  /**
   * Engine events arrive on libtorrent's alert thread: only non-blocking reads may happen there.
   */
  @Test
  void offeringReadsOnlyNonBlockingStateOnTheCallersThread() throws Exception {
    RecordingSink sink = new RecordingSink();
    KarmaEndorsementTrigger trigger = trigger(new EmptyIndex(), sink);
    FakeTransfer transfer = new FakeTransfer(TransferState.SEEDING, true, SOURCE, HASH);

    trigger.offer(transfer);
    awaitProbe(trigger, sink);

    assertFalse(
        transfer.blockingReadThreads.contains(Thread.currentThread()),
        "state, finished and v1 info hash are read off the caller's thread");
  }

  @Test
  void listenerChainForwardsDownloadFinished() {
    AtomicInteger finished = new AtomicInteger();
    BTEngineListenerChain chain =
        new BTEngineListenerChain(
            new BTEngineAdapter() {
              @Override
              public void downloadFinished(BTEngine engine, BTDownload dl) {
                finished.incrementAndGet();
              }
            });

    chain.downloadFinished(null, null);

    assertEquals(1, finished.get());
  }

  private KarmaEndorsementTrigger trigger(LocalIndex index, KarmaEndorsementSink sink) {
    return track(new KarmaEndorsementTrigger(List.of(OWN, PREVIOUS_OWN), sink, () -> true));
  }

  private KarmaEndorsementTrigger track(KarmaEndorsementTrigger trigger) {
    triggers.add(trigger);
    return trigger;
  }

  /**
   * Offers a transfer that must be endorsed and waits for it: everything offered before is done.
   */
  private static void awaitProbe(KarmaEndorsementTrigger trigger, RecordingSink sink)
      throws InterruptedException {
    String probe = PROBE_SOURCE.length + ":" + Hex.encode(PROBE_SOURCE) + "/" + PROBE_HASH;
    trigger.offer(new FakeTransfer(TransferState.SEEDING, true, PROBE_SOURCE, PROBE_HASH));
    // Before #1414 the probe is never endorsed either; don't hang, let the assertions fail.
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (!sink.calls.contains(probe) && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    Thread.sleep(50);
  }

  private static void awaitTrue(java.util.function.BooleanSupplier condition)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
  }

  private static byte[] key(int n) {
    byte[] key = new byte[32];
    key[0] = (byte) n;
    key[31] = (byte) 0x5a;
    return key;
  }

  private static String hash(int n) {
    byte[] hash = new byte[20];
    hash[0] = (byte) 0xab;
    hash[19] = (byte) n;
    return Hex.encode(hash).toLowerCase();
  }

  private static final class FakeTransfer implements KarmaEndorsementTrigger.Transfer {
    final TransferState state;
    final boolean finished;
    final byte[] source;
    final String infoHash;
    final Set<Thread> blockingReadThreads = ConcurrentHashMap.newKeySet();

    FakeTransfer(TransferState state, boolean finished, byte[] source, String infoHash) {
      this.state = state;
      this.finished = finished;
      this.source = source == null ? null : source.clone();
      this.infoHash = infoHash;
    }

    @Override
    public TransferState state() {
      blockingReadThreads.add(Thread.currentThread());
      return state;
    }

    @Override
    public boolean isFinished() {
      blockingReadThreads.add(Thread.currentThread());
      return finished;
    }

    @Override
    public byte[] distributedSourcePeerPub() {
      return source == null ? null : source.clone();
    }

    @Override
    public String infoHash() {
      return infoHash;
    }

    @Override
    public String v1InfoHash() {
      blockingReadThreads.add(Thread.currentThread());
      return infoHash;
    }
  }

  private static final class RecordingSink implements KarmaEndorsementSink {
    final List<String> calls = new CopyOnWriteArrayList<>();
    private final CountDownLatch release;

    RecordingSink() {
      this(new CountDownLatch(0));
    }

    RecordingSink(CountDownLatch release) {
      this.release = release;
    }

    @Override
    public void onDownloadCompletedFromPeer(byte[] peerEd25519Pub, byte[] infoHash) {
      try {
        release.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      calls.add(
          peerEd25519Pub.length
              + ":"
              + Hex.encode(peerEd25519Pub)
              + "/"
              + Hex.encode(infoHash).toLowerCase());
    }

    List<String> without(byte[] probeSource) {
      List<String> out = new ArrayList<>();
      String probe = probeSource.length + ":" + Hex.encode(probeSource) + "/";
      for (String call : calls) {
        if (!call.startsWith(probe)) {
          out.add(call);
        }
      }
      return out;
    }
  }

  private static final class MemoryStore implements KarmaChainStore {
    final List<KarmaChainEntry> entries = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void append(KarmaChainEntry entry) {
      entries.add(entry);
    }

    @Override
    public KarmaChain loadChain(byte[] ownerPub) {
      return new KarmaChain(ownerPub);
    }

    @Override
    public void close() {}
  }

  private static class EmptyIndex implements LocalIndex {
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
      return new ArrayList<>();
    }

    @Override
    public void markPublished(String infoHashHex, long timestamp) {}

    @Override
    public List<String> needsRepublish(long nowSec, long thresholdSec) {
      return new ArrayList<>();
    }

    @Override
    public void updateLastSeen(String infoHashHex, long ts) {}

    @Override
    public int size() {
      return 0;
    }
  }

  private static final class SingleTorrentIndex extends EmptyIndex {
    private final LocalSharedTorrent torrent;

    SingleTorrentIndex(String infoHashHex, byte[] publisher) {
      long now = System.currentTimeMillis() / 1000L;
      torrent =
          new LocalSharedTorrent.Builder()
              .infoHash(Hex.decode(infoHashHex))
              .name("shared")
              .sizeBytes(1)
              .fileCount(1)
              .filesJson("[]")
              .publisherNodeId(new byte[20])
              .publisherEd25519Pub(publisher)
              .publisherUtpPort(0)
              .addedAt(now)
              .lastSeenAt(now)
              .build();
    }

    @Override
    public Optional<LocalSharedTorrent> get(String infoHashHex) {
      return infoHashHex.equalsIgnoreCase(Hex.encode(torrent.infoHash()))
          ? Optional.of(torrent)
          : Optional.empty();
    }
  }
}
