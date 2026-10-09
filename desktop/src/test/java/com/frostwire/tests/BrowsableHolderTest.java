/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.frostwire.search.relay.DistributedSearchPerformer;
import com.limegroup.gnutella.gui.search.BrowsableHolder;
import java.awt.image.BufferedImage;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The browse action in the search results table only appears for holders that share a catalog. */
class BrowsableHolderTest {

  private static final byte[] HOLDER = new byte[32];

  static {
    HOLDER[0] = 5;
    HOLDER[31] = 9;
  }

  private static final String HOLDER_B64 =
      Base64.getUrlEncoder().withoutPadding().encodeToString(HOLDER);
  private static final String SOURCE = DistributedSearchPerformer.SOURCE_NAME;

  private static String magnet(String extra) {
    return "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=x&x.hp="
        + HOLDER_B64
        + extra;
  }

  @Test
  void aHolderThatSharesItsCatalogIsBrowsable() {
    assertArrayEquals(HOLDER, BrowsableHolder.advertisedPub(SOURCE, magnet("&x.hc=1")));
    assertArrayEquals(HOLDER, BrowsableHolder.advertisedPub(SOURCE, magnet("&x.hc=true&x.pe=1")));
  }

  @Test
  void aHolderThatDoesNotShareItsCatalogGetsNoAction() {
    assertNull(BrowsableHolder.advertisedPub(SOURCE, magnet("")));
    assertNull(BrowsableHolder.advertisedPub(SOURCE, magnet("&x.hc=0")));
    assertNull(BrowsableHolder.advertisedPub(SOURCE, magnet("&x.hc=maybe")));
  }

  @Test
  void onlyDistributedResultsWithAKnownHolderQualify() {
    assertNull(BrowsableHolder.advertisedPub("Bitsearch", magnet("&x.hc=1")));
    assertNull(BrowsableHolder.advertisedPub(null, magnet("&x.hc=1")));
    assertNull(
        BrowsableHolder.advertisedPub(
            SOURCE, "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&x.hc=1"));
    assertNull(
        BrowsableHolder.advertisedPub(SOURCE, magnet("&x.hc=1").replace(HOLDER_B64, "AAAA")));
    assertNull(
        BrowsableHolder.advertisedPub(SOURCE, "https://example.com/x.hc=1&x.hp=" + HOLDER_B64));
    assertNull(BrowsableHolder.advertisedPub(SOURCE, null));
    assertNull(BrowsableHolder.advertisedPub(SOURCE, ""));
  }

  @Test
  void theBrowseIconShipsInBothSizes() throws Exception {
    for (String name :
        new String[] {"search_result_browse_over", "search_result_browse_over_large"}) {
      BufferedImage image =
          ImageIO.read(getClass().getResourceAsStream("/org/limewire/gui/images/" + name + ".png"));
      int size = name.endsWith("_large") ? 32 : 16;
      assertEquals(size, image.getWidth(), name);
      assertEquals(size, image.getHeight(), name);
    }
  }
}
