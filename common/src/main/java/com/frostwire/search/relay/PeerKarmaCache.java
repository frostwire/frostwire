/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Karma of remote peers, from the chains {@link RemoteKarmaChainFetcher} fetches and verifies.
 *
 * <p>Two numbers per peer (frostwire/frostwire#1417):
 *
 * <ul>
 *   <li><b>received karma</b> ({@link #receivedKarma(byte[])}): the trust-weighted endorsements
 *       other peers gave it, from every chain this cache's fetcher verified ({@link
 *       ReceivedKarma}). This is the peer's karma.
 *   <li><b>participation</b> ({@link #participation(byte[])}): the endorsements the peer's own
 *       verified chain gives others. Anyone can raise it by endorsing made-up peers, so it is shown
 *       as participation and never counted as trust (frostwire/frostwire#1416, question 4).
 * </ul>
 *
 * <p>The fetcher owns one bounded, expiring positive/negative cache; every chain it verifies is
 * indexed into {@link #received()}.
 */
public class PeerKarmaCache implements AutoCloseable {

  private final RemoteKarmaChainFetcher fetcher;
  private final ReceivedKarma received = new ReceivedKarma();
  private final BiConsumer<byte[], List<KarmaChainEntry>> indexer = received::indexChain;
  private final AtomicLong fetches = new AtomicLong();
  private Runnable untrackLocalNode;
  private final AtomicLong cacheHits = new AtomicLong();

  public PeerKarmaCache(RemoteKarmaChainFetcher fetcher) {
    if (fetcher == null) {
      throw new IllegalArgumentException("fetcher is null");
    }
    this.fetcher = fetcher;
    fetcher.addVerifiedChainListener(indexer);
  }

  /**
   * The index of received endorsements this cache scores from, for wiring this node's identity, its
   * own chain and the announcements it verifies.
   */
  public ReceivedKarma received() {
    return received;
  }

  /**
   * Wires this node into {@link #received()}: its key, its own chain (indexed now and again after
   * each endorsement {@code writer} appends), the clock (its chain's current epoch), its own index
   * (endorsements of this node count for torrents in it) and the first-sightings file in {@code
   * relayHomeDir}. Reads the file: call on a worker, once per relay generation.
   */
  public void trackLocalNode(
      byte[] ownPub, KarmaChainWriter writer, LocalIndex index, File relayHomeDir) {
    if (ownPub == null || writer == null || index == null) {
      throw new IllegalArgumentException("null argument");
    }
    byte[] own = ownPub.clone();
    received.setSelf(own, infoHash -> index.get(Hex.encode(infoHash)).isPresent());
    received.setClock(() -> writer.chain().currentEpoch());
    if (relayHomeDir != null) {
      received.persistTo(new File(relayHomeDir, RelayConstants.KARMA_FIRST_SEEN_FILE));
    }
    Runnable indexOwnChain = () -> received.indexChain(own, writer.chain().entries());
    indexOwnChain.run();
    writer.addEndorsementListener(indexOwnChain);
    synchronized (this) {
      untrackLocalNode = () -> writer.removeEndorsementListener(indexOwnChain);
    }
  }

  /**
   * The peer's received, trust-weighted karma. Never waits: it reads the index, and queues a fetch
   * of the peer's own chain (whose endorsements of others it indexes) when that isn't cached.
   */
  public double receivedKarma(byte[] peerPub) {
    if (peerPub == null || peerPub.length != 32) {
      return 0;
    }
    fetcher.getCachedChain(peerPub);
    return received.score(peerPub);
  }

  /**
   * The number of endorsements the peer's verified chain gives. 0 means no chain or no endorsements
   * in what was published. Never negative. May wait up to the fetcher's lookup timeout; routing/UI
   * callers must use {@link #cachedParticipation(byte[])}.
   */
  public long participation(byte[] peerPub) {
    if (peerPub == null || peerPub.length != 32) {
      return 0;
    }
    if (fetcher.isCached(peerPub)) {
      cacheHits.incrementAndGet();
    } else {
      fetches.incrementAndGet();
    }
    return endorsementsGiven(fetcher.fetchChain(peerPub));
  }

  /** Nonblocking {@link #participation(byte[])}; coalesces bounded refresh work on cache misses. */
  public long cachedParticipation(byte[] peerPub) {
    return endorsementsGiven(fetcher.getCachedChain(peerPub));
  }

  /** Drop the cached chain for a peer; the next lookup re-fetches. */
  public void evict(byte[] peerPub) {
    if (peerPub == null) {
      return;
    }
    fetcher.evict(peerPub);
  }

  /** Drop all cached chains. The received-endorsement index is kept. */
  public void clear() {
    fetcher.clear();
  }

  /**
   * This cache owns its fetcher; close cancels its refreshes, not the shared DHT session, and saves
   * the index's first sightings.
   */
  @Override
  public void close() {
    fetcher.removeVerifiedChainListener(indexer);
    Runnable untrack;
    synchronized (this) {
      untrack = untrackLocalNode;
      untrackLocalNode = null;
    }
    if (untrack != null) {
      untrack.run();
    }
    fetcher.close();
    received.save();
  }

  /** Diagnostic counters. */
  public long fetchCount() {
    return fetches.get();
  }

  public long cacheHitCount() {
    return cacheHits.get();
  }

  /** The number of endorsements in {@code chain}: what its owner gave, not what it received. */
  public static long endorsementsGiven(List<KarmaChainEntry> chain) {
    if (chain == null || chain.isEmpty()) {
      return 0;
    }
    long count = 0;
    for (KarmaChainEntry e : chain) {
      if (e.kind() == KarmaChainEntry.Kind.ENDORSEMENT) {
        count++;
      }
    }
    return count;
  }
}
