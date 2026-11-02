/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The karma of the peers behind distributed search results, for table renderers and list adapters,
 * which must not walk the trust graph themselves. {@link #get(String)} never blocks: it returns the
 * cached value (null until the first load) and loads a missing or stale one on the {@code loader}
 * executor. When a load changes a value, the listeners run on the {@code ui} executor, coalesced.
 *
 * <p>Values come from {@link DisplayKarma#forPeer(PeerDirectory, byte[])}, the same accessor the
 * Peer Directory and identity screens use. Bounded to {@link #MAX_ENTRIES} peers.
 */
public final class KarmaDisplayCache {

  private static final Logger LOG = Logger.getLogger(KarmaDisplayCache.class);

  /** How long a loaded value is shown before it is loaded again. */
  public static final long REFRESH_MS = 30_000;

  /** At most as many peers as the directory holds. */
  public static final int MAX_ENTRIES = PeerDirectory.DEFAULT_MAX_ENTRIES;

  private final Supplier<PeerDirectory> directory;
  private final Executor loader;
  private final Executor ui;
  private final LongSupplier clock;
  private final long refreshMs;
  private final int maxEntries;
  private final Map<String, Loaded> loaded = new ConcurrentHashMap<>();
  // Peer -> when its load was queued, so a peer is queued once at a time; a load the executor
  // dropped is queued again after refreshMs.
  private final Map<String, Long> queued = new ConcurrentHashMap<>();
  private final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();
  private final AtomicBoolean notifyQueued = new AtomicBoolean();

  public KarmaDisplayCache(Supplier<PeerDirectory> directory, Executor loader, Executor ui) {
    this(directory, loader, ui, System::currentTimeMillis, REFRESH_MS, MAX_ENTRIES);
  }

  KarmaDisplayCache(
      Supplier<PeerDirectory> directory,
      Executor loader,
      Executor ui,
      LongSupplier clock,
      long refreshMs,
      int maxEntries) {
    if (directory == null || loader == null || ui == null || clock == null) {
      throw new IllegalArgumentException("null argument");
    }
    if (refreshMs <= 0 || maxEntries <= 0) {
      throw new IllegalArgumentException("refreshMs and maxEntries must be > 0");
    }
    this.directory = directory;
    this.loader = loader;
    this.ui = ui;
    this.clock = clock;
    this.refreshMs = refreshMs;
    this.maxEntries = maxEntries;
  }

  /**
   * The hex key of the peer that returned a distributed search result, or null for any other
   * result. Compute it once per result, not per paint.
   */
  public static String sourcePeerHex(SearchResult sr) {
    if (!(sr instanceof CompositeFileSearchResult)
        || !DistributedSearchPerformer.SOURCE_NAME.equals(sr.getSource())) {
      return null;
    }
    return ((CompositeFileSearchResult) sr)
        .getDistributedSourcePeerPub()
        .map(Hex::encode)
        .orElse(null);
  }

  /**
   * The peer's cached karma, or null while it has not loaded (or the distributed search stack is
   * not running). Never blocks: a missing or stale value is loaded on the loader executor.
   */
  public DisplayKarma get(String peerHex) {
    if (peerHex == null) {
      return null;
    }
    long now = clock.getAsLong();
    Loaded value = loaded.get(peerHex);
    if (value == null || now - value.atMs >= refreshMs) {
      queueLoad(peerHex, now);
    }
    return value != null ? value.karma : null;
  }

  /** Runs {@code listener} on the ui executor after loads change values. */
  public void addListener(Runnable listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  /** Forgets every value, e.g. when the distributed search stack is rewired. */
  public void clear() {
    loaded.clear();
  }

  private void queueLoad(String peerHex, long now) {
    Long since = queued.putIfAbsent(peerHex, now);
    if (since != null && (now - since < refreshMs || !queued.replace(peerHex, since, now))) {
      return;
    }
    try {
      loader.execute(() -> load(peerHex));
    } catch (RuntimeException e) {
      queued.remove(peerHex, now);
      LOG.warn("Could not queue the karma load of " + peerHex, e);
    }
  }

  private void load(String peerHex) {
    try {
      DisplayKarma karma = DisplayKarma.forPeer(directory.get(), Hex.decode(peerHex));
      Loaded previous = loaded.put(peerHex, new Loaded(karma, clock.getAsLong()));
      trim();
      if (karma != null && (previous == null || !karma.equals(previous.karma))) {
        notifyListeners();
      }
    } catch (Throwable t) {
      LOG.warn("Could not load the karma of " + peerHex, t);
    } finally {
      queued.remove(peerHex);
    }
  }

  private void trim() {
    while (loaded.size() > maxEntries) {
      Map.Entry<String, Loaded> oldest = null;
      for (Map.Entry<String, Loaded> e : loaded.entrySet()) {
        if (oldest == null || e.getValue().atMs < oldest.getValue().atMs) {
          oldest = e;
        }
      }
      if (oldest == null) {
        return;
      }
      loaded.remove(oldest.getKey(), oldest.getValue());
    }
  }

  private void notifyListeners() {
    if (listeners.isEmpty() || !notifyQueued.compareAndSet(false, true)) {
      return;
    }
    try {
      ui.execute(
          () -> {
            notifyQueued.set(false);
            for (Runnable listener : listeners) {
              try {
                listener.run();
              } catch (RuntimeException e) {
                LOG.warn("Karma listener failed", e);
              }
            }
          });
    } catch (RuntimeException e) {
      notifyQueued.set(false);
      LOG.warn("Could not notify karma listeners", e);
    }
  }

  private static final class Loaded {
    final DisplayKarma karma;
    final long atMs;

    Loaded(DisplayKarma karma, long atMs) {
      this.karma = karma;
      this.atMs = atMs;
    }
  }
}
