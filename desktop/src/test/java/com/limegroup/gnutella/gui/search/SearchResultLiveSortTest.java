/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Lite doesn't re-rank the search results on every batch while a search runs; it sorts them once
 * when the search finishes. Full keeps sorting them as they arrive (frostwire/frostwire#1555).
 * SearchResultMediator needs the whole search UI, so the wiring is checked at source level.
 */
class SearchResultLiveSortTest {
  @Test
  void liteSortsOnlyOnceTheSearchFinishes() {
    assertFalse(SearchResultMediator.sortsLive(false, true), "Lite sorts while results arrive");
    assertTrue(SearchResultMediator.sortsLive(true, true), "Lite doesn't sort a finished search");
  }

  @Test
  void fullSortsResultsAsTheyArrive() {
    assertTrue(SearchResultMediator.sortsLive(false, false));
    assertTrue(SearchResultMediator.sortsLive(true, false));
  }

  @Test
  void addsAskTheTableWhetherToSortAndTheFinishedSearchSortsOnce() throws Exception {
    String table = source("com/limegroup/gnutella/gui/tables/AbstractTableMediator.java");
    assertTrue(
        table.contains("if(sortsWhileAdding()&&DATA_MODEL.isSorted()){addedAt=sorted.getAsInt();"),
        "adds don't ask sortsWhileAdding()");
    String search = source("com/limegroup/gnutella/gui/search/SearchResultMediator.java");
    assertTrue(
        search.contains(
            "returnsuper.sortsWhileAdding()&&sortsLive(isStopped(),PerformanceMode.isLite());"),
        "search results don't defer sorting in Lite");
    assertTrue(
        search.contains(
            "if(wasRunning&&PerformanceMode.isLite()){GUIMediator.safeInvokeLater(this::resortNow);"),
        "a finished Lite search isn't sorted");
    assertTrue(
        search.contains(
            "if(DATA_MODEL.ranksByKarma()&&sortsLive(isStopped(),PerformanceMode.isLite())){forceResort();"),
        "karma re-ranks a running Lite search");
  }

  private static String source(String path) throws Exception {
    return Files.readString(
            Path.of(System.getProperty("user.dir")).resolve("src/main/java").resolve(path))
        .replaceAll("//[^\\n]*", "")
        .replaceAll("\\s+", "");
  }
}
