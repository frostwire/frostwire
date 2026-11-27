/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import org.junit.jupiter.api.Test;

/** JUnit coverage for DateParser (the sibling DateParserTest is a main-method program). */
final class DateParserBehaviorTest {

  private static long local(int y, int mo, int d, int h, int mi, int s) {
    Calendar c = Calendar.getInstance();
    c.clear();
    c.set(y, mo - 1, d, h, mi, s);
    return c.getTimeInMillis();
  }

  private static boolean nearNow(long t) {
    return Math.abs(System.currentTimeMillis() - t) < 60_000;
  }

  @Test
  void torrentDatesAcceptEveryKnownFormat() {
    assertEquals(local(2024, 3, 5, 14, 30, 15), DateParser.parseTorrentDate("2024-03-05 14:30:15"));
    assertEquals(
        local(2024, 3, 5, 14, 30, 15), DateParser.parseTorrentDate("2024-03-05T14:30:15Z"));
    assertEquals(
        local(2024, 3, 5, 14, 30, 15) + 123,
        DateParser.parseTorrentDate("2024-03-05T14:30:15.123Z"));
    assertEquals(local(2024, 3, 5, 0, 0, 0), DateParser.parseTorrentDate("2024-03-05"));
    assertEquals(local(2024, 3, 25, 0, 0, 0), DateParser.parseTorrentDate("25/03/2024"));
    assertEquals(local(2024, 3, 25, 0, 0, 0), DateParser.parseTorrentDate("03/25/2024"));
    assertEquals(local(2024, 3, 5, 0, 0, 0), DateParser.parseTorrentDate("2024/03/05"));
    assertEquals(local(2024, 3, 5, 0, 0, 0), DateParser.parseTorrentDate("  2024-03-05  "));
    assertTrue(DateParser.parseTorrentDate("2024/03/05 14:30:15 +0000") > 0);
  }

  @Test
  void torrentDatesFallBackToNow() {
    assertTrue(nearNow(DateParser.parseTorrentDate(null)));
    assertTrue(nearNow(DateParser.parseTorrentDate("  ")));
    assertTrue(nearNow(DateParser.parseTorrentDate("not a date")));
    assertTrue(nearNow(DateParser.parseTorrentDate("2024-13-45"))); // strict parsing
  }

  @Test
  void isoDatesWithAndWithoutMillis() {
    assertEquals(local(2020, 1, 2, 3, 4, 5), DateParser.parseIsoDate("2020-01-02T03:04:05Z"));
    assertEquals(
        local(2020, 1, 2, 3, 4, 5) + 7, DateParser.parseIsoDate("2020-01-02T03:04:05.007Z"));
    assertEquals(-1, DateParser.parseIsoDate("garbage"));
    assertEquals(-1, DateParser.parseIsoDate(null));
    assertEquals(-1, DateParser.parseIsoDate(" "));
  }

  @Test
  void simpleDates() {
    assertEquals(local(1999, 12, 31, 0, 0, 0), DateParser.parseSimpleDate("1999-12-31"));
    assertTrue(nearNow(DateParser.parseSimpleDate("31-12-1999")));
    assertTrue(nearNow(DateParser.parseSimpleDate(null)));
    assertTrue(nearNow(DateParser.parseSimpleDate("")));
  }

  @Test
  void relativeAges() {
    long hour = 60L * 60 * 1000;
    long day = 24 * hour;
    assertNear(System.currentTimeMillis() - 3 * hour, DateParser.parseRelativeAge("3 hours ago"));
    assertNear(
        System.currentTimeMillis() - 5 * 60_000, DateParser.parseRelativeAge("5 minutes ago"));
    assertNear(System.currentTimeMillis() - 3 * day, DateParser.parseRelativeAge("3 days ago"));
    assertNear(System.currentTimeMillis() - 14 * day, DateParser.parseRelativeAge("2 weeks ago"));
    assertNear(System.currentTimeMillis() - 62 * day, DateParser.parseRelativeAge("2 months ago"));
    assertNear(System.currentTimeMillis() - 730 * day, DateParser.parseRelativeAge("2 years ago"));
    assertNear(System.currentTimeMillis() - day, DateParser.parseRelativeAge("Yesterday"));
    assertNear(System.currentTimeMillis() - 365 * day, DateParser.parseRelativeAge("last year"));
    assertNear(System.currentTimeMillis() - 365 * day, DateParser.parseRelativeAge("1 year+"));
    assertNear(System.currentTimeMillis() - 31 * day, DateParser.parseRelativeAge("last month"));
    // a plain date falls through to the simple parser
    assertEquals(local(2001, 2, 3, 0, 0, 0), DateParser.parseRelativeAge("2001-02-03"));
    assertTrue(nearNow(DateParser.parseRelativeAge(null)));
    assertTrue(nearNow(DateParser.parseRelativeAge(" ")));
    assertTrue(nearNow(DateParser.parseRelativeAge("x days ago")));
    assertTrue(nearNow(DateParser.parseRelativeAge("5 parsecs ago")));
  }

  private static void assertNear(long expected, long actual) {
    assertTrue(Math.abs(expected - actual) < 60_000, expected + " vs " + actual);
  }

  @Test
  void refHelpersWrapAndClear() {
    Object o = new Object();
    assertEquals(o, Ref.strong(o));
    WeakReference<Object> weak = Ref.weak(o);
    SoftReference<Object> soft = Ref.soft(o);
    PhantomReference<Object> phantom = Ref.phantom(o, new ReferenceQueue<>());
    assertTrue(Ref.alive(weak));
    assertTrue(Ref.alive(soft));
    assertTrue(!Ref.alive(phantom), "phantom references never expose their referent");
    assertTrue(!Ref.alive(null));
    Reference<Object> ref = soft;
    Ref.free(ref);
    assertTrue(!Ref.alive(soft));
    Ref.free(null);
    assertTrue(Ref.alive(weak));
  }
}
