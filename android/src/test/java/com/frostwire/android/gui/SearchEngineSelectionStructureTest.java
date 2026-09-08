/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import static org.junit.Assert.assertTrue;

import com.frostwire.android.BuildConfig;
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

  @Test
  public void releaseBuild_hidesAndDisablesDiagnosticLocalSearch() throws Exception {
    String engineSource = readProjectFile("src/main/java/com/frostwire/android/gui/SearchEngine.java");
    String repositorySource =
        readProjectFile("src/main/java/com/frostwire/android/core/ConfigurationRepository.kt");

    assertTrue(engineSource.contains("return BuildConfig.DEBUG;"));
    assertTrue(SearchEngine.LOCAL.isActive() == BuildConfig.DEBUG);
    assertTrue(
        repositorySource.contains(
            "if (!BuildConfig.DEBUG) {\n            setDefault(Constants.PREF_KEY_SEARCH_USE_LOCAL, false)"));
  }

  @Test
  public void searchSettings_putDistributedFirstAndSeparateIceBridgeParticipation() throws Exception {
    String searchSettings = readProjectFile("res/xml/settings_search_engines.xml");
    String distributedSettings = readProjectFile("res/xml/settings_distributed_search.xml");
    String constants = readProjectFile("src/main/java/com/frostwire/android/core/Constants.java");
    String repositorySource =
        readProjectFile("src/main/java/com/frostwire/android/core/ConfigurationRepository.kt");

    int distributed = searchSettings.indexOf("frostwire.prefs.search.use_distributed");
    int selectAll = searchSettings.indexOf("frostwire.prefs.search.preference_category.select_all");
    assertTrue(distributed >= 0 && distributed < selectAll);
    assertTrue(distributedSettings.contains("frostwire.prefs.icebridge.enabled"));
    assertTrue(constants.contains("PREF_KEY_ICEBRIDGE_ENABLED"));
    assertTrue(
        repositorySource.contains("m[Constants.PREF_KEY_ICEBRIDGE_ENABLED] = true"));
    assertTrue(repositorySource.contains("migrateDistributedParticipationPreference()"));
  }

  private static String readProjectFile(String relativePath) throws Exception {
    File file = new File(relativePath);
    if (!file.isFile()) {
      file = new File("android", relativePath);
    }
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
  }
}
