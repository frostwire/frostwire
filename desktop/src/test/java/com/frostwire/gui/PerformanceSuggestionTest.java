/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.PerformanceMode.Choice;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Full suggests Lite once when the UI keeps freezing (frostwire/frostwire#1555). */
class PerformanceSuggestionTest {
  private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);

  private final AtomicLong uptime = new AtomicLong(10 * MINUTE);
  private final AtomicInteger shown = new AtomicInteger();
  private boolean dismissed;
  private final PerformanceSuggestion suggestion =
      new PerformanceSuggestion(uptime::get, () -> dismissed, shown::incrementAndGet);

  @AfterEach
  void backToFull() {
    PerformanceMode.resolve(Choice.FULL, null);
  }

  @Test
  void thresholds() {
    assertEquals(3, PerformanceSuggestion.STALLS);
    assertEquals(TimeUnit.HOURS.toMillis(1), PerformanceSuggestion.WINDOW_MS);
    assertEquals(2 * MINUTE, PerformanceSuggestion.STARTUP_GRACE_MS);
  }

  @Test
  void threeStallsWithinAnHourSuggestLiteOnce() {
    stallsMinutesApart(2, 20);
    assertEquals(0, shown.get());
    stallsMinutesApart(1, 20);
    assertEquals(1, shown.get());
    stallsMinutesApart(5, 1);
    assertEquals(1, shown.get(), "suggested more than once a session");
  }

  @Test
  void stallsSpreadOverMoreThanAnHourDoNot() {
    stallsMinutesApart(6, 31);
    assertEquals(0, shown.get());
  }

  @Test
  void startupStallsDoNotCount() {
    uptime.set(0);
    stallsMinutesApart(2, 0);
    uptime.set(MINUTE);
    suggestion.onStall();
    assertEquals(0, shown.get());
  }

  @Test
  void liteNeverSuggests() {
    PerformanceMode.resolve(Choice.LITE, null);
    stallsMinutesApart(5, 1);
    assertEquals(0, shown.get());
  }

  @Test
  void aDismissedSuggestionNeverComesBack() {
    dismissed = true;
    stallsMinutesApart(5, 1);
    assertEquals(0, shown.get());
  }

  @Test
  void everyRecordedStallIsCounted() throws Exception {
    String spooler =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve("src/main/java/com/limegroup/gnutella/gui/CrashReportSpooler.java"))
            .replaceAll("\\s+", "");
    assertEquals(
        spooler.split("LivePresenceHeartbeat\\.stallRecorded\\(\\);", -1).length - 1,
        spooler.split("PerformanceSuggestion\\.stallRecorded\\(\\);", -1).length - 1,
        "a stall the heartbeat counts doesn't reach the suggestion");
    assertTrue(spooler.contains("PerformanceSuggestion.stallRecorded();"));
  }

  private void stallsMinutesApart(int stalls, int minutes) {
    for (int i = 0; i < stalls; i++) {
      uptime.addAndGet(minutes * MINUTE);
      suggestion.onStall();
    }
  }
}
