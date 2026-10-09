/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * The public catalog is opt-out. The stored default, the settings switch, and every translated
 * explanation must agree, or the screen can show OFF while seeded torrents are being advertised.
 */
public class IceBridgeCatalogDefaultTest {

  @Test
  public void repositoryAndSettingsSwitchAgreeOnOptOutDefault() throws Exception {
    String repo =
        readProjectFile("src/main/java/com/frostwire/android/core/ConfigurationRepository.kt");
    assertTrue(repo.contains("m[Constants.PREF_KEY_ICEBRIDGE_PUBLIC_CATALOG] = true"));
    Matcher m =
        Pattern.compile(
                "android:key=\"frostwire\\.prefs\\.icebridge\\.public_catalog\"[^>]*?"
                    + "android:defaultValue=\"(true|false)\"",
                Pattern.DOTALL)
            .matcher(readProjectFile("res/xml/settings_distributed_search.xml"));
    assertTrue(m.find());
    assertEquals("true", m.group(1));
  }

  @Test
  public void catalogAndNetworkAreSeparateChoicesInTheStringsAndTheSettingsScreen()
      throws Exception {
    String strings = readProjectFile("res/values/strings.xml");
    String join = stringValue(strings, "icebridge_join_title");
    String joinNote = stringValue(strings, "icebridge_join_note");
    String catalog = stringValue(strings, "icebridge_public_catalog_title");
    String catalogNote = stringValue(strings, "icebridge_public_catalog_summary");
    assertNotEquals(join, catalog);
    assertTrue(
        "unchecking the catalog must not look like hiding from search",
        joinNote.contains("even if you do not share your catalog"));
    assertTrue(catalogNote.contains("browse") && catalogNote.contains("crawlers"));
    assertFalse(
        "the summary must not claim a stale default", catalogNote.contains("Off by default"));

    String xml = readProjectFile("res/xml/settings_distributed_search.xml");
    Matcher m =
        Pattern.compile(
                "android:key=\"frostwire\\.prefs\\.icebridge\\.public_catalog\"([^>]*?)/>",
                Pattern.DOTALL)
            .matcher(xml);
    assertTrue(m.find());
    assertTrue(
        "the catalog switch is meaningless off the network",
        m.group(1).contains("android:dependency=\"frostwire.prefs.icebridge.enabled\""));
  }

  private static String stringValue(String strings, String name) {
    Matcher m = Pattern.compile("<string name=\"" + name + "\">([^<]*)</string>").matcher(strings);
    assertTrue(name, m.find());
    return m.group(1);
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
