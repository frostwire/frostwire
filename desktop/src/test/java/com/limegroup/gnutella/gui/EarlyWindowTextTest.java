/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Before the splash the warm-up loads only the font files the first windows' text needs, so that
 * text must be the one those windows show, in the user's language (Icebase #7d83b84c37c5,
 * #0068ec7873a2, frostwire/frostwire#1544). It includes the setup wizard's title and buttons, which
 * the EDT measured in Dialog bold while its slots were still unloaded (Icebase #d5a31f6c0160,
 * frostwire/frostwire#1329).
 */
class EarlyWindowTextTest {

  private static final Path SOURCES = Path.of(System.getProperty("user.dir"), "src/main/java");

  /** Where the first windows show each string. */
  private static final Map<String, List<String>> SHOWN_BY =
      Map.of(
          "com/limegroup/gnutella/gui/SplashWindow.java", List.of("Loading FrostWire..."),
          "com/limegroup/gnutella/gui/Initializer.java",
              List.of("Loading HTML Engine...", "Loading User Interface..."),
          "com/limegroup/gnutella/gui/init/SetupManager.java",
              List.of("FrostWire Setup Wizard", "<< Back", "Next >>", "Finish", "Cancel"),
          "com/limegroup/gnutella/gui/init/WelcomeWindow.java", List.of("Welcome"),
          "com/limegroup/gnutella/gui/init/BitTorrentSettingsWindow.java",
              List.of("BitTorrent Sharing Settings"),
          "com/frostwire/gui/bittorrent/TorrentSeedingSettingComponent.java",
              List.of("Seed finished downloads.", "Don't seed finished downloads."),
          "com/frostwire/gui/tabs/SearchTab.java", List.of("Search"),
          "com/frostwire/gui/tabs/TransfersTab.java",
              List.of("Transfers", "Downloading", "Seeding", "Finished"),
          "com/frostwire/gui/tabs/LibraryTab.java", List.of("Library"));

  @AfterEach
  void restoreLocale() {
    I18n.setLocale(ResourceManager.getLocale());
  }

  @Test
  void theSampleHasTheTextOfTheSplashTheSetupWizardAndTheMainWindow() throws Exception {
    EarlyWindowText.strings(); // sets the user's locale once; the test then picks its own
    I18n.setLocale(Locale.ENGLISH);
    List<String> strings = EarlyWindowText.strings();

    for (Map.Entry<String, List<String>> window : SHOWN_BY.entrySet()) {
      String source = Files.readString(SOURCES.resolve(window.getKey()));
      for (String text : window.getValue()) {
        assertTrue(
            source.contains("tr(\"" + text.replace("\"", "\\\"") + "\")"),
            window.getKey() + " no longer shows " + text);
        assertTrue(strings.contains(text), "the sample lacks " + text);
      }
    }
    String sample = EarlyWindowText.sample();
    assertTrue(sample.contains("FrostWire Setup Wizard") && sample.contains("Next >>"), sample);
  }

  /** A Japanese user's first windows load the Japanese font, so the sample is in Japanese. */
  @Test
  void theSampleIsInTheUsersLanguage() {
    EarlyWindowText.strings();
    I18n.setLocale(Locale.JAPANESE);

    String sample = EarlyWindowText.sample();

    assertTrue(
        sample
            .codePoints()
            .anyMatch(
                cp ->
                    Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
                        || Character.UnicodeScript.of(cp) == Character.UnicodeScript.KATAKANA),
        "not translated: " + sample);
  }
}
