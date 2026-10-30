/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Compatibility fixtures for the SizeParser fix (frostwire/frostwire#1360). The boundary is the
 * sign of the value and the size of the result: 0 and every size up to the largest one below 2^63
 * bytes must parse to the exact byte count, in each unit and spelling the search engines (1337x,
 * Torrentz2, Nyaa, TPB) show, whatever the default locale.
 */
class SizeParserCompatibilityTest {

  private static final long KB = 1024L;
  private static final long MB = KB * 1024;
  private static final long GB = MB * 1024;
  private static final long TB = GB * 1024;
  private static final long PB = TB * 1024;

  @Test
  void zeroIsZeroInEveryUnit() {
    assertEquals(0, SizeParser.parseSize("0 B"));
    assertEquals(0, SizeParser.parseSize("0 KB"));
    assertEquals(0, SizeParser.parseSize("0.0 GB"));
  }

  @Test
  void oneOfEachUnitBelowTerabytes() {
    assertEquals(1, SizeParser.parseSize("1 B"));
    assertEquals(KB, SizeParser.parseSize("1 KB"));
    assertEquals(MB, SizeParser.parseSize("1 MB"));
    assertEquals(GB, SizeParser.parseSize("1 GB"));
    assertEquals(KB, SizeParser.parseSize("1 KiB"));
    assertEquals(MB, SizeParser.parseSize("1 MiB"));
    assertEquals(GB, SizeParser.parseSize("1 GiB"));
  }

  /** TB and PB came out as 0 before the fix (int overflow in the constants). */
  @Test
  void oneOfEachUnitFromTerabytes() {
    assertEquals(TB, SizeParser.parseSize("1 TB"));
    assertEquals(PB, SizeParser.parseSize("1 PB"));
    assertEquals(TB, SizeParser.parseSize("1 TiB"));
    assertEquals(PB, SizeParser.parseSize("1 PiB"));
  }

  @Test
  void decimalsAreTruncatedToWholeBytes() {
    assertEquals(1536, SizeParser.parseSize("1.5 KB"));
    assertEquals(524288, SizeParser.parseSize("0.5 MB"));
    assertEquals(1320702443L, SizeParser.parseSize("1.23 GB"));
    assertEquals(5046586572L, SizeParser.parseSize("4.7 GB"));
    assertEquals(734003200L, SizeParser.parseSize("700 MB"));
    assertEquals(1, SizeParser.parseSize("1.9 B"));
  }

  @Test
  void spellingsAndSeparatorsTheEnginesShow() {
    assertEquals(1610612736L, SizeParser.parseSize("1.5 gib"));
    assertEquals(1610612736L, SizeParser.parseSize("1.5 gb"));
    assertEquals(1572864, SizeParser.parseSize("  1.5   MB  "));
    assertEquals(1572864, SizeParser.parseSize("1.5\tMB"));
    assertEquals(1572864, SizeParser.parseSize("1.5" + (char) 160 + "MiB"));
    // TPB maps its own unit names and calls convertToBytes.
    assertEquals(1610612736L, SizeParser.convertToBytes(1.5, "GB"));
    assertEquals(0, SizeParser.convertToBytes(0, "B"));
  }

  /**
   * Nyaa's spelling of B. Accepted since the fix, which made Nyaa use SizeParser: SizeParser didn't
   * know it before, Nyaa's own parser did.
   */
  @Test
  void nyaasBytesSpelling() {
    assertEquals(0, SizeParser.parseSize("0 bytes"));
    assertEquals(945, SizeParser.parseSize("945 bytes"));
    assertEquals(945, SizeParser.parseSize("945 Bytes"));
    Locale saved = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertEquals(945, SizeParser.parseSize("945 bytes"));
    } finally {
      Locale.setDefault(saved);
    }
  }

  @Test
  void htmlNonBreakingSpaceSeparatorFromTheJavadoc() {
    assertEquals(1320702443L, SizeParser.parseSize("1.23&nbsp;GB", "&nbsp;"));
    assertEquals(1572864, SizeParser.parseSize("1.5&nbsp;MB", "&nbsp;"));
  }

  /** Came out as 0 before the fix (TB and PB were 0). */
  @Test
  void largestSizesBelowTwoToTheSixtyThirdParseExactly() {
    assertEquals(8191 * PB, SizeParser.parseSize("8191 PB"));
    assertEquals(8388607 * TB, SizeParser.parseSize("8388607 TB"));
    // The largest double below 2^63.
    assertEquals(9223372036854774784L, SizeParser.parseSize("9223372036854774784 B"));
  }

  @Test
  void unitsWithoutAnIParseInAnyDefaultLocale() {
    Locale saved = Locale.getDefault();
    try {
      for (String tag : new String[] {"de-DE", "fr-FR", "tr-TR", "ar-EG"}) {
        Locale.setDefault(Locale.forLanguageTag(tag));
        assertEquals(1610612736L, SizeParser.parseSize("1.5 GB"), tag);
        assertEquals(KB, SizeParser.parseSize("1 KB"), tag);
      }
    } finally {
      Locale.setDefault(saved);
    }
  }

  @Test
  void binaryUnitsParseInTheTurkishLocale() {
    Locale saved = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertEquals(KB, SizeParser.parseSize("1 KiB"));
      assertEquals(1610612736L, SizeParser.parseSize("1.5 GiB"));
      assertEquals(TB, SizeParser.parseSize("1 TiB"));
    } finally {
      Locale.setDefault(saved);
    }
  }

  /** Rejected with -1, before and after the fix. */
  @Test
  void unparseableSizesAreMinusOne() {
    assertEquals(-1, SizeParser.parseSize(null));
    assertEquals(-1, SizeParser.parseSize(""));
    assertEquals(-1, SizeParser.parseSize("1,5 GB"));
    assertEquals(-1, SizeParser.parseSize("1.5GB"));
    assertEquals(-1, SizeParser.parseSize("1.5 XB"));
  }

  /** Rejected with -1 since the fix: negative, not a number, or 2^63 bytes and more. */
  @Test
  void sizesOutsideTheRangeAreMinusOne() {
    assertEquals(-1, SizeParser.parseSize("-1 B"));
    assertEquals(-1, SizeParser.parseSize("-1.5 KB"));
    assertEquals(-1, SizeParser.parseSize("NaN GB"));
    assertEquals(-1, SizeParser.parseSize("Infinity B"));
    assertEquals(-1, SizeParser.parseSize("8192 PB"));
    assertEquals(-1, SizeParser.parseSize("8388608 TB"));
    // Long.MAX_VALUE rounds to 2^63 as a double (it used to be clamped back to Long.MAX_VALUE).
    assertEquals(-1, SizeParser.parseSize("9223372036854775807 B"));
    assertEquals(-1, SizeParser.convertToBytes(-1, "GB"));
  }
}
