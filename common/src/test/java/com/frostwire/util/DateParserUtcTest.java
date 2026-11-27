/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** A timestamp ending in 'Z' is UTC, whatever the default time zone of the JVM is. */
class DateParserUtcTest {
  // 2024-03-05T12:34:56Z
  private static final long EPOCH_MILLIS = 1709642096000L;

  /**
   * Runs on a fresh thread (the formatters are thread-local and take the default zone when they are
   * created) with a non-UTC default zone that is restored afterwards.
   */
  private static void underZone(String zoneId, Runnable body) throws InterruptedException {
    TimeZone saved = TimeZone.getDefault();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
      Thread t =
          new Thread(
              () -> {
                try {
                  body.run();
                } catch (Throwable e) {
                  failure.set(e);
                }
              });
      t.start();
      t.join();
    } finally {
      TimeZone.setDefault(saved);
    }
    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }
  }

  @Test
  void isoTimestampIsUtcInANonUtcZone() throws InterruptedException {
    underZone(
        "America/Denver",
        () -> assertEquals(EPOCH_MILLIS, DateParser.parseIsoDate("2024-03-05T12:34:56Z")));
  }

  @Test
  void isoTimestampWithMillisIsUtcInANonUtcZone() throws InterruptedException {
    underZone(
        "Asia/Kolkata",
        () ->
            assertEquals(EPOCH_MILLIS + 789, DateParser.parseIsoDate("2024-03-05T12:34:56.789Z")));
  }

  @Test
  void torrentDateParsesIsoTimestampAsUtcInANonUtcZone() throws InterruptedException {
    underZone(
        "Pacific/Auckland",
        () -> assertEquals(EPOCH_MILLIS, DateParser.parseTorrentDate("2024-03-05T12:34:56Z")));
  }

  @Test
  void isoTimestampIsUnchangedInUtc() throws InterruptedException {
    underZone(
        "UTC", () -> assertEquals(EPOCH_MILLIS, DateParser.parseIsoDate("2024-03-05T12:34:56Z")));
  }
}
