/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.widget.AbsListView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class TransferUpdateExecutorAndScrollListenersTest {

  @Test
  public void anUninitializedExecutorRejectsWork() {
    TransferUpdateExecutor executor = new TransferUpdateExecutor();
    assertFalse(executor.isAvailable());
    assertFalse(executor.execute(() -> {}));
    // shutting down something never started is harmless
    executor.shutdown();
  }

  @Test
  public void aStartedExecutorRunsTasksUntilItIsShutDown() throws Exception {
    TransferUpdateExecutor executor = new TransferUpdateExecutor();
    executor.initialize();
    executor.initialize(); // idempotent
    assertTrue(executor.isAvailable());
    CountDownLatch ran = new CountDownLatch(1);
    assertTrue(executor.execute(ran::countDown));
    assertTrue("the task ran", ran.await(5, TimeUnit.SECONDS));
    executor.shutdown();
    assertFalse(executor.isAvailable());
    assertFalse(executor.execute(() -> {}));
    executor.shutdown(); // already shut down
    // a shut down executor can be brought back
    executor.initialize();
    assertTrue(executor.isAvailable());
    executor.shutdown();
  }

  @Test
  public void composedListenersForwardToEveryListenerEvenWhenOneThrows() {
    AbsListView.OnScrollListener bad = mock(AbsListView.OnScrollListener.class);
    AbsListView.OnScrollListener good = mock(AbsListView.OnScrollListener.class);
    AbsListView view = mock(AbsListView.class);
    doThrow(new IllegalStateException("boom")).when(bad).onScrollStateChanged(view, 1);
    doThrow(new IllegalStateException("boom")).when(bad).onScroll(view, 1, 2, 3);
    ScrollListeners.ComposedOnScrollListener composed =
        new ScrollListeners.ComposedOnScrollListener(bad, good);
    composed.onScrollStateChanged(view, 1);
    composed.onScroll(view, 1, 2, 3);
    verify(good).onScrollStateChanged(view, 1);
    verify(good).onScroll(view, 1, 2, 3);
  }

  @Test
  public void composedListenersWithoutMembersDoNothing() {
    AbsListView view = mock(AbsListView.class);
    ScrollListeners.ComposedOnScrollListener empty = new ScrollListeners.ComposedOnScrollListener();
    empty.onScrollStateChanged(view, 0);
    empty.onScroll(view, 0, 0, 0);
    ScrollListeners.ComposedOnScrollListener none =
        new ScrollListeners.ComposedOnScrollListener((AbsListView.OnScrollListener[]) null);
    none.onScrollStateChanged(view, 0);
    none.onScroll(view, 0, 0, 0);
    verify(view, never()).setFastScrollEnabled(anyBoolean());
  }

  @Test
  public void fastScrollIsOnlyEnabledWhileScrolling() {
    AbsListView view = mock(AbsListView.class);
    ScrollListeners.FastScrollDisabledWhenIdleOnScrollListener listener =
        new ScrollListeners.FastScrollDisabledWhenIdleOnScrollListener();
    listener.onScrollStateChanged(view, AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
    verify(view).setFastScrollEnabled(false);
    listener.onScrollStateChanged(view, AbsListView.OnScrollListener.SCROLL_STATE_FLING);
    verify(view).setFastScrollEnabled(true);
    listener.onScroll(view, 0, 0, 0);
    assertEquals(0, ScrollListeners.class.getDeclaredClasses().length - 2);
  }
}
