/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IndexResult;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.SharedTorrentIndexer;
import com.frostwire.util.Logger;

import java.util.Set;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Owns bounded indexing work; a queued callback never grants sharing authorization. */
final class AndroidSharedTorrentIndexer implements BTEngineListener, AutoCloseable {    private static final Logger LOG = Logger.getLogger(AndroidSharedTorrentIndexer.class);
    private final LocalIndex index;
    private final Predicate<BTDownload> eligible;
    private final Consumer<BTDownload> indexDownload;
    private final Executor executor;
    private final Set<BTDownload> pending = ConcurrentHashMap.newKeySet();
    private final Set<BTDownload> indexed = Collections.newSetFromMap(new WeakHashMap<>());
    private final Object writes = new Object();
    private volatile boolean closed;

    AndroidSharedTorrentIndexer(LocalIndex index, IdentityKeys identity, BooleanSupplier permitted) {
        this(index, dl -> permitted.getAsBoolean()
                        && AndroidShareVisibility.isLiveTransfer(dl.getInfoHash(), dl)
                        && permitted.getAsBoolean(),
                dl -> {
                    IndexResult result = new SharedTorrentIndexer(index, identity)
                            .indexTorrentInfo(dl.getTorrentHandle().torrentFile(), dl.getName());
                    if (result != IndexResult.UPSERTED) {
                        throw new IllegalStateException("Torrent indexing failed: " + result);
                    }
                },
                new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(64), runnable -> new Thread(runnable, "AndroidShareIndexer")));
    }

    AndroidSharedTorrentIndexer(LocalIndex index, Predicate<BTDownload> eligible,
                                Consumer<BTDownload> indexDownload, Executor executor) {
        this.index = index;
        this.eligible = eligible;
        this.indexDownload = indexDownload;
        this.executor = executor;
    }

    @Override
    public void started(BTEngine engine) {}

    @Override
    public void stopped(BTEngine engine) {}

    @Override
    public void downloadAdded(BTEngine engine, BTDownload download) {
        downloadUpdate(engine, download);
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload download) {
        if (closed || download == null || !pending.add(download)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    synchronized (writes) {
                        if (closed) return;
                        if (!eligible.test(download)) {
                            indexed.remove(download);
                            return;
                        }
                        if (!indexed.contains(download)) {
                            indexDownload.accept(download);
                            // Removal can happen on main while native metadata/SQLite is busy.
                            if (closed || !eligible.test(download)) {
                                index.delete(download.getInfoHash());
                            } else {
                                indexed.add(download);
                            }
                        }
                    }
                } catch (Throwable t) {
                    LOG.warn("Unable to index live transfer", t);
                } finally {
                    pending.remove(download);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            pending.remove(download); // A later engine update retries without growing a queue.
        }
    }

    @Override
    public void close() {
        closed = true;
        if (executor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executor).shutdownNow();
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
            executor.execute(() -> {
                synchronized (writes) {
                    try {
                        if (!closed && stillRemoved.getAsBoolean()) index.delete(hash);
                    } catch (Throwable t) {
                        LOG.warn("Unable to remove withdrawn torrent from local index", t);
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            // A historical row is never authorization; do not block main on SQLite or a full queue.
        }
    }
}
