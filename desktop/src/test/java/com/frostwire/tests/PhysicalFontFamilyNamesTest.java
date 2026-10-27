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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fonts must be created by logical family name (or through PlatformFonts). A physical family the
 * system lacks, like Helvetica on Windows or Arial on Linux, makes the JDK open every installed
 * font file the first time text in it is measured, which stalled the EDT for over 5 seconds while
 * laying out the Transfers tab (Icebase #c0082a8524a8, GitHub #1310; Icebase #d45a8ea5abbe, GitHub
 * #1313).
 *
 * <p>Besides {@code new Font("...")}, this catches the other ways a family name reaches the JDK:
 * {@code Font.decode("...")}, a string constant of the same file passed to {@code new Font}, and
 * HTML/CSS text ({@code <font face=...>}, {@code font-family:}), whose first family Swing's HTML
 * renderer creates as is.
 */
class PhysicalFontFamilyNamesTest {
  private static final Path DESKTOP = Path.of(System.getProperty("user.dir"));
  private static final List<Path> SOURCE_ROOTS =
      List.of(DESKTOP.resolve("src/main/java"), DESKTOP.resolve("../common/src/main/java"));
  private static final Pattern FONT_BY_NAME =
      Pattern.compile("new(?:Font|FontUIResource)\\(\"([^\"]*)\"");
  private static final Pattern FONT_BY_CONSTANT =
      Pattern.compile("new(?:Font|FontUIResource)\\(([A-Za-z_$][\\w$]*),");
  private static final Pattern STRING_CONSTANT =
      Pattern.compile("String([A-Za-z_$][\\w$]*)=\"([^\"]*)\";");
  private static final Pattern FONT_DECODE = Pattern.compile("Font\\.decode\\(\"([^\"]*)\"");

  /**
   * {@code <font face='x,y'>} or {@code font-family:x,y}; group 1 is the first family, the only one
   * Swing uses. A face built from an expression ({@code face=" + family + "}) names no literal.
   */
  private static final Pattern HTML_FONT_FAMILY =
      Pattern.compile("(?i)(?:<font[^>]*?face=\\\\?[\"']?|font-family:)([^,;\"'\\\\}>+]+)");

  /** Font.decode style and size suffixes: "-BOLD-12", or "Bold12" once whitespace is gone. */
  private static final Pattern DECODE_SUFFIX =
      Pattern.compile("(?i)(?:-.*|(?:bolditalic|bold|italic|plain)?\\d*)$");

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
    assertFalse(isLogical(matcher.group(1)));
  }

  @Test
  void scannerFlagsFamiliesOutsideFontConstructorLiterals() {
    assertEquals(
        List.of("arial"),
        physicalFamilies("String html = \"<html><font face='arial,helvetica,sanserif'>\";"));
    assertEquals(
        List.of("Helvetica"), physicalFamilies("String h = \"<font face=\\\"Helvetica\\\">\";"));
    assertEquals(
        List.of("LucidaGrande"),
        physicalFamilies("String css = \"body { font-family: Lucida Grande, sans-serif; }\";"));
    assertEquals(List.of("Arial"), physicalFamilies("Font f = Font.decode(\"Arial-BOLD-12\");"));
    assertEquals(
        List.of("Helvetica"),
        physicalFamilies(
            "static final String FAMILY = \"Helvetica\";\nFont f =\n    new Font(FAMILY, 0, 12);"));
  }

  @Test
  void scannerAcceptsLogicalFamilies() {
    assertEquals(
        List.of(),
        physicalFamilies(
            "String a = \"<font face='\" + Font.SANS_SERIF + \"'>\";"
                + "String b = \"<font face='SansSerif,Arial'>\";"
                + "String c = \"font-family: Dialog;\";"
                + "Font d = Font.decode(\"Dialog-BOLD-12\");"
                + "Font e = Font.decode(\"Monospaced 12\");"
                + "static final String F = \"Dialog\";"
                + "Font g = new Font(F, 0, 12);"
                + "Font h = new Font(name, 0, 12);"
                + "Font i = new Font(familyFor(x), 0, 12);"
                + "String j = \"<font face=\" + family + \">\";"
                + "String k = \"listen interface=\" + iface;"));
  }

  private static void collect(Path file, List<String> offenders) {
    String source;
    try {
      source = Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    for (String family : physicalFamilies(source)) {
      offenders.add(DESKTOP.relativize(file) + " (\"" + family + "\")");
    }
  }

  /** The non-logical font families a source file asks for. */
  static List<String> physicalFamilies(String rawSource) {
    String source = stripWhitespace(rawSource);
    List<String> families = new ArrayList<>();
    Matcher matcher = FONT_BY_NAME.matcher(source);
    while (matcher.find()) {
      families.add(matcher.group(1));
    }
    Map<String, String> constants = new HashMap<>();
    matcher = STRING_CONSTANT.matcher(source);
    while (matcher.find()) {
      constants.put(matcher.group(1), matcher.group(2));
    }
    matcher = FONT_BY_CONSTANT.matcher(source);
    while (matcher.find()) {
      String value = constants.get(matcher.group(1));
      if (value != null) {
        families.add(value);
      }
    }
    matcher = FONT_DECODE.matcher(source);
    while (matcher.find()) {
      families.add(DECODE_SUFFIX.matcher(matcher.group(1)).replaceFirst(""));
    }
    matcher = HTML_FONT_FAMILY.matcher(source);
    while (matcher.find()) {
      families.add(matcher.group(1));
    }
    families.removeIf(PhysicalFontFamilyNamesTest::isLogical);
    return families;
  }

  private static boolean isLogical(String family) {
    return LOGICAL_FAMILIES.contains(family.toLowerCase(Locale.ROOT));
  }

  /** Whitespace-free, so formatting cannot hide a call; family names lose their spaces too. */
  private static String stripWhitespace(String source) {
    return source.replaceAll("\\s+", "");
  }
}
