/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Compatibility fixtures for the UrlUtils.decode fix (frostwire/frostwire#1359). The boundary the
 * crash input crossed is a {@code %} followed by fewer than two hex digits. Everything URLDecoder
 * accepted before the fix must still decode to the same string; only the stray {@code %} cases,
 * which used to throw, now decode to a literal {@code %}.
 */
class UrlUtilsDecodeCompatibilityTest {

  @Test
  void percentFollowedByTwoHexDigitsDecodesToThatByte() {
    assertEquals("A", UrlUtils.decode("%41"));
    assertEquals("abcA", UrlUtils.decode("abc%41"));
    assertEquals("Aabc", UrlUtils.decode("%41abc"));
    assertEquals("%", UrlUtils.decode("%25"));
    // Decoded once only: an escaped escape stays an escape.
    assertEquals("%25", UrlUtils.decode("%2525"));
    assertEquals("%41", UrlUtils.decode("%2541"));
    assertEquals("+", UrlUtils.decode("%2B"));
    assertEquals("+", UrlUtils.decode("%2b"));
    assertEquals("&=?#", UrlUtils.decode("%26%3D%3F%23"));
  }

  @Test
  void plusIsASpace() {
    assertEquals(" ", UrlUtils.decode("+"));
    assertEquals("a b c", UrlUtils.decode("a+b%20c"));
    assertEquals("a+b", UrlUtils.decode("a%2Bb"));
  }

  @Test
  void stringsWithoutEscapesComeBackUnchanged() {
    assertEquals("", UrlUtils.decode(""));
    assertEquals(
        "ubuntu-24.04-desktop-amd64.iso", UrlUtils.decode("ubuntu-24.04-desktop-amd64.iso"));
    assertEquals("ñandú 🎵", UrlUtils.decode("ñandú 🎵"));
  }

  @Test
  void validUtf8EscapesDecodeInUpperAndLowerCase() {
    assertEquals("ñ", UrlUtils.decode("%C3%B1"));
    assertEquals("ñ", UrlUtils.decode("%c3%b1"));
    assertEquals("€", UrlUtils.decode("%E2%82%AC"));
    assertEquals("🎵", UrlUtils.decode("%F0%9F%8E%B5"));
    assertEquals("�", UrlUtils.decode("%EF%BF%BD"));
    assertEquals(
        "Ubuntu 22.04 [x64] – 50%", UrlUtils.decode("Ubuntu+22.04+%5Bx64%5D+%E2%80%93+50%25"));
  }

  @Test
  void malformedUtf8DecodesToReplacementCharactersAsBefore() {
    assertEquals("�", UrlUtils.decode("%C3"));
    assertEquals("�", UrlUtils.decode("%FF"));
    assertEquals("�(", UrlUtils.decode("%C3%28"));
    assertEquals("��", UrlUtils.decode("%C0%AF"));
    assertEquals("�", UrlUtils.decode("%E2%82"));
    assertEquals("�", UrlUtils.decode("%F0%9F%8E"));
  }

  @Test
  void whatEncodeProducesDecodesBackToTheOriginal() {
    // ExternalControl forwards links with encode and the running instance decodes them.
    String[] originals = {
      "50% off", "a+b", "%41", "%", "x&y=z", "ñandú 🎵", "magnet:?xt=urn:btih:abc&dn=a%20b"
    };
    for (String original : originals) {
      assertEquals(original, UrlUtils.decode(UrlUtils.encode(original)));
    }
  }

  /**
   * Accepted since the fix (URLDecoder threw IllegalArgumentException for each): a {@code %}
   * followed by zero or one hex digit, or by a character that isn't an ASCII hex digit, is kept as
   * a literal {@code %}.
   */
  @Test
  void strayPercentsAtTheBoundaryAreKeptLiterally() {
    assertEquals("%", UrlUtils.decode("%"));
    assertEquals("%4", UrlUtils.decode("%4"));
    assertEquals("a%4", UrlUtils.decode("a%4"));
    assertEquals("%4z", UrlUtils.decode("%4z"));
    assertEquals("%z4", UrlUtils.decode("%z4"));
    assertEquals("%A", UrlUtils.decode("%%41"));
    assertEquals("%-1", UrlUtils.decode("%-1"));
    assertEquals("% 1", UrlUtils.decode("%+1"));
    // Fullwidth digits are digits to Character.digit, but not hex digits in a URL.
    assertEquals("%１１", UrlUtils.decode("%１１"));
  }
}
