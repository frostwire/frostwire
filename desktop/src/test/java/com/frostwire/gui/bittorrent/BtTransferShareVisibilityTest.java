/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.LibtorrentTorrentMetadataProvider;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import org.junit.jupiter.api.Test;

class BtTransferShareVisibilityTest {
  @Test
  void localHistoryPreferenceDoesNotAuthorizePublicShares() {
    boolean includeInactive = SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.getValue();
    try {
      SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.setValue(true);
      assertFalse(BtTransferShareVisibility.INSTANCE.isVisible(null));
      assertFalse(BtTransferShareVisibility.INSTANCE.isVisible(""));
      assertTrue(BtTransferShareVisibility.LOCAL.isVisible("historical-local-row"));
    } finally {
      SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.setValue(includeInactive);
    }
  }

  @Test
  void iceBridgeOptOutDeniesMetadataWithoutEnteringTheNativeSession() {
    boolean iceBridge = SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue();
    boolean includeInactive = SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.getValue();
    try {
      LibtorrentTorrentMetadataProvider provider =
          new LibtorrentTorrentMetadataProvider(BtTransferShareVisibility.INSTANCE);
      byte[] selectedHash = new byte[20];
      String selectedHex = com.frostwire.util.Hex.encode(selectedHash);
      SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.setValue(true);
      SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(false);
      assertFalse(BtTransferShareVisibility.INSTANCE.isVisible(selectedHex));
      assertFalse(provider.isPubliclyShared(selectedHash));
      assertNull(provider.torrentBytes(selectedHash));
      assertTrue(BtTransferShareVisibility.LOCAL.isVisible(selectedHex));
    } finally {
      SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(iceBridge);
      SearchEnginesSettings.LOCAL_SEARCH_INCLUDE_INACTIVE.setValue(includeInactive);
    }
  }
}
