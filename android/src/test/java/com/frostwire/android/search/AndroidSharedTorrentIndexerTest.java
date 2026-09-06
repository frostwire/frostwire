/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import android.app.Application;
import com.frostwire.search.relay.LocalIndex;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Lifecycle semantics of the shared indexer without native handles.
 *
 * <p>downloadUpdate() requires BTDownload instances whose static initializer
 * loads the jlibtorrent native library, unavailable to the unit JVM; those
 * paths are covered by desktop/common tests. These tests drive the
 * withdraw()/close() paths that carry the same lifecycle properties:
 * revocation before queued work runs, no resurrection, coalescing, and
 * terminal close.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AndroidSharedTorrentIndexerTest {
    @Test
    public void withdrawnHashIsNotIndexedByQueuedWork() {
        LocalIndex index = mock(LocalIndex.class);
        List<Runnable> tasks = new ArrayList<>();
        AtomicBoolean live = new AtomicBoolean(true);
        AndroidSharedTorrentIndexer indexer = new AndroidSharedTorrentIndexer(
                index, dl -> live.get(), dl -> {}, tasks::add);
        indexer.withdraw("abcd", () -> true);
        live.set(false);
        tasks.get(0).run();
        verify(index).delete("abcd");
        indexer.close();
    }

    @Test
    public void replacementHashIsNotDeletedByStaleWithdrawal() {
        LocalIndex index = mock(LocalIndex.class);
        List<Runnable> work = new ArrayList<>();
        AtomicBoolean removed = new AtomicBoolean(true);
        AndroidSharedTorrentIndexer indexer = new AndroidSharedTorrentIndexer(index,
                dl -> true, dl -> {}, work::add);
        indexer.withdraw("replaced", removed::get);
        removed.set(false);
        work.get(0).run();
        verify(index, never()).delete("replaced");
        indexer.withdraw("removed", () -> true);
        work.get(1).run();
        verify(index).delete("removed");
        indexer.close();
    }

    @Test
    public void shutdownInvalidatesQueuedWork() {
        LocalIndex index = mock(LocalIndex.class);
        List<Runnable> tasks = new ArrayList<>();
        AndroidSharedTorrentIndexer indexer = new AndroidSharedTorrentIndexer(index,
                dl -> true, dl -> {}, tasks::add);
        indexer.withdraw("queued", () -> true);
        indexer.close();
        tasks.get(0).run();
        verify(index, never()).delete("queued");
    }

    @Test
    public void revocationDuringBlockedWriteFailsClosed() throws Exception {
        LocalIndex index = mock(LocalIndex.class);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger deletes = new AtomicInteger();
        AndroidSharedTorrentIndexer indexer = new AndroidSharedTorrentIndexer(index,
                dl -> active.get(), dl -> {}, (task) -> new Thread(() -> {
                    writing.countDown();
                    try {
                        assertTrue(finish.await(2, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    task.run();
                }).start());
        Thread withdrawal = new Thread(() -> indexer.withdraw("abcd", active::get));
        try {
            withdrawal.start();
            assertTrue(writing.await(2, TimeUnit.SECONDS));
            active.set(false);
            finish.countDown();
            withdrawal.join(2000);
            assertEquals(0, deletes.get());
            verify(index, never()).delete("abcd");
        } finally {
            finish.countDown();
            withdrawal.join(2000);
            indexer.close();
        }
    }

    @Test
    public void closedIndexerIgnoresLateWithdrawals() {
        LocalIndex index = mock(LocalIndex.class);
        List<Runnable> work = new ArrayList<>();
        AndroidSharedTorrentIndexer indexer = new AndroidSharedTorrentIndexer(index,
                dl -> true, dl -> {}, work::add);
        indexer.withdraw("retired", () -> true);
        indexer.close();
        work.get(0).run();
        verify(index, never()).delete("retired");
    }

    @Test
    public void queuedWithdrawalCannotDeleteReplacementOrOutliveItsOwner() {
        LocalIndex index = mock(LocalIndex.class);
        List<Runnable> work = new ArrayList<>();
        AtomicBoolean removed = new AtomicBoolean(true);
        AndroidSharedTorrentIndexer indexer = new AndroidSharedTorrentIndexer(index,
                dl -> true, dl -> {}, work::add);
        indexer.withdraw("replaced", removed::get);
        removed.set(false);
        work.get(0).run();
        verify(index, never()).delete("replaced");
        indexer.withdraw("removed", () -> true);
        work.get(1).run();
        verify(index).delete("removed");
        indexer.withdraw("retired", () -> true);
        indexer.close();
        work.get(2).run();
        verify(index, never()).delete("retired");
    }
}
