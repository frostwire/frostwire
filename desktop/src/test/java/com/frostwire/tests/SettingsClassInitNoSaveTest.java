/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Settings classes are initialized by whichever thread first reads one of their settings, often the
 * EDT (the setup wizard's seeding page read SearchEnginesSettings and wrote the whole props file
 * from its static initializer). Class initialization can't be observed reliably in a shared test
 * JVM, so guard it at source level.
 */
class SettingsClassInitNoSaveTest {
  private static final Path SOURCE =
      Path.of(System.getProperty("user.dir"))
          .resolve("src/main/java/com/limegroup/gnutella/settings/SearchEnginesSettings.java");

  @Test
  void rudpPortMigrationDoesNotWriteSettingsDuringClassInit() throws Exception {
    String source = Files.readString(SOURCE).replaceAll("\\s+", "");
    int start = source.indexOf("static{if(!ICEBRIDGE_RUDP_AUTO_MIGRATED.getValue())");
    assertTrue(start >= 0, "migration static initializer not found");
    String block = source.substring(start, source.indexOf("publicstaticfinalclass", start));

    assertTrue(block.contains("ICEBRIDGE_RUDP_AUTO_MIGRATED.setValue(true);"));
    assertFalse(block.contains(".save()"), "static init must not write the props file");
  }
}
