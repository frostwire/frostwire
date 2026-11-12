/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * A restore announces its transfers in the order the file system lists their files. Waiting for
 * them one name at a time consumed the later one while waiting for the earlier one, so
 * BTDownloadTransferTest.restoringBringsEveryTransferBackToItsOwnFolder timed out on CI once.
 */
class EventsNextEachTest {
  private static final Function<String, String> NAME = s -> s;
  private static final long SHORT = TimeUnit.MILLISECONDS.toNanos(200);

  @Test
  void theNamesCanArriveInAnyOrder() {
    LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(List.of("second", "first"));

    Map<String, String> found = LocalEngine.Events.nextEach(queue, NAME, SHORT, "first", "second");

    assertEquals(List.of("second", "first"), List.copyOf(found.keySet()));
    assertTrue(queue.isEmpty());
  }

  @Test
  void otherDownloadsAreSkippedAndTheRestIsLeftInTheQueueOnlyAfterTheLastName() {
    LinkedBlockingQueue<String> queue =
        new LinkedBlockingQueue<>(List.of("other", "second", "first", "later"));

    Map<String, String> found = LocalEngine.Events.nextEach(queue, NAME, SHORT, "first", "second");

    assertEquals(2, found.size());
    assertEquals(List.of("later"), List.copyOf(queue), "events after the last wanted one stay");
  }

  @Test
  void aMissingNameTimesOutAndSaysWhatArrivedInstead() {
    LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(List.of("first", "other"));

    AssertionError error =
        assertThrows(
            AssertionError.class,
            () -> LocalEngine.Events.nextEach(queue, NAME, SHORT, "first", "second"));

    assertTrue(error.getMessage().contains("[second]"), error.getMessage());
    assertTrue(error.getMessage().contains("seen: [first]"), error.getMessage());
    assertTrue(error.getMessage().contains("skipped: [other]"), error.getMessage());
  }

  @Test
  void waitingForTheNamesOneByOneLosesTheLaterOne() {
    // The pattern the restore test used: the first wait consumes "second", and "second" never
    // comes again. This is why nextEach exists.
    LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(List.of("second", "first"));

    LocalEngine.Events.nextEach(queue, NAME, SHORT, "first");

    assertThrows(
        AssertionError.class, () -> LocalEngine.Events.nextEach(queue, NAME, SHORT, "second"));
  }
}
