/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class RelayStartupTrackerTest {
  @Test
  public void timeoutRevokesAttemptButCannotRetryBeforeWorkerFinishesCleanup() {
    AtomicLong now = new AtomicLong();
    RelayStartupTracker tracker = new RelayStartupTracker(now::get);
    long attempt = tracker.begin(false);
    tracker.phase(attempt, RelayStartupTracker.Phase.SERVER);
    assertEquals(90, tracker.snapshot().seconds());
    assertEquals(0, tracker.begin(false));
    now.set(90_000);
    assertFalse(tracker.permitted(attempt));
    assertFalse(tracker.accept(attempt));
    assertTrue(tracker.busy());
    assertEquals(RelayStartupTracker.State.DRAINING, tracker.snapshot().state());
    assertFalse(tracker.retryDue());
    assertEquals(0, tracker.begin(true));
    assertFalse(tracker.finish(attempt, true)); // A late success must never be adopted.
    assertEquals(RelayStartupTracker.State.RETRY_WAIT, tracker.snapshot().state());
    assertEquals(5, tracker.snapshot().seconds());
    now.set(95_000);
    assertTrue(tracker.retryDue());
    assertTrue(tracker.begin(false) > attempt);
  }

  @Test
  public void healthyCompletionIsIdempotentAndDoesNotAllowAnOverlappingAutomaticStart() {
    RelayStartupTracker tracker = new RelayStartupTracker(() -> 0);
    long attempt = tracker.begin(false);
    assertTrue(tracker.accept(attempt));
    assertEquals(0, tracker.begin(false));
    assertTrue(tracker.finish(attempt, true));
    assertTrue(tracker.permitted(attempt));
    assertEquals(RelayStartupTracker.State.RUNNING, tracker.snapshot().state());
    assertFalse(tracker.retryDue());
  }

  @Test
  public void failuresBackOffAndStopAfterThreeAttemptsUntilManualRetry() {
    AtomicLong now = new AtomicLong();
    RelayStartupTracker tracker = new RelayStartupTracker(now::get);
    for (int i = 1; i <= 3; i++) {
      long attempt = tracker.begin(false);
      assertTrue(attempt > 0);
      tracker.failed(attempt, new IllegalStateException("private detail must not be displayed"));
      tracker.finish(attempt, false);
      assertEquals("IllegalStateException", tracker.snapshot().failure());
      if (i < 3) {
        assertEquals(i * 5, tracker.snapshot().seconds());
        assertEquals(0, tracker.begin(false));
        now.addAndGet(i * 5_000);
      }
    }
    assertEquals(RelayStartupTracker.State.FAILED, tracker.snapshot().state());
    assertEquals(0, tracker.begin(false));
    assertTrue(tracker.begin(true) > 0);
  }

  @Test
  public void cancellationAndOldCompletionCannotOverwriteReplacement() {
    RelayStartupTracker tracker = new RelayStartupTracker(() -> 0);
    long old = tracker.begin(false);
    tracker.cancel();
    assertFalse(tracker.permitted(old));
    long next = tracker.begin(false);
    tracker.phase(next, RelayStartupTracker.Phase.IDENTITY);
    tracker.phase(old, RelayStartupTracker.Phase.SERVER);
    tracker.finish(old, true);
    assertEquals(RelayStartupTracker.Phase.IDENTITY, tracker.snapshot().phase());
    assertEquals(RelayStartupTracker.State.STARTING, tracker.snapshot().state());
    tracker.finish(next, true);
    assertEquals(RelayStartupTracker.State.RUNNING, tracker.snapshot().state());
    assertTrue(tracker.permitted(next));
    assertFalse(tracker.retryDue());
  }
}
