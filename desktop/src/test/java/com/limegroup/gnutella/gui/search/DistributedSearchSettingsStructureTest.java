/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DistributedSearchSettingsStructureTest {

  @Test
  void distributedSearchDefaultsOnAndIsFirstButDoesNotGateParticipation() throws Exception {
    String settings = read("src/main/java/com/limegroup/gnutella/settings/SearchEnginesSettings.java");
    String pane =
        read("src/main/java/com/limegroup/gnutella/gui/options/panes/SearchEnginesPaneItem.java");
    String visibility =
        read("src/main/java/com/frostwire/gui/bittorrent/BtTransferShareVisibility.java");
    String urlHandler =
        read("src/main/java/com/limegroup/gnutella/gui/search/IceBridgeUrlHandler.java");

    assertTrue(
        settings.contains("createBooleanSetting(\"DISTRIBUTED_SEARCH_ENABLED\", true)"));
    assertTrue(pane.contains("addDistributedSearchCheckbox(panel);"));
    assertTrue(pane.contains("I18n.tr(\"Distributed Search\")"));
    assertFalse(
        visibility
            .substring(visibility.indexOf("public boolean isVisible"), visibility.indexOf("private static boolean isActiveTransfer"))
            .contains("DISTRIBUTED_SEARCH_ENABLED"));
    assertTrue(urlHandler.contains("distributed == null || !distributed.isEnabled() || !distributed.isReady()"));
  }

  private static String read(String relativePath) throws Exception {
    Path path = Path.of(relativePath);
    if (!Files.isRegularFile(path)) {
      path = Path.of("desktop", relativePath);
    }
    return Files.readString(path, StandardCharsets.UTF_8);
  }
}
