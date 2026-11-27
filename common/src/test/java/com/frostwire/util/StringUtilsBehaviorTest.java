/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** JUnit coverage for the string helpers that the main-method test in this package skips. */
final class StringUtilsBehaviorTest {

  @Test
  void containsWithWildcardsTermsAndCaseOption() {
    assertTrue(StringUtils.contains("abcd", "a*d"));
    assertFalse(StringUtils.contains("abcd", "d*a"));
    assertTrue(StringUtils.contains("hello big world", "world hello"));
    assertTrue(StringUtils.contains("hello big world", "world+hello"));
    assertFalse(StringUtils.contains("ABC", "abc"));
    assertTrue(StringUtils.contains("ABC", "abc", true));
    assertTrue(StringUtils.contains("Hello World", "hello*WORLD", true));
    assertFalse(StringUtils.contains("Hello World", "bye", true));
    assertTrue(StringUtils.contains("abc", ""));
    assertEquals('a', StringUtils.toOtherCase('A'));
    assertEquals('Z', StringUtils.toOtherCase('z'));
    assertEquals('1', StringUtils.toOtherCase('1'));
    assertEquals('[', StringUtils.toOtherCase('['));
    assertEquals('é', StringUtils.toOtherCase('é'));
  }

  @Test
  void splitVariants() {
    assertArrayEquals(new String[] {"a", "b", "c"}, StringUtils.split("a,,b,c,", ','));
    assertArrayEquals(new String[] {"a", "b"}, StringUtils.split("a;b", ";,"));
    assertArrayEquals(
        new String[] {"", "a", "", "b", ""}, StringUtils.splitNoCoalesce(",a,,b,", ','));
    assertArrayEquals(new String[] {"a", "b"}, StringUtils.splitNoCoalesce("a;b", ";,"));
    assertArrayEquals(new String[0], StringUtils.splitNoCoalesce("", ','));
    assertArrayEquals(new String[] {"", ""}, StringUtils.splitNoCoalesce(",", ','));
    assertArrayEquals(new String[] {"x"}, StringUtils.splitNoCoalesce("x", ','));
  }

  @Test
  void prefixAndSubstringSearchIgnoreCase() {
    assertTrue(StringUtils.startsWithIgnoreCase("FrostWire", "frost"));
    assertTrue(StringUtils.startsWithIgnoreCase("frostwire", "FROST"));
    assertTrue(StringUtils.startsWithIgnoreCase("abc", ""));
    assertFalse(StringUtils.startsWithIgnoreCase("ab", "abc"));
    assertFalse(StringUtils.startsWithIgnoreCase("frostwire", "frozt"));
    assertEquals(5, StringUtils.indexOfIgnoreCase("FrostWire", "WIRE"));
    assertEquals(-1, StringUtils.indexOfIgnoreCase("FrostWire", "zzz"));
    assertEquals(0, StringUtils.indexOfIgnoreCase("TITLE", "title", Locale.ENGLISH));
  }

  @Test
  void replaceTruncateAndEncodings() {
    assertEquals("a-b-c", StringUtils.replace("a b c", " ", "-"));
    assertEquals("abc", StringUtils.replace("abc", "zz", "q"));
    assertEquals("ab", StringUtils.truncate("abc", 2));
    assertEquals("abc", StringUtils.truncate("abc", 3));
    byte[] latin = {(byte) 0xe9};
    assertEquals("é", StringUtils.getASCIIString(latin));
    assertEquals("café", StringUtils.getUTF8String("café".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void explodeAndEmptiness() {
    assertEquals("a,b", StringUtils.explode(new String[] {"a", "b"}, ","));
    assertEquals("", StringUtils.explode(new String[0], ","));
    assertEquals("a|b", StringUtils.explode(Arrays.asList("a", "b"), "|"));
    assertEquals("", StringUtils.explode(Collections.<String>emptyList(), "|"));
    assertTrue(StringUtils.isNullOrEmpty(null));
    assertTrue(StringUtils.isNullOrEmpty(""));
    assertFalse(StringUtils.isNullOrEmpty(" "));
    assertTrue(StringUtils.isNullOrEmpty(" ", true));
  }

  @Test
  void unicodeAndWhitespaceCleaning() {
    assertEquals("a b", StringUtils.removeDoubleSpaces("a    b"));
    assertNull(StringUtils.removeDoubleSpaces(null));
    assertEquals("café 1!", StringUtils.removeUnicodeCharacters("café 1!"));
    // symbols and emoji are dropped, letters of any script are kept
    assertEquals("ab", StringUtils.removeUnicodeCharacters("a☃b😀"));
    assertEquals("中文", StringUtils.removeUnicodeCharacters("中文"));
    assertEquals("", StringUtils.removeUnicodeCharacters(""));
    assertNull(StringUtils.removeUnicodeCharacters(null));
  }

  @Test
  void localeStringsFallBackToTheDefault() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("es"));
      Map<String, String> strings = new HashMap<>();
      strings.put("en", "Hello");
      strings.put("es", "Hola");
      assertEquals("Hola", StringUtils.getLocaleString(strings, "fallback"));
      strings.remove("es");
      assertEquals("fallback", StringUtils.getLocaleString(strings, "fallback"));
      strings.put("es", "  ");
      assertEquals("fallback", StringUtils.getLocaleString(strings, "fallback"));
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  void quickHashIsStableAndSensitiveToContent() {
    assertEquals(1, StringUtils.quickHash(""));
    assertEquals(StringUtils.quickHash("frostwire"), StringUtils.quickHash("frostwire"));
    assertTrue(StringUtils.quickHash("frostwire") != StringUtils.quickHash("frostwirf"));
    assertTrue(StringUtils.quickHash("some rather long string to wrap around") >= 0);
  }

  @Test
  void fromHtmlStripsMarkupButKeepsEmphasis() {
    assertEquals("plain", StringUtils.fromHtml("plain"));
    assertNull(StringUtils.fromHtml(null));
    assertEquals("a b", StringUtils.fromHtml("<p>a&nbsp;b</p>"));
    assertEquals("_em_ and *strong*", StringUtils.fromHtml("<em>em</em> and <b>strong</b>"));
    assertEquals("keep", StringUtils.fromHtml("<!-- gone -->keep<script>alert(1)</script>"));
    assertEquals("x", StringUtils.fromHtml("<span style=\"color:red\">x</span>"));
    assertEquals("Tom & \"Jerry\"", StringUtils.fromHtml("<u>Tom</u> &amp; &quot;Jerry&quot;"));
  }
}
