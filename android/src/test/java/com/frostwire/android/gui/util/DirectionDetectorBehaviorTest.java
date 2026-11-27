/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.util;

import static android.widget.AbsListView.OnScrollListener.SCROLL_STATE_FLING;
import static android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
import static android.widget.AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL;
import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The list scroll direction voting: up and down decisions, the top of the list, and rearming. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class DirectionDetectorBehaviorTest {
  private final AtomicInteger ups = new AtomicInteger();
  private final AtomicInteger downs = new AtomicInteger();
  private final DirectionDetectorScrollListener listener =
      new DirectionDetectorScrollListener(
          new DirectionDetectorScrollListener.ScrollDirectionListener() {
            @Override
            public void onScrollUp() {
              ups.incrementAndGet();
            }

            @Override
            public void onScrollDown() {
              downs.incrementAndGet();
            }
          });

  private void scrollTo(int firstVisible) {
    listener.onScroll(null, firstVisible, 5, 100);
  }

  @Test
  public void fourDownwardStepsDecideTheListIsScrollingDown() {
    for (int i = 1; i <= 3; i++) {
      scrollTo(10 + i);
    }
    assertEquals(0, downs.get());
    scrollTo(14);
    assertEquals(1, downs.get());
    assertEquals(0, ups.get());
  }

  @Test
  public void fourUpwardStepsDecideTheListIsScrollingUp() {
    scrollTo(50); // jump down first, a single vote
    listener.onScrollStateChanged(null, SCROLL_STATE_IDLE);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400)); // forget that vote
    for (int i = 1; i <= 4; i++) {
      scrollTo(50 - i);
    }
    assertEquals(1, ups.get());
    assertEquals(0, downs.get());
  }

  @Test
  public void afterADecisionTheSameDirectionIsMutedBrieflyThenRearmed() {
    for (int i = 1; i <= 4; i++) {
      scrollTo(10 + i);
    }
    assertEquals(1, downs.get());
    // more steps down right away are not counted while the direction is muted
    for (int i = 5; i <= 12; i++) {
      scrollTo(10 + i);
    }
    assertEquals(1, downs.get());
    // after the mute interval the same gesture decides again
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    for (int i = 13; i <= 16; i++) {
      scrollTo(10 + i);
    }
    assertEquals(2, downs.get());
  }

  @Test
  public void mutingTheUpDirectionRearmsToo() {
    scrollTo(60);
    listener.onScrollStateChanged(null, SCROLL_STATE_IDLE);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    for (int i = 1; i <= 4; i++) {
      scrollTo(60 - i);
    }
    assertEquals(1, ups.get());
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    for (int i = 5; i <= 8; i++) {
      scrollTo(60 - i);
    }
    assertEquals(2, ups.get());
  }

  @Test
  public void returningToTheTopAlwaysRevealsTheChromeWithoutVotes() {
    scrollTo(5);
    scrollTo(0);
    assertEquals(1, ups.get());
    // already at the top: nothing more to announce
    scrollTo(0);
    assertEquals(1, ups.get());
  }

  @Test
  public void aListThatFitsOnOneScreenNeverVotes() {
    for (int i = 1; i <= 8; i++) {
      listener.onScroll(null, i, 10, 10);
    }
    assertEquals(0, downs.get());
    assertEquals(0, ups.get());
  }

  @Test
  public void aMixedGestureWithoutAClearMajorityDecidesNothing() {
    scrollTo(10);
    scrollTo(9);
    scrollTo(10);
    scrollTo(9);
    assertEquals(0, downs.get());
    assertEquals(0, ups.get());
  }

  @Test
  public void startingAFlingOrATouchScrollReevaluatesTheVotes() {
    scrollTo(1);
    scrollTo(2);
    scrollTo(3);
    listener.onScrollStateChanged(null, SCROLL_STATE_FLING);
    assertEquals(0, downs.get());
    scrollTo(4);
    assertEquals(1, downs.get());
    listener.onScrollStateChanged(null, SCROLL_STATE_TOUCH_SCROLL);
    // the idle task does nothing while a finger is on the list
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
  }

  @Test
  public void aListenerWithoutACallbackIgnoresScrolling() {
    DirectionDetectorScrollListener silent = new DirectionDetectorScrollListener(null);
    for (int i = 1; i <= 10; i++) {
      silent.onScroll(null, i, 5, 100);
    }
    silent.onScrollStateChanged(null, SCROLL_STATE_IDLE);
    assertEquals(0, downs.get());
  }
}
