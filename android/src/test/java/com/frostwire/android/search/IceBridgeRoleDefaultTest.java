/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Android should participate as a full IceBridge peer (BOTH) by default: originate searches,
 * answer from its own index, and relay for others. It must not silently default to a CLIENT leaf.
 */
public class IceBridgeRoleDefaultTest {

  @Test
  public void repositoryDefaultsRoleToBoth() throws Exception {
    String repo =
        readProjectFile("src/main/java/com/frostwire/android/core/ConfigurationRepository.kt");
    assertTrue(
        "PREF_KEY_ICEBRIDGE_ROLE must default to BOTH",
        repo.contains("m[Constants.PREF_KEY_ICEBRIDGE_ROLE] = \"BOTH\""));
    assertFalse(
        "the old CLIENT default must be gone",
        repo.contains("m[Constants.PREF_KEY_ICEBRIDGE_ROLE] = \"CLIENT\""));
  }

  @Test
  public void repositoryMigratesExistingClientInstallOnce() throws Exception {
    String repo =
        readProjectFile("src/main/java/com/frostwire/android/core/ConfigurationRepository.kt");
    assertTrue(
        "existing installs persisted the old CLIENT default and need a one-time migration",
        repo.contains("migrateIceBridgeRoleToBoth"));
  }

  @Test
  public void settingsDefaultIsBoth() throws Exception {
    String xml = readProjectFile("res/xml/settings_distributed_search.xml");
    assertTrue(xml.contains("android:key=\"frostwire.prefs.icebridge.role\""));
    assertTrue(
        "the settings screen must default the role to BOTH", xml.contains("android:defaultValue=\"BOTH\""));
    assertFalse(xml.contains("android:defaultValue=\"CLIENT\""));
  }

  @Test
  public void relayStackFallbackIsBoth() throws Exception {
    String stack =
        readProjectFile("src/main/java/com/frostwire/android/search/AndroidRelayStack.java");
    String method = blockStartingAt(stack, "private static IceBridgeConfig.Role readConfiguredRole()");
    assertTrue(
        "when the role preference is missing or unparseable, fall back to BOTH",
        method.contains("IceBridgeConfig.Role.BOTH"));
  }

  private static String blockStartingAt(String source, String marker) {
    int start = source.indexOf(marker);
    if (start < 0) {
      return "";
    }
    int openingBrace = source.indexOf('{', start);
    if (openingBrace < 0) {
      return source.substring(start);
    }
    int depth = 0;
    for (int i = openingBrace; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(start, i);
      }
    }
    return source.substring(start);
  }

  private static String readProjectFile(String relativePath) throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relativePath);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relativePath);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
