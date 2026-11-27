/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.tables.SizeHolder;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** What a search result row shows in each column, and how it ranks. */
class SearchResultDataLineTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  private static StubUISearchResult mp3(String name, int seeds) {
    return new StubUISearchResult(name, "mp3", 4_000_000L, seeds, "Archive", "free song", 0L);
  }

  @Test
  void eachColumnShowsItsValue() {
    StubUISearchResult r =
        new StubUISearchResult(
            "free song.mp3", "mp3", 4_000_000L, 12, "Archive", "free song", 1000L);
    SearchResultDataLine line = r.line();
    assertSame(r, line.getSearchResult());
    assertEquals(12, line.getValueAt(SearchTableColumns.COUNT_IDX));
    assertEquals("free song.mp3", line.getValueAt(SearchTableColumns.NAME_IDX).toString());
    assertEquals(new SizeHolder(4_000_000L), line.getValueAt(SearchTableColumns.SIZE_IDX));
    assertEquals(
        "Archive", ((SourceHolder) line.getValueAt(SearchTableColumns.SOURCE_IDX)).getSourceName());
    assertEquals(new Date(1000L), line.getValueAt(SearchTableColumns.ADDED_IDX));
    assertEquals("mp3", line.getValueAt(SearchTableColumns.EXTENSION_IDX));
    assertNotNull(line.getValueAt(SearchTableColumns.ACTIONS_IDX));
    assertNull(line.getValueAt(SearchTableColumns.TYPE_IDX), "headless has no icons");
    assertNull(line.getValueAt(SearchTableColumns.KARMA_IDX), "not a distributed result");
    assertNull(line.getValueAt(99));
  }

  @Test
  void unknownCreationTimeShowsNoDate() {
    assertNull(mp3("a.mp3", 1).line().getValueAt(SearchTableColumns.ADDED_IDX));
  }

  @Test
  void seedsShowOnlyWhenPositiveAndNotPreliminary() {
    assertNull(mp3("a.mp3", 0).line().getSeedsAsInteger());
    assertNull(mp3("a.mp3", -4).line().getSeedsAsInteger());
    assertEquals(7, mp3("a.mp3", 7).line().getSeedsAsInteger());
    StubUISearchResult preliminary = mp3("a.mp3", 7);
    preliminary.preliminary = true;
    SearchResultDataLine line = preliminary.line();
    assertNull(line.getSeedsAsInteger());
    assertEquals(7, line.getSeeds());
  }

  @Test
  void lineDelegatesBasicAttributesToTheResult() {
    SearchResultDataLine line = mp3("Some Song.mp3", 3).line();
    assertEquals("Some Song.mp3", line.getFilename());
    assertEquals("Some Song.mp3", line.getDisplayName());
    assertEquals(4_000_000L, line.getSize());
    assertEquals("mp3", line.getExtension());
    assertEquals("Some Song.mp3", line.getHash());
    assertNull(line.getSearchEngine());
    assertEquals("Some Song.mp3 (3)", line.toString());
    assertEquals(NamedMediaType.getFromExtension("mp3"), line.getNamedMediaType());
  }

  @Test
  void columnShapeComesFromTheSearchColumns() {
    SearchResultDataLine line = mp3("a.mp3", 1).line();
    assertEquals(SearchTableColumns.COLUMN_COUNT, line.getColumnCount());
    assertNotNull(line.getColumn(SearchTableColumns.NAME_IDX));
    assertEquals(SearchTableColumns.NAME_IDX, line.getTypeAheadColumn());
    assertFalse(line.isDynamic(SearchTableColumns.COUNT_IDX));
    assertFalse(line.isClippable(SearchTableColumns.COUNT_IDX));
    assertFalse(line.isClippable(SearchTableColumns.TYPE_IDX));
    assertTrue(line.isClippable(SearchTableColumns.NAME_IDX));
  }

  @Test
  void karmaColumnHasNoTooltipForNonDistributedResults() {
    SearchResultDataLine line = mp3("a.mp3", 1).line();
    assertFalse(line.hasKarma());
    assertNull(line.getKarma());
    assertNull(line.getToolTipArray(SearchTableColumns.KARMA_IDX));
    assertFalse(line.isTooltipRequired(SearchTableColumns.KARMA_IDX));
    assertFalse(line.isTooltipRequired(SearchTableColumns.NAME_IDX));
  }

  @Test
  void resultsOfTheSameClassAreTheSameKind() {
    assertTrue(mp3("a.mp3", 1).line().isSameKindAs(mp3("b.mp3", 2).line()));
  }

  @Test
  void relevanceRewardsMatchingTheQueryAndSeeds() {
    SearchResultDataLine exact =
        new StubUISearchResult("free song", "mp3", 1, 100, "s", "free song", 0L).line();
    SearchResultDataLine unrelated =
        new StubUISearchResult("zzzz qqqq", "mp3", 1, 100, "s", "free song", 0L).line();
    SearchResultDataLine exactNoSeeds =
        new StubUISearchResult("free song", "mp3", 1, 0, "s", "free song", 0L).line();
    assertTrue(exact.getRelevanceScore() > unrelated.getRelevanceScore());
    assertTrue(exact.getRelevanceScore() > exactNoSeeds.getRelevanceScore());
    for (SearchResultDataLine l : new SearchResultDataLine[] {exact, unrelated, exactNoSeeds}) {
      assertTrue(l.getRelevanceScore() >= 0.0 && l.getRelevanceScore() <= 1.0);
    }
  }

  @Test
  void updateRecomputesRelevanceWithoutChangingIt() {
    SearchResultDataLine line = mp3("free song", 20).line();
    double before = line.getRelevanceScore();
    line.update();
    assertEquals(before, line.getRelevanceScore(), 0.0);
  }

  @Test
  void anEmptyQueryStillScoresWithinRange() {
    SearchResultDataLine line =
        new StubUISearchResult("something", "mp3", 1, 5, "s", "", 0L).line();
    assertTrue(line.getRelevanceScore() >= 0.0 && line.getRelevanceScore() <= 1.0);
  }
}
