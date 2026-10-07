/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.search;

import static org.junit.Assert.assertEquals;
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
  public void baseSummaryDoesNotClaimItIsOffByDefault() throws Exception {
    String strings = readProjectFile("res/values/strings.xml");
    Matcher m =
        Pattern.compile("name=\"icebridge_public_catalog_summary\">([^<]*)<").matcher(strings);
    assertTrue(m.find());
    assertTrue(m.group(1).endsWith("On by default."));
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
