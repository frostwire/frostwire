/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.internetarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.FileSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Internet Archive advanced-search reader. A {@code null} among {@code response.docs} threw a
 * NullPointerException outside the per-item guard, so the catch around the whole response dropped
 * every result of the page. Found by {@code InternetArchiveSearchPatternFuzzTest}. The other docs
 * below are the shapes archive.org sends, which must keep decoding to the same results.
 */
class InternetArchiveSearchPatternTest {

  private static final String DOC_A =
      "{\"identifier\":\"twil-217\",\"title\":\"This Week in Linux 217\","
          + "\"collection\":[\"podcasts_mirror\"],\"mediatype\":\"audio\",\"downloads\":1}";
  private static final String DOC_B =
      "{\"identifier\":\"atn-54\",\"title\":[\"Angry Tech News #54\",\"alt\"],"
          + "\"collection\":[\"podcasts_mirror\"]}";

  private static List<FileSearchResult> parse(String... docs) {
    return new InternetArchiveSearchPattern()
        .parseResults(
            "{\"response\":{\"numFound\":2,\"start\":0,\"docs\":["
                + String.join(",", docs)
                + "]}}");
  }

  @Test
  void nullDocDoesNotDropTheRestOfThePage() {
    List<FileSearchResult> results = parse("null", DOC_A, "null", DOC_B);
    assertEquals(2, results.size());
    assertEquals("This Week in Linux 217", results.get(0).getDisplayName());
    assertEquals("Angry Tech News #54", results.get(1).getDisplayName());
  }

  @Test
  void docsDecodeToPreliminaryCrawlableResults() {
    List<FileSearchResult> results = parse(DOC_A, DOC_B);
    assertEquals(2, results.size());
    FileSearchResult a = results.get(0);
    assertEquals("twil-217", a.getFilename());
    assertEquals("https://archive.org/details/twil-217?output=json", a.getDetailsUrl());
    assertEquals(-1, a.getSize());
    assertTrue(a.isPreliminary());
    assertEquals("Angry Tech News #54", results.get(1).getDisplayName());
  }

  @Test
  void docsWithoutIdentifierOrFromSamplesOnlyAreSkipped() {
    List<FileSearchResult> results =
        parse(
            "{\"title\":\"no identifier\"}",
            "{\"identifier\":\"s\",\"title\":\"sample\",\"collection\":[\"samples_only\"]}",
            "{\"identifier\":\"untitled\"}");
    assertEquals(1, results.size());
    assertEquals("untitled", results.get(0).getDisplayName());
  }
}
