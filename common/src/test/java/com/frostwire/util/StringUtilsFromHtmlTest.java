/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** fromHtml turns BR tags into line breaks and everything else into single-spaced text. */
class StringUtilsFromHtmlTest {
  private static final String NL = System.lineSeparator();

  @Test
  void brTagsBecomeLineBreaks() {
    assertEquals("one" + NL + "two", StringUtils.fromHtml("one<br>two"));
    assertEquals("one" + NL + "two", StringUtils.fromHtml("one<br/>two"));
    assertEquals("one" + NL + "two" + NL + "three", StringUtils.fromHtml("one<BR />two<br >three"));
  }

  @Test
  void spacesAroundABreakAreDropped() {
    assertEquals("one" + NL + "two", StringUtils.fromHtml("one <br>   two"));
  }

  @Test
  void consecutiveBreaksAreKept() {
    assertEquals("one" + NL + NL + "two", StringUtils.fromHtml("one<br><br>two"));
  }

  @Test
  void sourceNewlinesAreOrdinaryWhitespace() {
    assertEquals("one two" + NL + "three", StringUtils.fromHtml("<p>one\n   two<br>three</p>"));
  }

  @Test
  void otherWhitespaceStillCollapsesToOneSpace() {
    assertEquals("hello world", StringUtils.fromHtml("<p>hello</p>   <p> world</p>"));
    assertEquals("_a_ b", StringUtils.fromHtml("<i>a</i>\t\t  b"));
  }

  @Test
  void plainTextAndNullAreReturnedAsTheyAre() {
    String plain = "no markup  here";
    assertSame(plain, StringUtils.fromHtml(plain));
    assertNull(StringUtils.fromHtml(null));
  }
}
