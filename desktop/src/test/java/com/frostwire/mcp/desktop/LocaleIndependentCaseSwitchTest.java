/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1407: switching on, or looking up an enum by, a string case-converted with
 * the default locale breaks on Turkish and Azeri systems, where I and i convert to İ and ı
 * ("ignore" from "IGNORE" became "ıgnore" in the MCP transfer tools). Protocol words, units and
 * enum names are converted with Locale.ROOT.
 */
class LocaleIndependentCaseSwitchTest {

  private static final Pattern DEFAULT_LOCALE_DISPATCH =
      Pattern.compile(
          "(switch\\s*\\([^)]*\\.to(Upper|Lower)Case\\(\\)\\s*\\)"
              + "|valueOf\\([^)]*\\.to(Upper|Lower)Case\\(\\)\\s*\\))");

  @Test
  void noSwitchOrEnumLookupUsesTheDefaultLocale() throws IOException {
    List<String> offenders = new ArrayList<>();
    for (String root : new String[] {"src/main/java", "../common/src/main/java"}) {
      try (Stream<Path> files = Files.walk(Path.of(root))) {
        for (Path file :
            (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
          String[] lines = Files.readString(file).split("\n", -1);
          for (int i = 0; i < lines.length; i++) {
            Matcher m = DEFAULT_LOCALE_DISPATCH.matcher(lines[i]);
            if (m.find()) {
              offenders.add(file + ":" + (i + 1) + ": " + lines[i].trim());
            }
          }
        }
      }
    }
    assertEquals(List.of(), offenders, "use toUpperCase(Locale.ROOT) / toLowerCase(Locale.ROOT)");
  }
}
