/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RelayParticipationSettingsTest {
  @Test
  void eitherOptOutStopsStartupBeforePublicDependenciesAreUsed() {
    boolean iceBridge = SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue();
    boolean distributed = SearchEnginesSettings.DISTRIBUTED_SEARCH_ENABLED.getValue();
    try {
      AtomicInteger publicStarts = new AtomicInteger();
      SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(false);
      SearchEnginesSettings.DISTRIBUTED_SEARCH_ENABLED.setValue(true);
      Initializer.startRelayParticipation(publicStarts::incrementAndGet);
      assertEquals(0, publicStarts.get());
      SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(true);
      SearchEnginesSettings.DISTRIBUTED_SEARCH_ENABLED.setValue(false);
      Initializer.startRelayParticipation(publicStarts::incrementAndGet);
      assertEquals(0, publicStarts.get());
      SearchEnginesSettings.DISTRIBUTED_SEARCH_ENABLED.setValue(true);
      Initializer.startRelayParticipation(publicStarts::incrementAndGet);
      assertEquals(1, publicStarts.get());
    } finally {
      SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(iceBridge);
      SearchEnginesSettings.DISTRIBUTED_SEARCH_ENABLED.setValue(distributed);
    }
  }
}
