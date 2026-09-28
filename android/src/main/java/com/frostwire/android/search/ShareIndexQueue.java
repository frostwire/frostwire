/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import com.frostwire.search.relay.LocalIndex;
import com.frostwire.util.Logger;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Bounded, serialized share-index writes for live transfers. A queued task never grants sharing
 * authorization: eligibility is re-checked right before and right after each write.
 *
 * <p>Generic over the transfer type so the lifecycle is testable without native handles.
 */
class ShareIndexQueue<D> implements AutoCloseable {
  private static final Logger LOG = Logger.getLogger(ShareIndexQueue.class);

  /**
   * Re-check delays for a transfer that was not yet shareable when its engine callback arrived. New
   * torrents (including seeds built from finished HTTP/YouTube downloads) are added paused and
   * resumed right after; the only callback observes the paused state and none follows once the
   * torrent starts seeding.
   */
  static final long[] RETRY_DELAYS_MS = {1_000L, 3_000L, 10_000L, 30_000L};

  /** Periodic sweep for transitions without an engine callback, such as a user resuming. */
  static final long RECONCILE_INTERVAL_MS = 60_000L;

  /** Attempt marker for sweep submissions, which never schedule retries. */
  private static final int SWEEP = -1;

  /** Delayed-task seam so tests can drive retries deterministically. */
  interface Delayer {
    void schedule(Runnable task, long delayMs);
  }

  private final LocalIndex index;
  private final Predicate<D> eligible;
  private final Consumer<D> indexDownload;
  private final Function<D, String> hashOf;
  private final Executor executor;
  private final Delayer delayer;
  private final ScheduledExecutorService ownedScheduler;
  private volatile Runnable indexChangedListener;
  private final Set<D> pending = ConcurrentHashMap.newKeySet();
  private final Set<D> indexed = Collections.newSetFromMap(new WeakHashMap<>());
  private final Object writes = new Object();
  private volatile boolean closed;

  /** Uses an owned daemon scheduler for retries and the reconcile sweep. */
  ShareIndexQueue(
      LocalIndex index,
      Predicate<D> eligible,
      Consumer<D> indexDownload,
      Function<D, String> hashOf,
      Executor executor) {
    this.index = index;
    this.eligible = eligible;
    this.indexDownload = indexDownload;
    this.hashOf = hashOf;
    this.executor = executor;
    ScheduledThreadPoolExecutor scheduler =
        new ScheduledThreadPoolExecutor(
            1,
            runnable -> {
              Thread t = new Thread(runnable, "AndroidShareIndexerRetry");
              t.setDaemon(true);
              return t;
            });
    scheduler.setRemoveOnCancelPolicy(true);
    this.ownedScheduler = scheduler;
    this.delayer =
        (task, delayMs) -> {
          try {
            scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
          } catch (RejectedExecutionException closedScheduler) {
            // Closed: nothing left to retry.
          }
        };
  }

  /** Test constructor: retries go through {@code delayer}; no reconcile sweep. */
  ShareIndexQueue(
      LocalIndex index,
      Predicate<D> eligible,
      Consumer<D> indexDownload,
      Function<D, String> hashOf,
      Executor executor,
      Delayer delayer) {
    this.index = index;
    this.eligible = eligible;
    this.indexDownload = indexDownload;
    this.hashOf = hashOf;
    this.executor = executor;
    this.delayer = delayer;
    this.ownedScheduler = null;
  }

  /** Index {@code download} once it is shareable, retrying briefly while it is not yet. */
  void submit(D download) {
    submit(download, 0);
  }

  /**
   * Re-evaluate live transfers on a fixed period. Already-indexed transfers are skipped; the sweep
   * does not start per-item retries of its own.
   */
  void startReconcile(Supplier<? extends Iterable<D>> live) {
    if (ownedScheduler == null || live == null) {
      return;
    }
    try {
      ownedScheduler.scheduleWithFixedDelay(
          () -> reconcile(live),
          RECONCILE_INTERVAL_MS,
          RECONCILE_INTERVAL_MS,
          TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException closedScheduler) {
      // Closed before start.
    }
  }

  void reconcile(Supplier<? extends Iterable<D>> live) {
    try {
      Iterable<D> downloads = live.get();
      if (downloads == null) return;
      for (D download : downloads) {
        if (closed) return;
        submit(download, SWEEP);
      }
    } catch (Throwable t) {
      LOG.debug("Share index reconcile failed", t);
    }
  }

  private void submit(D download, int attempt) {
    if (closed || download == null || !pending.add(download)) {
      return;
    }
    try {
      executor.execute(
          () -> {
            boolean retry = false;
            try {
              synchronized (writes) {
                if (closed) return;
                if (!eligible.test(download)) {
                  indexed.remove(download);
                  retry = attempt != SWEEP && attempt < RETRY_DELAYS_MS.length;
                  return;
                }
                if (!indexed.contains(download)) {
                  indexDownload.accept(download);
                  // Removal can happen on main while native metadata/SQLite is busy.
                  if (closed || !eligible.test(download)) {
                    index.delete(hashOf.apply(download));
                  } else {
                    indexed.add(download);
                    if (attempt != 0) {
                      LOG.info(
                          "Share index: indexed "
                              + hashOf.apply(download)
                              + (attempt == SWEEP ? " from reconcile sweep" : " after retry " + attempt));
                    }
                    notifyIndexChanged();
                  }
                }
              }
            } catch (Throwable t) {
              LOG.warn("Unable to index live transfer", t);
            } finally {
              pending.remove(download);
              if (retry && !closed) {
                delayer.schedule(() -> submit(download, attempt + 1), RETRY_DELAYS_MS[attempt]);
              }
            }
          });
    } catch (RejectedExecutionException full) {
      pending.remove(download); // A later engine update retries without growing a queue.
    }
  }

  @Override
  public void close() {
    closed = true;
    if (executor instanceof ThreadPoolExecutor) {
      ((ThreadPoolExecutor) executor).shutdownNow();
    }
    if (ownedScheduler != null) {
      ownedScheduler.shutdownNow();
    }
    synchronized (writes) {
      // Drain any in-flight write before a replacement identity can index the same hash.
      pending.clear();
      indexed.clear();
    }
  }

  void withdraw(String hash, BooleanSupplier stillRemoved) {
    if (closed) return;
    try {
      executor.execute(
          () -> {
            synchronized (writes) {
              try {
                if (!closed && stillRemoved.getAsBoolean()) {
                  index.delete(hash);
                  notifyIndexChanged();
                }
              } catch (Throwable t) {
                LOG.warn("Unable to remove withdrawn torrent from local index", t);
              }
            }
          });
    } catch (RejectedExecutionException full) {
      // A historical row is never authorization; do not block main on SQLite or a full queue.
    }
  }

  void setIndexChangedListener(Runnable listener) {
    indexChangedListener = listener;
  }

  private void notifyIndexChanged() {
    Runnable listener = indexChangedListener;
    if (listener != null && !closed) {
      try {
        listener.run();
      } catch (RuntimeException e) {
        LOG.debug("Unable to schedule index digest announcement", e);
      }
    }
  }
}
