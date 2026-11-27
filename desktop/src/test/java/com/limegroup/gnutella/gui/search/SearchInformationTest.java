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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.settings.SearchSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** What a search box submission carries, and which submissions the search accepts. */
class SearchInformationTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  private static SearchInformation keywords(String query) {
    return SearchInformation.createKeywordSearch(query, null, MediaType.getTorrentMediaType());
  }

  @Test
  void aKeywordSearchKeepsTheTrimmedQueryAndUsesItAsTitle() {
    SearchInformation info = keywords("  creative commons  ");
    assertEquals("creative commons", info.getQuery());
    assertEquals("  creative commons  ", info.getTitle(), "the title is the query as typed");
    assertTrue(info.isKeywordSearch());
    assertFalse(info.isXMLSearch());
  }

  @Test
  void aTitledSearchKeepsItsOwnTitle() {
    SearchInformation info =
        SearchInformation.createTitledKeywordSearch(
            "ubuntu", "<xml/>", MediaType.getVideoMediaType(), "Ubuntu releases");
    assertEquals("Ubuntu releases", info.getTitle());
    assertTrue(info.isXMLSearch());
    assertTrue(info.toString().contains("Ubuntu releases"));
    assertTrue(info.toString().contains("ubuntu"));
  }

  @Test
  void emptyXmlIsNotAnXmlSearch() {
    assertFalse(
        SearchInformation.createKeywordSearch("a", "", MediaType.getTorrentMediaType())
            .isXMLSearch());
  }

  @Test
  void aSearchNeedsAQueryAndAMediaType() {
    assertThrows(NullPointerException.class, () -> keywords(null));
    assertThrows(
        NullPointerException.class, () -> SearchInformation.createKeywordSearch("a", null, null));
  }

  @Test
  void blankQueriesAreEmptyAndLongOnesAreRefused() {
    assertEquals(SearchMediator.QUERY_EMPTY, SearchMediator.validateInfo(keywords("")));
    assertEquals(SearchMediator.QUERY_EMPTY, SearchMediator.validateInfo(keywords("   ")));
    assertEquals(SearchMediator.QUERY_VALID, SearchMediator.validateInfo(keywords("a")));
    assertEquals(SearchMediator.QUERY_VALID, SearchMediator.validateInfo(keywords("free music")));
    int max = SearchSettings.MAX_QUERY_LENGTH.getValue();
    assertEquals(
        SearchMediator.QUERY_VALID, SearchMediator.validateInfo(keywords("x".repeat(max))));
    int tooLong = SearchMediator.validateInfo(keywords("x".repeat(max + 1)));
    assertTrue(
        tooLong != SearchMediator.QUERY_VALID && tooLong != SearchMediator.QUERY_EMPTY,
        "over the maximum query length: " + tooLong);
  }
}
