/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.init.SeedingPublishingText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Every shipped language must translate the "Seeding & Publishing" setup wizard page. */
class SetupWizardTranslationsTest {

  /** English sources: the default/English catalogs legitimately leave these untranslated. */
  private static final Set<String> ENGLISH = Set.of("default", "en", "en_CA", "en_GB");

  private static final String DESCRIPTION =
      "Seeding a torrent publishes it. With Distributed Search enabled, other FrostWire users on the network can find the torrents you are actively sharing and download them from you.";

  private static List<Path> catalogs() throws IOException {
    List<Path> out = new ArrayList<>();
    Path dir = Path.of(System.getProperty("user.dir")).resolve("lib/messagebundles");
    try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.po")) {
      files.forEach(out::add);
    }
    return out;
  }

  private static final Pattern ENTRY =
      Pattern.compile(
          "^msgid \"((?:[^\"\\\\]|\\\\.)*)\"\\n((?:\"(?:[^\"\\\\]|\\\\.)*\"\\n)*)msgstr \"((?:[^\"\\\\]|\\\\.)*)\"\\n((?:\"(?:[^\"\\\\]|\\\\.)*\"\\n)*)",
          Pattern.MULTILINE);
  private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

  private static String join(String first, String continuation) {
    StringBuilder sb = new StringBuilder(first);
    Matcher m = LITERAL.matcher(continuation);
    while (m.find()) {
      sb.append(m.group(1));
    }
    return sb.toString();
  }

  /** Long entries are wrapped over several quoted lines by gettext; join them back. */
  private static String msgstr(String catalog, String msgid) {
    String wanted = msgid.replace("\"", "\\\"");
    Matcher m = ENTRY.matcher(catalog);
    while (m.find()) {
      if (join(m.group(1), m.group(2)).equals(wanted)) {
        return join(m.group(3), m.group(4));
      }
    }
    return null;
  }

  @Test
  void sourceStringsMatchTheCatalogKeys() {
    List<String> points = SeedingPublishingText.distributedSearchPoints();
    assertEquals(3, points.size());
    assertTrue(points.get(2).startsWith("You can change both options later"));
  }

  @Test
  void everyLanguageTranslatesTheWizardPage() throws Exception {
    List<String> msgids = new ArrayList<>(SeedingPublishingText.distributedSearchPoints());
    msgids.add(DESCRIPTION);
    msgids.add(SeedingPublishingText.joinNetworkLabel());
    msgids.add(SeedingPublishingText.joinNetworkNote());
    msgids.add(SeedingPublishingText.catalogLabel());
    msgids.add(SeedingPublishingText.catalogNote());
    List<Path> files = catalogs();
    assertTrue(files.size() >= 61, "expected every language catalog, found " + files.size());
    for (Path file : files) {
      String lang = file.getFileName().toString().replace(".po", "");
      String catalog = Files.readString(file, StandardCharsets.UTF_8);
      for (String msgid : msgids) {
        String translated = msgstr(catalog, msgid);
        assertTrue(translated != null, lang + " is missing: " + msgid);
        if (!ENGLISH.contains(lang)) {
          assertFalse(translated.isBlank(), lang + " has an empty translation: " + msgid);
          assertFalse(
              translated.equals(msgid.replace("\"", "\\\"")), lang + " left English: " + msgid);
        }
      }
    }
  }
}
