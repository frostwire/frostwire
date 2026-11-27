/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TimerServiceTest {

  private static void advance(long seconds) {
    ShadowLooper.idleMainLooper(seconds, TimeUnit.SECONDS);
  }

  @Test
  public void observerIsCalledEveryIntervalUntilUnsubscribed() {
    AtomicInteger ticks = new AtomicInteger();
    TimerObserver observer = ticks::incrementAndGet;
    TimerSubscription subscription = TimerService.subscribe(observer, 2);
    assertTrue(subscription.isSubscribed());

    advance(1);
    assertEquals(0, ticks.get());
    advance(1);
    assertEquals(1, ticks.get());
    advance(4);
    assertEquals(3, ticks.get());

    subscription.unsubscribe();
    assertFalse(subscription.isSubscribed());
    advance(10);
    assertEquals(3, ticks.get());
  }

  @Test
  public void reSubscribeRevivesAnUnsubscribedSubscriptionWithANewObserver() {
    AtomicInteger first = new AtomicInteger();
    AtomicInteger second = new AtomicInteger();
    TimerObserver firstObserver = first::incrementAndGet;
    TimerObserver secondObserver = second::incrementAndGet;
    TimerSubscription subscription = TimerService.subscribe(firstObserver, 1);
    subscription.unsubscribe();
    advance(2);
    assertEquals(0, first.get());

    TimerService.reSubscribe(secondObserver, subscription, 1);
    assertTrue(subscription.isSubscribed());
    advance(3);
    assertEquals(0, first.get());
    assertEquals(3, second.get());
  }

  @Test
  public void throwingObserverIsUnsubscribedAutomatically() {
    AtomicInteger ticks = new AtomicInteger();
    TimerObserver observer =
        () -> {
          ticks.incrementAndGet();
          throw new IllegalStateException("boom");
        };
    TimerSubscription subscription = TimerService.subscribe(observer, 1);
    advance(5);
    assertEquals(1, ticks.get());
    assertFalse(subscription.isSubscribed());
  }

  @Test
  public void goneViewObserverIsNotNotifiedButStaysSubscribed() {
    Context context = ApplicationProvider.getApplicationContext();
    AtomicInteger ticks = new AtomicInteger();
    class TickingView extends View implements TimerObserver {
      TickingView(Context c) {
        super(c);
      }

      @Override
      public void onTime() {
        ticks.incrementAndGet();
      }
    }
    TickingView view = new TickingView(context);
    TimerSubscription subscription = TimerService.subscribe(view, 1);
    view.setVisibility(View.GONE);
    advance(2);
    assertEquals(0, ticks.get());
    assertTrue(subscription.isSubscribed());
    view.setVisibility(View.VISIBLE);
    advance(1);
    assertEquals(1, ticks.get());
  }

  @Test
  public void onTimeDirectlyOnUnsubscribedDoesNothing() {
    AtomicInteger ticks = new AtomicInteger();
    TimerObserver observer = ticks::incrementAndGet;
    TimerSubscription subscription = TimerService.subscribe(observer, 60);
    subscription.onTime();
    assertEquals(1, ticks.get());
    subscription.unsubscribe();
    subscription.onTime();
    assertEquals(1, ticks.get());
  }
}
