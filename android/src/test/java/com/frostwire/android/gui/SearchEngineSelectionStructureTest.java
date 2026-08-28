/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

public class SearchEngineSelectionStructureTest {

  @Test
  public void getEngines_distributedEnabledBeforeWiring_doesNotEnableArchive() throws Exception {
    String source = readProjectFile("src/main/java/com/frostwire/android/gui/SearchEngine.java");
    String compactSource = source.replaceAll("\\s+", "");

    assertTrue(
        compactSource.contains(
            "booleanoneEnabled=ConfigurationManager.instance()"
                + ".getBoolean(Constants.PREF_KEY_SEARCH_USE_DISTRIBUTED);"));
  }

  private static String readProjectFile(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
