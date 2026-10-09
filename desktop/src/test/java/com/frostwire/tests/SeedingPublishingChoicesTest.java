/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.init.SeedingPublishingChoices;
import com.limegroup.gnutella.gui.init.SeedingPublishingText;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Joining the network (be searchable, be found by keyword) and sharing the catalog (be browsable
 * and crawlable) are two different decisions and must never be coupled.
 */
class SeedingPublishingChoicesTest {

  private boolean enabled;
  private boolean catalog;

  @BeforeEach
  void remember() {
    enabled = SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue();
    catalog = SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue();
  }

  @AfterEach
  void restore() {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(enabled);
    SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(catalog);
  }

  @Test
  void decliningTheCatalogStillKeepsTheUserOnTheNetwork() {
    SeedingPublishingChoices.save(true, false);

    assertTrue(SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
    assertFalse(SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue());
  }

  @Test
  void bothChoicesAreStoredIndependently() {
    for (boolean join : new boolean[] {true, false}) {
      for (boolean share : new boolean[] {true, false}) {
        SeedingPublishingChoices.save(join, share);
        assertEquals(join, SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
        assertEquals(share, SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue());
      }
    }
  }

  @Test
  void theWordingSaysTheCatalogIsNotWhatMakesTorrentsFindable() {
    assertNotEquals(SeedingPublishingText.joinNetworkLabel(), SeedingPublishingText.catalogLabel());
    assertTrue(
        SeedingPublishingText.joinNetworkNote().contains("even if you do not share your catalog"),
        "unchecking the catalog must not look like hiding from search");
    assertTrue(SeedingPublishingText.joinNetworkNote().contains("leave the network"));
    assertTrue(SeedingPublishingText.catalogNote().contains("browse"));
    assertTrue(SeedingPublishingText.catalogNote().contains("crawlers"));
    assertTrue(SeedingPublishingText.catalogNote().contains("never published"));
  }
}
