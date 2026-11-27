/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/** The engine's executor runs tasks and, in debug builds, refuses tasks that pin a Context. */
public class EngineThreadPoolTest {
  private EngineThreadPool pool;

  @Before
  public void setUp() {
    pool = new EngineThreadPool();
  }

  @After
  public void tearDown() {
    pool.shutdownNow();
  }

  @Test
  public void executeAndSubmitRunEveryKindOfTask() throws Exception {
    CountDownLatch latch = new CountDownLatch(4);
    pool.execute(latch::countDown);
    pool.submit((Runnable) latch::countDown).get(5, TimeUnit.SECONDS);
    assertEquals("done", pool.submit(latch::countDown, "done").get(5, TimeUnit.SECONDS));
    assertEquals(
        Integer.valueOf(7),
        pool.submit(
                () -> {
                  latch.countDown();
                  return 7;
                })
            .get(5, TimeUnit.SECONDS));
    assertTrue(latch.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void aTaskHoldingAContextIsRejectedAsAPossibleLeak() {
    Context context = Mockito.mock(Context.class);
    Runnable leaky = new LeakyTask(context);
    try {
      pool.execute(leaky);
      fail("a task that pins a Context must be rejected in debug builds");
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("context leak"));
    }
    try {
      pool.submit(leaky);
      fail("submit must reject it too");
    } catch (RuntimeException expected) {
      assertTrue(expected.getMessage().contains("context leak"));
    }
  }

  @Test
  public void tasksQueuedBehindARunningOneAreStillExecuted() throws Exception {
    CountDownLatch gate = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(3);
    pool.execute(
        () -> {
          try {
            gate.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException ignored) {
          }
          done.countDown();
        });
    pool.execute(done::countDown);
    pool.execute(done::countDown);
    gate.countDown();
    assertTrue(done.await(5, TimeUnit.SECONDS));
  }

  private static final class LeakyTask implements Runnable {
    private final Context context;

    LeakyTask(Context context) {
      this.context = context;
    }

    @Override
    public void run() {
      context.getPackageName();
    }
  }
}
