/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.concurrent.concurrent.ExecutorsHelper;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Endorses the distributed-search peer a download came from when the download completes
 * (SPEC_KARMA_CHAIN.md Step 7). The source is the verified signer of the search result the user
 * downloaded, which BTEngine attaches to the transfer ({@link
 * BTDownload#getDistributedSourcePeerPub}) and keeps in its resume data. A download without one
 * endorses no one.
 *
 * <p>Events: {@code downloadFinished} on completion, and {@code downloadAdded}/{@code
 * downloadUpdate}, so a transfer restored already complete (finished while the relay was off, or
 * whose endorsement failed) is tried again. They arrive on libtorrent's alert thread, so {@link
 * #offer} only reads the in-memory source and the handle's info hash, keeps the newest event per
 * info hash and hands it to one worker; the worker reads the transfer's state and writes.
 *
 * <p>Deduplication: an info hash is marked done only after its endorsement was appended (or can
 * never be: already in the chain, an own or invalid key, a v2-only torrent), so a failure is
 * retried on the next event. Never endorsed: any of this node's keys, current or earlier
 * identity's, and the all-zero key. The manual "Endorse Source Peer" action stays; the chain
 * refuses a second endorsement of the same peer and torrent.
 */
public final class KarmaEndorsementTrigger implements BTEngineListener, AutoCloseable {

  private static final Logger LOG = Logger.getLogger(KarmaEndorsementTrigger.class);

  private static final String THREAD_NAME_PREFIX = "karma-endorse-";

  private final List<byte[]> ownKeys;
  private final KarmaEndorsementSink sink;
  private final Set<String> creditedInfoHashes = ConcurrentHashMap.newKeySet();

  /** Newest event per info hash not yet taken by the worker; one queued task per entry. */
  private final ConcurrentHashMap<String, Transfer> pending = new ConcurrentHashMap<>();

  private final DhtAdvertiser.Lifecycle lifecycle;
  // offer() queues at most one task per info hash, so the queue is bounded by the transfers.
  private final ExecutorService executor =
      ExecutorsHelper.newFixedSizeUnboundedThreadPool(1, THREAD_NAME_PREFIX);

  public KarmaEndorsementTrigger(
      LocalIndex index, byte[] ownEd25519Pub, KarmaEndorsementSink sink) {
    this(index, ownEd25519Pub, sink, () -> true);
  }

  /**
   * The nonblocking predicate must be bound to this identity generation. The caller owns listener
   * installation/removal and must close this trigger before cleanup. An opaque sink must guard its
   * own internal stages (e.g. a guarded KarmaChainWriter).
   */
  public KarmaEndorsementTrigger(
      LocalIndex index,
      byte[] ownEd25519Pub,
      KarmaEndorsementSink sink,
      BooleanSupplier permitted) {
    this(index, ownKeys(ownEd25519Pub), sink, permitted);
  }

  /**
   * As above, with every key this node has signed as: the current identity's first, then earlier
   * identities'. A source with any of them is never endorsed.
   */
  public KarmaEndorsementTrigger(
      LocalIndex index,
      Collection<byte[]> ownKeys,
      KarmaEndorsementSink sink,
      BooleanSupplier permitted) {
    if (index == null) {
      // Unused since the trigger endorses the download's own source; still required here.
      throw new IllegalArgumentException("index is null");
    }
    if (ownKeys == null || ownKeys.isEmpty()) {
      throw new IllegalArgumentException("ownKeys is null or empty");
    }
    for (byte[] key : ownKeys) {
      if (key == null) {
        throw new IllegalArgumentException("ownKeys has a null key");
      }
    }
    if (sink == null) {
      throw new IllegalArgumentException("sink is null");
    }
    List<byte[]> keys = new ArrayList<>(ownKeys.size());
    for (byte[] key : ownKeys) {
      keys.add(key.clone());
    }
    this.ownKeys = Collections.unmodifiableList(keys);
    this.sink = sink;
    this.lifecycle = new DhtAdvertiser.Lifecycle(permitted);
  }

  private static List<byte[]> ownKeys(byte[] ownEd25519Pub) {
    if (ownEd25519Pub == null) {
      throw new IllegalArgumentException("ownEd25519Pub is null");
    }
    return Collections.singletonList(ownEd25519Pub);
  }

  private boolean isOwnKey(byte[] key) {
    for (byte[] own : ownKeys) {
      if (Arrays.equals(own, key)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void started(BTEngine engine) {}

  @Override
  public void stopped(BTEngine engine) {
    close();
  }

  @Override
  public void downloadAdded(BTEngine engine, BTDownload dl) {
    if (dl != null) {
      offer(Transfer.of(dl));
    }
  }

  @Override
  public void downloadUpdate(BTEngine engine, BTDownload dl) {
    if (dl != null) {
      offer(Transfer.of(dl));
    }
  }

  @Override
  public void downloadFinished(BTEngine engine, BTDownload dl) {
    if (dl != null) {
      offer(Transfer.of(dl));
    }
  }

  /**
   * Queues a transfer event for the worker, replacing a queued event for the same info hash. Runs
   * on libtorrent's alert thread: reads only the in-memory source attribution and the handle's info
   * hash, neither of which waits for libtorrent.
   */
  void offer(Transfer dl) {
    if (dl == null || !lifecycle.getAsBoolean()) {
      return;
    }
    String key;
    try {
      if (dl.distributedSourcePeerPub() == null) {
        return;
      }
      key = dl.infoHash();
    } catch (Throwable t) {
      LOG.debug("Karma endorsement: could not read the transfer", t);
      return;
    }
    if (key == null || key.isEmpty()) {
      return;
    }
    String infoHashKey = key.toLowerCase(Locale.ROOT);
    if (creditedInfoHashes.contains(infoHashKey) || pending.put(infoHashKey, dl) != null) {
      // Done, or a queued task will take this newer event.
      return;
    }
    try {
      executor.execute(() -> drain(infoHashKey));
    } catch (RejectedExecutionException e) {
      // Closed: nothing will run it.
      pending.remove(infoHashKey, dl);
    }
  }

  private void drain(String infoHashKey) {
    Transfer dl = pending.remove(infoHashKey);
    if (dl == null) {
      return;
    }
    if (!lifecycle.enter()) {
      close();
      return;
    }
    try {
      if (endorse(dl)) {
        creditedInfoHashes.add(infoHashKey);
      }
    } catch (Throwable t) {
      LOG.warn("KarmaEndorsementTrigger: endorsement failed", t);
    } finally {
      lifecycle.leave();
      if (lifecycle.isClosed()) {
        close();
      }
    }
  }

  /**
   * Endorses the transfer's source if it completed. True when nothing is left to do for this
   * transfer; false when a later event should try again (not complete yet, no energy or Bitcoin
   * block right now, the store failed).
   */
  private boolean endorse(Transfer dl) {
    TransferState state = dl.state();
    if (state != TransferState.FINISHED && state != TransferState.SEEDING && !dl.isFinished()) {
      return false;
    }
    byte[] source = dl.distributedSourcePeerPub();
    if (source == null || source.length != 32 || isZero(source) || isOwnKey(source)) {
      return true;
    }
    String v1InfoHash = dl.v1InfoHash();
    if (v1InfoHash == null || v1InfoHash.isEmpty()) {
      // Signed endorsements name the v1 info hash; v2-only torrents can't be endorsed.
      return true;
    }
    byte[] infoHash = Hex.decode(v1InfoHash);
    if (!lifecycle.getAsBoolean()) {
      return false;
    }
    if (sink instanceof KarmaChainWriter) {
      KarmaChainWriter writer = (KarmaChainWriter) sink;
      if (writer.chain().hasEndorsement(source, infoHash)) {
        return true;
      }
      if (!writer.canEndorseSourcePeer(source, infoHash)) {
        // No energy left this epoch, or the writer was revoked.
        return false;
      }
      return writer.onDownloadCompletedFromPeer(source, infoHash, lifecycle);
    }
    sink.onDownloadCompletedFromPeer(source, infoHash);
    return true;
  }

  private static boolean isZero(byte[] key) {
    for (byte b : key) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Permanently revoke admission and discard queued updates without waiting. An in-flight
   * sink/provider may ignore interruption. Does not close the borrowed sink/index.
   */
  @Override
  public void close() {
    lifecycle.close();
    executor.shutdownNow();
  }

  /** Worker-only bounded drain after close; false forbids disposing borrowed resources yet. */
  public boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
    if (timeout < 0) {
      throw new IllegalArgumentException("timeout must be >= 0");
    }
    return executor.awaitTermination(timeout, unit);
  }

  /** What the trigger reads from a transfer; {@link #of} adapts a {@link BTDownload}. */
  interface Transfer {
    TransferState state();

    boolean isFinished();

    /** The verified distributed-search source carried by the transfer, or null. */
    byte[] distributedSourcePeerPub();

    /** The info hash libtorrent reports (v1 when the torrent has one), lowercase hex, or null. */
    String infoHash();

    /** The v1 info hash signed endorsements use, or null for a v2-only torrent. */
    String v1InfoHash();

    static Transfer of(BTDownload dl) {
      return new Transfer() {
        @Override
        public TransferState state() {
          return dl.getState();
        }

        @Override
        public boolean isFinished() {
          return dl.isFinished();
        }

        @Override
        public byte[] distributedSourcePeerPub() {
          return dl.getDistributedSourcePeerPub();
        }

        @Override
        public String infoHash() {
          return dl.getInfoHash();
        }

        @Override
        public String v1InfoHash() {
          return dl.getV1InfoHash();
        }
      };
    }
  }
}
