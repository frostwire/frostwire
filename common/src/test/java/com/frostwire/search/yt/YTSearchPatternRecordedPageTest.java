/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.yt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.FileSearchResult;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Parses a recorded YouTube search page, so CI doesn't depend on YouTube answering a GitHub runner
 * (GitHub #1440). The fixture is a real "frostwire" search from 2026-11-01, trimmed to its first
 * four video entries, with its tracking blobs replaced by "fixture". The live check stays in
 * {@code com.frostwire.tests.YTSearchPatternTest}, tagged {@code live}.
 */
class YTSearchPatternRecordedPageTest {

  private static String recordedPage() throws Exception {
    try (InputStream in =
        YTSearchPatternRecordedPageTest.class.getResourceAsStream("youtube-search-frostwire.html")) {
      assertNotNull(in, "fixture youtube-search-frostwire.html");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void parsesEveryVideoOfARecordedSearchPage() throws Exception {
    List<FileSearchResult> results = new YTSearchPattern().parseResults(recordedPage());

    assertEquals(4, results.size());
    assertEquals(
        List.of(
            "Is FrostWire Legal?",
            "Is FrostWire Safe?",
            "Frostwire Review and Reccomended Settings",
            "How to search and download files with FrostWire Wynwood (5.6)"),
        results.stream().map(FileSearchResult::getDisplayName).toList());
    assertEquals("https://www.youtube.com/watch?v=ye2CLllRO8I", results.get(0).getDetailsUrl());
    for (FileSearchResult result : results) {
      assertEquals("YT", result.getSource());
      assertTrue(result.getDetailsUrl().startsWith("https://www.youtube.com/watch?v="));
      assertTrue(result.getFilename().endsWith(".mp4"));
      assertFalse(result.getThumbnailUrl().isEmpty(), "thumbnail of " + result.getDisplayName());
      assertTrue(result.getSize() > 0, "size estimated from the duration");
      assertTrue(result.getCreationTime() > 0, "creation time from the published age");
    }
  }
}
