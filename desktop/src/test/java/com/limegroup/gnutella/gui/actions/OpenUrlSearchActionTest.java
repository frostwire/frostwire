/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.settings.SearchSettings;
import javax.swing.Action;
import org.junit.jupiter.api.Test;

/**
 * Icebase #6066eb0dd91b, frostwire/frostwire#1584: OK in File > Open Magnet, Torrent or Video URL
 * built a {@link SearchAction} for a web address, and the constructor throws for a query longer
 * than the search box allows, so a long address crashed the button instead of showing a message.
 */
class OpenUrlSearchActionTest {

  @Test
  void aNormalAddressGetsASearchAction() {
    SearchAction action =
        FileMenuActions.cloudUrlSearchAction("https://example.com/watch?v=abc123");
    assertNotNull(action);
    assertTrue(
        String.valueOf(action.getValue(Action.NAME))
            .contains("https://example.com/watch?v=abc123"));
  }

  @Test
  void anAddressLongerThanTheSearchBoxAllowsGetsNoActionInsteadOfAnException() {
    String longUrl =
        "https://example.com/?q=" + "a".repeat(SearchSettings.MAX_QUERY_LENGTH.getValue());
    assertNull(FileMenuActions.cloudUrlSearchAction(longUrl));
    String atTheLimit =
        "https://example.com/?q="
            + "a"
                .repeat(
                    SearchSettings.MAX_QUERY_LENGTH.getValue()
                        - "https://example.com/?q=".length());
    assertNotNull(FileMenuActions.cloudUrlSearchAction(atTheLimit));
  }

  @Test
  void bracesAndQuotesInTheAddressAreNotAMessageFormatPattern() {
    for (String url :
        new String[] {
          "https://example.com/{id}/x",
          "https://example.com/a'b",
          "https://example.com/{0,number}",
          "https://example.com/}{"
        }) {
      SearchAction action = FileMenuActions.cloudUrlSearchAction(url);
      assertNotNull(action, url);
      assertEquals(
          true, String.valueOf(action.getValue(Action.NAME)).contains(url), "title shows " + url);
    }
  }
}
