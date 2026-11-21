/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.frostwire.search.engines.SearchEngineID;
import org.junit.jupiter.api.Test;

/**
 * An engine id with no setting made the Search options panel throw IllegalArgumentException the
 * moment it asked whether that engine is on (an Icebase report from a dev build, 2026-11-21). Every
 * engine the shared registry knows needs its desktop setting.
 */
class DesktopSearchEngineAdapterTest {

  @Test
  void everySearchEngineIdHasADesktopSetting() {
    for (SearchEngineID id : SearchEngineID.values()) {
      assertNotNull(DesktopSearchEngineAdapter.settingFor(id), "no setting for " + id);
    }
  }
}
