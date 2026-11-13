/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Icebase reports say whether the startup warm-up had finished and how long it took
 * (frostwire/frostwire#1490), so a stall on a warmed-up path can be judged.
 */
class WarmUpProgressTest {
  private static final long MS = TimeUnit.MILLISECONDS.toNanos(1);

  @Test
  void nothingBeforeTheWarmUpStarts() {
    assertNull(new WarmUpProgress().snapshot(5 * MS));
  }

  @Test
  void runningUntilEveryThreadFinishes() {
    WarmUpProgress progress = new WarmUpProgress();
    progress.start(2, 1_000 * MS);
    assertEquals(new WarmUpProgress.Snapshot(false, 500), progress.snapshot(1_500 * MS));

    progress.stepFinished(3_000 * MS);
    assertEquals(new WarmUpProgress.Snapshot(false, 3_000), progress.snapshot(4_000 * MS));

    progress.stepFinished(7_000 * MS);
    // Done: the total time stays fixed however late the report is taken.
    assertEquals(new WarmUpProgress.Snapshot(true, 6_000), progress.snapshot(60_000 * MS));
  }

  @Test
  void aSecondStartDoesNotRestartTheClock() {
    WarmUpProgress progress = new WarmUpProgress();
    progress.start(1, 0);
    progress.start(1, 10_000 * MS);
    progress.stepFinished(2_000 * MS);
    assertEquals(new WarmUpProgress.Snapshot(true, 2_000), progress.snapshot(20_000 * MS));
  }

  @Test
  void bothWarmUpThreadsAreTracked() throws Exception {
    Path source = Path.of("src/main/java/com/frostwire/util/JdkWarmUp.java");
    if (!Files.isRegularFile(source)) {
      source = Path.of("desktop").resolve(source);
    }
    String code =
        new String(Files.readAllBytes(source), StandardCharsets.UTF_8).replaceAll("\\s+", "");
    assertTrue(code.contains("PROGRESS.start(2,System.nanoTime());"));
    assertTrue(code.contains("newThread(tracked(()->warmUp(earlyWindowText)),\"JdkWarmUp\")"));
    assertTrue(
        code.contains(
            "startAppClassPreload(SPLASH_SHOWN,SPLASH_WAIT_MS,tracked(JdkWarmUp::loadAppClasses))"));
  }
}
