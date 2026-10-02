/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.icebridge.client.PeerRegistrySync;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Tiny installer that hands a {@link LocalIndex} and an optional {@link PeerKarmaCache} to the
 * desktop {@code SearchEngine.LOCAL} so the user-facing "Local" search can read from the local
 * distributed-search index and weight results by karma.
 *
 * <p>Mirrors {@code SharedTorrentIndexerInstaller} in spirit but for the search-engine side of the
 * relay stack.
 */
public final class LocalSearchEngineWire {
  private static final AtomicReference<Runnable> indexChangedListener = new AtomicReference<>();

  private LocalSearchEngineWire() {}

  /** Bind to this relay lifetime; stale cleanup cannot detach a replacement owner. */
  public static AutoCloseable bindIndexDigest(PeerRegistrySync sync, BooleanSupplier active) {
    Runnable listener =
        () -> {
          if (active.getAsBoolean()) {
            sync.announceIndexDigestSoon();
          }
        };
    indexChangedListener.set(listener);
    listener.run(); // Catch writes made before public networking became ready.
    return () -> indexChangedListener.compareAndSet(listener, null);
  }

  /** Only queues work; digest building and network I/O stay on the sync worker. */
  public static void indexChanged() {
    Runnable listener = indexChangedListener.get();
    if (listener != null) {
      listener.run();
    }
  }

  public static void setIndex(LocalIndex index) {
    if (index == null) {
      throw new IllegalArgumentException("index is null");
    }
    SearchEngine local = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.LOCAL_ID);
    if (local == null) {
      throw new IllegalStateException("LOCAL search engine is not registered");
    }
    local.setLocalIndex(index);
  }

  /**
   * Wire an optional karma cache so the LOCAL engine sorts results by the publisher's karma score.
   * Pass {@code null} to disable karma weighting. Idempotent; the most recent call wins.
   */
  public static void setKarmaCache(PeerKarmaCache karmaCache) {
    SearchEngine local = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.LOCAL_ID);
    if (local == null) {
      throw new IllegalStateException("LOCAL search engine is not registered");
    }
    local.setKarmaCache(karmaCache);
  }
}
