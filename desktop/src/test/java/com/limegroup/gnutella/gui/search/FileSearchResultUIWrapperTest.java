/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.theme.SkinPopupMenu;
import com.frostwire.search.CompositeFileSearchResult;
import java.util.Base64;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;

class FileSearchResultUIWrapperTest {

  private static final String INFO_HASH = "0123456789abcdef0123456789abcdef01234567";
  private static final byte[] HOLDER = new byte[32];
  private static final String HOLDER_B64 =
      Base64.getUrlEncoder().withoutPadding().encodeToString(HOLDER);

  @Test
  void distributedResultUsesDetailsAndAlwaysOffersBrowseWhenHolderIsKnown() {
    String magnet = magnet("BACK 2 LIFE (audio).webm", "x.pe=192.0.2.4:6889");
    CompositeFileSearchResult result = result("Distributed", magnet);
    FileSearchResultUIWrapper wrapper = wrapper(result);
    SkinPopupMenu menu =
        (SkinPopupMenu) wrapper.createMenu(baseMenu(), new SearchResultDataLine[] {null}, null);

    assertTrue(hasItem(menu, "Distributed Result Details"));
    assertTrue(hasItem(menu, "Browse Shared Torrents"));
    assertFalse(hasItem(menu, "View in Distributed"));

    DistributedSearchResultDetailsWindow.Details details =
        DistributedSearchResultDetailsWindow.describe(result, "back 2 life", null);
    assertTrue(details.description.contains(INFO_HASH));
    assertTrue(details.description.contains("192.0.2.4:6889"));
    assertTrue(details.description.contains(magnet));
    assertArrayEquals(HOLDER, details.holderPub);
  }

  @Test
  void webSearchResultRetainsItsProviderPageAction() {
    FileSearchResultUIWrapper wrapper = wrapper(result("YouTube", magnet("video.webm", "")));
    SkinPopupMenu menu =
        (SkinPopupMenu) wrapper.createMenu(baseMenu(), new SearchResultDataLine[] {null}, null);

    assertTrue(hasItem(menu, "View in YouTube"));
    assertFalse(hasItem(menu, "Distributed Result Details"));
    assertFalse(hasItem(menu, "Browse Shared Torrents"));
  }

  private static CompositeFileSearchResult result(String source, String magnet) {
    return CompositeFileSearchResult.builder()
        .displayName("BACK 2 LIFE (audio).webm")
        .filename("BACK 2 LIFE (audio).webm")
        .size(1024)
        .detailsUrl(magnet)
        .source(source)
        .creationTime(1_700_000_000L)
        .torrent(magnet, INFO_HASH, 0, magnet)
        .build();
  }

  private static FileSearchResultUIWrapper wrapper(CompositeFileSearchResult result) {
    return new FileSearchResultUIWrapper(
        result,
        SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID),
        "back 2 life");
  }

  private static String magnet(String name, String extra) {
    return "magnet:?xt=urn:btih:"
        + INFO_HASH
        + "&dn="
        + name.replace(' ', '+')
        + (extra.isEmpty() ? "" : "&" + extra)
        + "&x.hp="
        + HOLDER_B64;
  }

  private static boolean hasItem(SkinPopupMenu menu, String text) {
    for (int i = 0; i < menu.getComponentCount(); i++) {
      if (menu.getComponent(i) instanceof JMenuItem
          && text.equals(((JMenuItem) menu.getComponent(i)).getText())) {
        return true;
      }
    }
    return false;
  }

  private static SkinPopupMenu baseMenu() {
    SkinPopupMenu menu = new SkinPopupMenu();
    menu.add(new JMenuItem("Base 1"));
    menu.add(new JMenuItem("Base 2"));
    menu.add(new JMenuItem("Base 3"));
    return menu;
  }
}
