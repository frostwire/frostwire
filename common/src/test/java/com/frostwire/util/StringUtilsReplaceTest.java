/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** replace() is left-to-right and non-overlapping, and an empty search string changes nothing. */
class StringUtilsReplaceTest {
  @Test
  void overlappingMatchesAreReplacedLeftToRightWithoutOverlap() {
    assertEquals("ba", StringUtils.replace("aaa", "aa", "b"));
    assertEquals("bb", StringUtils.replace("aaaa", "aa", "b"));
    assertEquals("xxa", StringUtils.replace("aaaaa", "aa", "x"));
  }

  @Test
  void ordinaryReplacementsStillWork() {
    assertEquals("hello there world", StringUtils.replace("hello big world", "big", "there"));
    assertEquals("a-b-c", StringUtils.replace("a b c", " ", "-"));
    assertEquals("abc", StringUtils.replace("abc", "x", "y"));
    assertEquals("", StringUtils.replace("", "x", "y"));
    assertEquals("ac", StringUtils.replace("abc", "b", ""));
    assertEquals("yy", StringUtils.replace("xx", "x", "y"));
  }

  @Test
  void emptySearchStringReturnsTheInputAndTerminates() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(5), () -> assertEquals("abc", StringUtils.replace("abc", "", "-")));
    assertTimeoutPreemptively(
        Duration.ofSeconds(5), () -> assertEquals("", StringUtils.replace("", "", "-")));
  }
}
