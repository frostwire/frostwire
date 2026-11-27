/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * A preference key in a settings XML that misspells the "frostwire." prefix never matches the key
 * the code reads: settings_torrent.xml had "froswire.prefs.network.enable_dht", so the DHT switch
 * wrote a setting nothing read and its change listener never attached.
 */
public class PreferenceKeysTest {

  @Test
  public void everyFrostwirePreferenceKeyIsSpelledFrostwire() throws Exception {
    Pattern key = Pattern.compile("android:key=\"(fro[a-z]*\\.[^\"]*)\"");
    List<String> misspelled = new ArrayList<>();
    File[] files = new File("res/xml").listFiles((dir, name) -> name.endsWith(".xml"));
    for (File file : files) {
      Matcher m = key.matcher(Files.readString(file.toPath(), StandardCharsets.UTF_8));
      while (m.find()) {
        if (!m.group(1).startsWith("frostwire.")) {
          misspelled.add(file.getName() + ": " + m.group(1));
        }
      }
    }
    assertTrue("misspelled preference keys: " + misspelled, misspelled.isEmpty());
  }
}
