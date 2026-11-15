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
import com.limegroup.gnutella.settings.BugSettings;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private boolean icebase;

  @BeforeEach
  void remember() {
    enabled = SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue();
    catalog = SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue();
    icebase = BugSettings.SEND_ICEBASE_REPORTS.getValue();
  }

  @AfterEach
  void restore() {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(enabled);
    SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(catalog);
    BugSettings.SEND_ICEBASE_REPORTS.setValue(icebase);
  }

  @Test
  void decliningTheCatalogStillKeepsTheUserOnTheNetwork() {
    SeedingPublishingChoices.save(true, false, true);

    assertTrue(SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
    assertFalse(SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue());
  }

  @Test
  void everyChoiceIsStoredIndependently() {
    for (boolean join : new boolean[] {true, false}) {
      for (boolean share : new boolean[] {true, false}) {
        for (boolean reports : new boolean[] {true, false}) {
          SeedingPublishingChoices.save(join, share, reports);
          assertEquals(join, SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
          assertEquals(share, SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue());
          assertEquals(reports, BugSettings.SEND_ICEBASE_REPORTS.getValue());
        }
      }
    }
  }

  /**
   * frostwire/frostwire#1565: the wizard page shows the Icebase reports choice with the stored
   * value and saves what the user picked. The page is Swing, so its source is checked.
   */
  @Test
  void theWizardPageLoadsAndSavesTheIcebaseReportsChoice() throws Exception {
    String page =
        Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve(
                    "src/main/java/com/limegroup/gnutella/gui/init/SeedingPublishingWindow.java"),
            StandardCharsets.UTF_8);
    String compact = page.replaceAll("\\s+", "");
    assertTrue(compact.contains("_icebaseReports=newJCheckBox(IcebaseReportsText.label());"));
    assertTrue(compact.contains("_icebaseReports.setSelected(IcebaseReports.enabled());"));
    assertTrue(compact.contains("note(IcebaseReportsText.note())"));
    assertTrue(
        compact.contains(
            "SeedingPublishingChoices.save(_joinNetwork.isSelected(),_shareCatalog.isSelected(),_icebaseReports.isSelected());"));
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
