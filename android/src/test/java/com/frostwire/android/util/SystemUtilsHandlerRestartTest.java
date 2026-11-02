/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.HandlerThread;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * SystemUtils keeps one HandlerThread per name in a static map. When that thread's looper had quit
 * (Robolectric quits background loopers after every test), postToHandler kept posting to the dead
 * Handler, which drops the task: AndroidPaths, which waits for its MISC task, then hung the next
 * test's MainApplication.onCreate forever.
 *
 * <p>The test uses SEARCH_PERFORMER: under Robolectric, MainApplication's background start-up keeps
 * MISC busy waiting for the Python runtime, so a task posted there doesn't run promptly.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SystemUtilsHandlerRestartTest {

  @Test
  public void aTaskPostedAfterTheHandlerThreadQuitStillRuns() throws Exception {
    CountDownLatch first = new CountDownLatch(1);
    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, first::countDown);
    assertTrue(first.await(5, TimeUnit.SECONDS));

    HandlerThread misc = findThread("SystemUtils::HandlerThread - SEARCH_PERFORMER");
    assertNotNull(misc);
    misc.quitSafely();
    misc.join(5_000);

    CountDownLatch second = new CountDownLatch(1);
    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, second::countDown);
    assertTrue("the task was dropped", second.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void aDelayedTaskPostedAfterTheHandlerThreadQuitStillRuns() throws Exception {
    CountDownLatch first = new CountDownLatch(1);
    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, first::countDown);
    assertTrue(first.await(5, TimeUnit.SECONDS));

    HandlerThread misc = findThread("SystemUtils::HandlerThread - SEARCH_PERFORMER");
    assertNotNull(misc);
    misc.quitSafely();
    misc.join(5_000);

    CountDownLatch second = new CountDownLatch(1);
    // No delay: Robolectric's clock doesn't advance on background threads.
    SystemUtils.postToHandlerDelayed(
        SystemUtils.HandlerThreadName.SEARCH_PERFORMER, second::countDown, 0);
    assertTrue("the task was dropped", second.await(5, TimeUnit.SECONDS));
  }

  /**
   * A task that throws kills its HandlerThread without quitting the looper, so post() still says
   * yes and every later task on that name was silently dropped: one uncaught exception in a MISC
   * task stopped all MISC work for the rest of the process.
   */
  @Test
  public void aTaskPostedAfterTheHandlerThreadDiedStillRuns() throws Exception {
    CountDownLatch first = new CountDownLatch(1);
    SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, first::countDown);
    assertTrue(first.await(5, TimeUnit.SECONDS));
    HandlerThread thread = findThread("SystemUtils::HandlerThread - SEARCH_PERFORMER");
    assertNotNull(thread);
    Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
    thread.setUncaughtExceptionHandler((t, e) -> {}); // keep the test log quiet
    try {
      SystemUtils.postToHandler(
          SystemUtils.HandlerThreadName.SEARCH_PERFORMER,
          () -> {
            throw new IllegalStateException("a task that crashes its thread");
          });
      thread.join(5_000);
      assertTrue("the thread died", !thread.isAlive());

      CountDownLatch second = new CountDownLatch(1);
      SystemUtils.postToHandler(SystemUtils.HandlerThreadName.SEARCH_PERFORMER, second::countDown);
      assertTrue("the task was dropped", second.await(5, TimeUnit.SECONDS));
    } finally {
      thread.setUncaughtExceptionHandler(previous);
    }
  }

  private static HandlerThread findThread(String name) {
    for (Thread t : Thread.getAllStackTraces().keySet()) {
      if (name.equals(t.getName()) && t instanceof HandlerThread && t.isAlive()) {
        return (HandlerThread) t;
      }
    }
    return null;
  }
}
