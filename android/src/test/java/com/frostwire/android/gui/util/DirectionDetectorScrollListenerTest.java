/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.util;

import static android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * frostwire/frostwire#1385: the listener slept the main thread for 400 ms whenever a scroll came to
 * rest (and 100 ms on each direction change) instead of scheduling its delayed work.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class DirectionDetectorScrollListenerTest {

  private final AtomicInteger downs = new AtomicInteger();
  private final DirectionDetectorScrollListener listener =
      new DirectionDetectorScrollListener(
          new DirectionDetectorScrollListener.ScrollDirectionListener() {
            @Override
            public void onScrollUp() {}

            @Override
            public void onScrollDown() {
              downs.incrementAndGet();
            }
          });

  @Test
  public void aScrollComingToRestDoesNotBlockTheMainThread() {
    long start = System.nanoTime();
    listener.onScrollStateChanged(null, SCROLL_STATE_IDLE);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertTrue("onScrollStateChanged blocked for " + elapsedMs + " ms", elapsedMs < 200);
  }

  @Test
  public void theIdleResetStillHappensAfterTheDelay() {
    scrollDownTo(1);
    scrollDownTo(2);
    scrollDownTo(3); // three votes, one short of a decision

    listener.onScrollStateChanged(null, SCROLL_STATE_IDLE);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    scrollDownTo(4); // after the idle reset this is the first vote again

    assertEquals(0, downs.get());
  }

  @Test
  public void aDirectionIsReportedAfterEnoughVotes() {
    for (int i = 1; i <= 4; i++) {
      scrollDownTo(i);
    }
    assertEquals(1, downs.get());
  }

  private void scrollDownTo(int firstVisibleItem) {
    listener.onScroll(null, firstVisibleItem, 5, 100);
  }
}
