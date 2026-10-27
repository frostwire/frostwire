/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fonts must be created by logical family name (or through PlatformFonts). A physical family the
 * system lacks, like Helvetica on Windows, makes the JDK open every installed font file the first
 * time text in it is measured, which stalled the EDT for over 5 seconds while laying out the
 * Transfers tab (Icebase #c0082a8524a8, GitHub #1310).
 */
class PhysicalFontFamilyNamesTest {
  private static final Path DESKTOP = Path.of(System.getProperty("user.dir"));
  private static final List<Path> SOURCE_ROOTS =
      List.of(DESKTOP.resolve("src/main/java"), DESKTOP.resolve("../common/src/main/java"));
  private static final Pattern FONT_BY_NAME =
      Pattern.compile("new(?:Font|FontUIResource)\\(\"([^\"]*)\"");
  private static final Set<String> LOGICAL_FAMILIES =
      Set.of("dialog", "dialoginput", "serif", "sansserif", "monospaced");

  @Test
  void fontsAreCreatedByLogicalFamilyName() throws IOException {
    List<String> offenders = new ArrayList<>();
    for (Path root : SOURCE_ROOTS) {
      try (Stream<Path> files = Files.walk(root)) {
        files.filter(p -> p.toString().endsWith(".java")).forEach(p -> collect(p, offenders));
      }
    }
    assertTrue(
        offenders.isEmpty(),
        "Use a logical font family or PlatformFonts instead of: " + String.join(", ", offenders));
  }

  @Test
  void scannerFlagsPhysicalFamilies() {
    // Guard the extractor itself: it must see through google-java-format line breaks.
    Matcher matcher =
        FONT_BY_NAME.matcher(stripWhitespace("x.setFont(\n    new Font(\"Helvetica\", 0, 12));"));
    assertTrue(matcher.find());
    assertEquals("Helvetica", matcher.group(1));
    assertFalse(LOGICAL_FAMILIES.contains(matcher.group(1).toLowerCase(Locale.ROOT)));
  }

  private static void collect(Path file, List<String> offenders) {
    String source;
    try {
      source = stripWhitespace(Files.readString(file));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Matcher matcher = FONT_BY_NAME.matcher(source);
    while (matcher.find()) {
      String family = matcher.group(1);
      if (!LOGICAL_FAMILIES.contains(family.toLowerCase(Locale.ROOT))) {
        offenders.add(DESKTOP.relativize(file) + " (\"" + family + "\")");
      }
    }
  }

  /** Whitespace-free, so formatting cannot hide a call; family names lose their spaces too. */
  private static String stripWhitespace(String source) {
    return source.replaceAll("\\s+", "");
  }
}
