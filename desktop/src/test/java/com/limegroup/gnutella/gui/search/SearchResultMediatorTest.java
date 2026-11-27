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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.engines.SearchEngineID;
import com.limegroup.gnutella.MediaType;
import java.util.List;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** One search's results table: the rows it collects, its identity, and when it stops. */
class SearchResultMediatorTest {

  private static LookAndFeel previous;

  @BeforeAll
  static void metal() throws Exception {
    System.setProperty("java.awt.headless", "true");
    previous = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new MetalLookAndFeel());
  }

  @AfterAll
  static void restore() throws Exception {
    UIManager.setLookAndFeel(previous);
  }

  private static SearchResultMediator mediator(long token, String query) throws Exception {
    SearchResultMediator[] holder = new SearchResultMediator[1];
    SwingUtilities.invokeAndWait(
        () ->
            holder[0] =
                new SearchResultMediator(
                    token,
                    List.of(query.split(" ")),
                    SearchInformation.createKeywordSearch(
                        query, null, MediaType.getAnyTypeMediaType())));
    return holder[0];
  }

  /** A result of an engine the engine filter always lets through. */
  private static StubUISearchResult result(String name, String source) {
    StubUISearchResult r = new StubUISearchResult(name, "mp3", 100, 5, source, "song", 0L);
    r.engine = DesktopSearchEngines.byId(SearchEngineID.YT_ID);
    return r;
  }

  private static void openAllFilters(SearchResultMediator m) {
    for (int depth = 0; depth < 4; depth++) {
      m.filterChanged(AllowFilter.instance(), depth);
    }
  }

  private static void onEdt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
  }

  @Test
  void aSearchPanelKnowsItsQueryTitleAndToken() throws Exception {
    SearchResultMediator m = mediator(77L, "free music");
    assertEquals("free music", m.getQuery());
    assertEquals("free music", m.getTitle());
    assertEquals(77L, m.getToken());
    assertTrue(m.matches(77L));
    assertFalse(m.matches(78L));
    assertEquals(List.of("free", "music"), m.getSearchTokens());
    assertNotNull(m.getComponent());
  }

  @Test
  void aNewPanelIsRunningUntilItIsStopped() throws Exception {
    SearchResultMediator m = mediator(1L, "a");
    assertFalse(m.isStopped());
    m.stop();
    assertTrue(m.isStopped());
    m.stop();
    assertTrue(m.isStopped());
  }

  @Test
  void resultsAreCollectedAndDuplicateHashesAreIgnored() throws Exception {
    SearchResultMediator m = mediator(2L, "song");
    onEdt(
        () -> {
          m.add(result("one.mp3", "Archive"));
          m.add(result("two.mp3", "TPB"));
          m.add(result("one.mp3", "Archive"));
          assertEquals(2, m.totalResults(), "shown or filtered out, each result counts once");
          openAllFilters(m);
          assertEquals(2, m.getSize(), "with every filter open both rows show");
          assertEquals(2, m.totalResults());
        });
  }

  @Test
  void sortsLiveEverywhereExceptWhileALiteSearchRuns() {
    assertTrue(SearchResultMediator.sortsLive(true, true));
    assertTrue(SearchResultMediator.sortsLive(false, false));
    assertTrue(SearchResultMediator.sortsLive(true, false));
    assertFalse(SearchResultMediator.sortsLive(false, true));
  }

  @Test
  void selectedLinesAreTheRowsSelectedInTheTable() throws Exception {
    SearchResultMediator m = mediator(3L, "song");
    onEdt(
        () -> {
          m.add(result("one.mp3", "Archive"));
          m.add(result("two.mp3", "TPB"));
          openAllFilters(m);
          assertEquals(0, m.getAllSelectedLines().length);
          m.setSelectedRow(0);
          SearchResultDataLine[] selected = m.getAllSelectedLines();
          assertEquals(1, selected.length);
          assertSame(m.getDataModel().get(0), selected[0]);
        });
  }
}
