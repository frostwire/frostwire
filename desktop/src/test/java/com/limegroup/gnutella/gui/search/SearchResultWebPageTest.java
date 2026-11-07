/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.frostwire.search.CompositeFileSearchResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A search result may have no details page: its source's parser found none. Clicking its Source
 * column (or a "details page" action) must then do nothing instead of throwing on the EDT
 * (frostwire/frostwire#1497).
 */
class SearchResultWebPageTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void aResultWithoutADetailsPageOpensNothing(String detailsUrl) {
    AbstractUISearchResult result = resultWithDetailsUrl(detailsUrl);

    assertDoesNotThrow(() -> result.showSearchResultWebPage(true));
    assertDoesNotThrow(() -> result.showSearchResultWebPage(false));
  }

  private static AbstractUISearchResult resultWithDetailsUrl(String detailsUrl) {
    CompositeFileSearchResult sr =
        CompositeFileSearchResult.builder()
            .displayName("song")
            .filename("song.mp3")
            .size(1)
            .detailsUrl(detailsUrl)
            .source("Test")
            .creationTime(0)
            .preliminary(false)
            .build();
    return new AbstractUISearchResult(sr, null, "song") {
      @Override
      public void download(boolean partial) {}

      @Override
      public String getHash() {
        return null;
      }

      @Override
      public int getSeeds() {
        return 0;
      }

      @Override
      public javax.swing.JPopupMenu createMenu(
          javax.swing.JPopupMenu popupMenu, SearchResultDataLine[] lines, SearchResultMediator rp) {
        return popupMenu;
      }
    };
  }
}
