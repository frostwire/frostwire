/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

final class StringUtilsTest {

  @AfterAll
  static void restoreLocale() {
    StringUtils.setLocale(Locale.getDefault());
  }

  @Test
  void containsSupportsWildcardsAndMultipleTerms() {
    assertTrue(StringUtils.contains("", ""));
    assertTrue(StringUtils.contains("abc", ""));
    assertTrue(StringUtils.contains("abc", "b"));
    assertFalse(StringUtils.contains("abc", "d"));
    assertTrue(StringUtils.contains("abcd", "a*d"));
    assertTrue(StringUtils.contains("abcd", "*a**d*"));
    assertFalse(StringUtils.contains("abcd", "d*a"));
    // a space or '+' separates independent terms that may appear in any order
    assertTrue(StringUtils.contains("hello big world", "world hello"));
    assertTrue(StringUtils.contains("hello big world", "world+hello"));
    assertFalse(StringUtils.contains("hello big world", "world hullo"));
    assertFalse(StringUtils.contains("short", "much longer than the input"));
    // matching is case sensitive
    assertFalse(StringUtils.contains("ABC", "abc"));
  }

  @Test
  void splitReplaceTruncate() {
    assertArrayEquals(new String[] {"a", "b", "c"}, StringUtils.split("a,b,,c", ','));
    assertArrayEquals(new String[] {"a", "b", "c"}, StringUtils.split("a-b_c", "-_"));
    assertArrayEquals(new String[0], StringUtils.split("", ','));
    assertEquals("a-b-c", StringUtils.replace("a b c", " ", "-"));
    assertEquals("xbxdx", StringUtils.replace("abada", "a", "x"));
    assertEquals("abc", StringUtils.replace("abc", "zz", "q"));
    assertEquals("", StringUtils.replace("", "a", "b"));
    assertEquals("abc", StringUtils.truncate("abc", 5));
    assertEquals("ab", StringUtils.truncate("abc", 2));
    assertEquals("", StringUtils.truncate("abc", 0));
  }

  @Test
  void explodeAndEmptyChecks() {
    assertEquals("a,b,c", StringUtils.explode(new String[] {"a", "b", "c"}, ","));
    assertEquals("", StringUtils.explode(new String[0], ","));
    assertEquals("a", StringUtils.explode(new String[] {"a"}, ","));
    assertEquals("x; y", StringUtils.explode(Arrays.asList("x", "y"), "; "));
    assertEquals("", StringUtils.explode(Collections.<String>emptyList(), ","));
    assertTrue(StringUtils.isNullOrEmpty(null));
    assertTrue(StringUtils.isNullOrEmpty(""));
    assertFalse(StringUtils.isNullOrEmpty("  "));
    assertTrue(StringUtils.isNullOrEmpty("  ", true));
    assertFalse(StringUtils.isNullOrEmpty(" x ", true));
    assertEquals("a b c", StringUtils.removeDoubleSpaces("a   b \t\n c"));
    assertNull(StringUtils.removeDoubleSpaces(null));
  }

  @Test
  void utf8DecodingAndPrimaryCollation() {
    assertEquals("café", StringUtils.getUTF8String("café".getBytes(StandardCharsets.UTF_8)));
    StringUtils.setLocale(Locale.US);
    assertEquals(0, StringUtils.compareFullPrimary("cafe", "café"));
    assertEquals(0, StringUtils.compareFullPrimary("ABC", "abc"));
    assertTrue(StringUtils.compareFullPrimary("apple", "banana") < 0);
    assertTrue(StringUtils.compareFullPrimary("b", "a") > 0);
  }
}
