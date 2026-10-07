/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.settings.SearchEnginesSettings;
import org.junit.jupiter.api.Test;

class IceBridgePublicCatalogDefaultTest {
  @Test
  void publicCatalogIsOptOutAndUserChoiceStillWins() {
    boolean original = SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue();
    try {
      SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(false);
      assertFalse(SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue());
      SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.revertToDefault();
      assertTrue(
          SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue(),
          "the default must publish active seeds until the user opts out");
    } finally {
      SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(original);
    }
  }
}
