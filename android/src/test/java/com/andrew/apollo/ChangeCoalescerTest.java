/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * The playback service throttled its change notifications by dropping every one after the first in
 * each interval (GitHub #1453): pressing next twice quickly left the screen, widget and lock screen
 * on the first song, and a second queue edit within a second was never saved. Repeated
 * notifications now collapse into one that runs when the interval ends.
 */
public class ChangeCoalescerTest {

  private long now;
  private final List<Long> delays = new ArrayList<>();
  private final List<Runnable> scheduled = new ArrayList<>();
  private int runs;
  private boolean schedulerUp = true;

  private final ChangeCoalescer coalescer =
      new ChangeCoalescer(
          (task, delay) -> {
            if (!schedulerUp) {
              return false;
            }
            scheduled.add(task);
            delays.add(delay);
            return true;
          },
          () -> now);

  private void submit() {
    coalescer.submit("queue", 1000, () -> runs++);
  }

  private void runScheduled(int index, long at) {
    now = at;
    scheduled.get(index).run();
  }

  @Test
  public void theFirstChangeGoesOutAtOnce() {
    submit();

    assertEquals(List.of(0L), delays);
    runScheduled(0, 0);
    assertEquals(1, runs);
  }

  @Test
  public void changesWithinTheIntervalCollapseIntoOneAtItsEnd() {
    submit();
    runScheduled(0, 0);

    now = 10;
    submit();
    now = 20;
    submit();

    assertEquals("one more, when the interval ends", List.of(0L, 990L), delays);
    runScheduled(1, 1000);
    assertEquals("the latest change is not lost", 2, runs);
  }

  @Test
  public void aChangeSubmittedBeforeTheLastOneRanIsNotScheduledTwice() {
    submit();
    submit();

    assertEquals(1, scheduled.size());
  }

  @Test
  public void aChangeThatCouldNotBeScheduledDoesNotBlockTheNextOne() {
    schedulerUp = false;
    submit();
    schedulerUp = true;
    submit();

    assertEquals(List.of(0L), delays);
  }

  @Test
  public void eachKindOfChangeHasItsOwnInterval() {
    submit();
    coalescer.submit("meta", 150, () -> {});

    assertEquals(List.of(0L, 0L), delays);
  }
}
