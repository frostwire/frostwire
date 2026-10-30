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
 * BTEngineListener that watches for download completion and fires a {@link KarmaEndorsementSink}
 * callback when the torrent was published by a peer (not by ourselves).
 *
 * <p>Deduplication: tracks already-credited info hashes so each download triggers exactly one
 * endorsement, even if multiple {@code downloadUpdate} events fire after completion.
 *
 * <p>Self-exclusion: endorsements are only fired for peers whose Ed25519 pubkey is non-zero AND
 * differs from our own pubkey.
 */
public final class KarmaEndorsementTrigger implements BTEngineListener, AutoCloseable {

  private static final Logger LOG = Logger.getLogger(KarmaEndorsementTrigger.class);

  private static final String THREAD_NAME_PREFIX = "karma-endorse-";

  private final LocalIndex index;
  private final List<byte[]> ownKeys;
  private final KarmaEndorsementSink sink;
  private final Set<String> creditedInfoHashes = ConcurrentHashMap.newKeySet();
  private final DhtAdvertiser.Lifecycle lifecycle;
  // The shared factory bounds this to one running callback and four queued updates.
  private final ExecutorService executor =
      ExecutorsHelper.newFixedSizeThreadPool(1, THREAD_NAME_PREFIX);

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
    this.index = index;
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
    // No-op: we only act on downloadUpdate when state transitions to FINISHED
  }

  @Override
  public void downloadUpdate(BTEngine engine, BTDownload dl) {
    if (dl == null) {
      return;
    }
    offer(Transfer.of(dl));
  }

  /** Hands a transfer event to the worker. */
  void offer(Transfer dl) {
    if (dl == null || !lifecycle.getAsBoolean()) {
      return;
    }
    try {
      executor.execute(() -> endorse(dl));
    } catch (RejectedExecutionException e) {
      // No credit was claimed: a later update can retry unless this generation closed.
      LOG.debug("Karma endorsement update rejected (closed or queue full)");
    }
  }

  private void endorse(Transfer dl) {
    if (!lifecycle.enter()) {
      close();
      return;
    }
    try {
      TransferState state = dl.state();
      if (!lifecycle.getAsBoolean()) {
        return;
      }
      if (state != TransferState.FINISHED && state != TransferState.SEEDING && !dl.isFinished()) {
        return;
      }
      // Distributed-search source endorsements are explicitly user initiated from the
      // transfer UI; do not silently consume karma when this automatic completion listener
      // sees the same attributed transfer.
      if (dl.distributedSourcePeerPub() != null) {
        return;
      }
      if (!lifecycle.getAsBoolean()) {
        return;
      }
      String infoHashHex;
      try {
        infoHashHex = dl.infoHash();
      } catch (Throwable t) {
        LOG.debug("Failed to read info hash from BTDownload", t);
        return;
      }
      if (infoHashHex == null || infoHashHex.isEmpty()) {
        return;
      }
      if (!lifecycle.getAsBoolean()) {
        return;
      }
      infoHashHex = infoHashHex.toLowerCase(Locale.ROOT);
      // Deduplicate
      if (!creditedInfoHashes.add(infoHashHex)) {
        return;
      }
      LocalSharedTorrent torrent = index.get(infoHashHex).orElse(null);
      if (torrent == null || !lifecycle.getAsBoolean()) {
        return;
      }
      byte[] peerPub = torrent.publisherEd25519Pub();
      if (peerPub == null) {
        return;
      }
      // Exclude self-endorsement
      if (isOwnKey(peerPub)) {
        return;
      }
      // Exclude placeholder zero pubkey
      boolean allZero = true;
      for (byte b : peerPub) {
        if (b != 0) {
          allZero = false;
          break;
        }
      }
      if (allZero) {
        return;
      }
      final byte[] peerPubFinal = peerPub.clone();
      final byte[] infoHashFinal = hexToBytes(infoHashHex);
      if (!lifecycle.getAsBoolean()) {
        return;
      }
      if (sink instanceof KarmaChainWriter) {
        ((KarmaChainWriter) sink)
            .onDownloadCompletedFromPeer(peerPubFinal, infoHashFinal, lifecycle);
      } else {
        sink.onDownloadCompletedFromPeer(peerPubFinal, infoHashFinal);
      }
    } catch (Throwable t) {
      LOG.warn("KarmaEndorsementTrigger error on downloadUpdate", t);
    } finally {
      lifecycle.leave();
      if (lifecycle.isClosed()) {
        close();
      }
    }
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

  private static byte[] hexToBytes(String hex) {
    return com.frostwire.util.Hex.decode(hex);
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
