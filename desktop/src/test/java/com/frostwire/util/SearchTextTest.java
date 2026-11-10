/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Search keywords are normalized for every keyword and every result on the EDT. Doing it with
 * {@code String.replaceAll} and a Unicode block regex compiled the regex each time, and the first
 * compile initialized {@code Character.UnicodeBlock} there (frostwire/frostwire#1521).
 */
class SearchTextTest {

  @Test
  void normalizeRemovesAccentsAndCase() {
    assertEquals("cancion", SearchText.normalize("CANCIÓN"));
    assertEquals("cancion", SearchText.normalize("canción"));
    assertEquals("francais uber", SearchText.normalize("Français Über"));
    assertEquals("plain text 123", SearchText.normalize("Plain Text 123"));
    assertEquals("", SearchText.normalize(""));
  }

  /** The same result as the expression the search box and the Library search used before. */
  @Test
  void normalizeMatchesTheFormerExpression() {
    for (String text :
        List.of("Crème Brûlée", "ＦＵＬＬ　ｗｉｄｔｈ", "ﬁne ligature", "日本語のテキスト", "İstanbul", "á̂b")) {
      String former =
          Normalizer.normalize(text, Normalizer.Form.NFKD)
              .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
              .toLowerCase(Locale.US);
      assertEquals(former, SearchText.normalize(text), text);
    }
  }

  @Test
  void theSearchesCompileNoRegexPerKeyword() throws Exception {
    for (String file :
        List.of(
            "src/main/java/com/limegroup/gnutella/gui/search/SearchMediator.java",
            "src/main/java/com/frostwire/gui/library/LibrarySearch.java")) {
      String source = Files.readString(Path.of(file)).replaceAll("\\s+", "");
      assertFalse(
          source.contains("replaceAll(\"\\\\p{"), file + " compiles a Unicode regex per call");
      assertTrue(source.contains("SearchText.normalize("), file + " uses SearchText");
    }
  }

  @Test
  void theStartupWarmUpRunsIt() throws Exception {
    assertEquals("cafe", JdkWarmUp.warmUpSearchText());
    String source =
        Files.readString(Path.of("src/main/java/com/frostwire/util/JdkWarmUp.java"))
            .replaceAll("\\s+", "");
    assertTrue(source.contains("warmUpSearchText();"), "the startup warm-up calls it");
  }
}
