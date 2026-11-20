/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tooltips are plain text: no {@code <html>} and no {@code <br>} (frostwire/frostwire#1562). Swing
 * parses an {@code <html>} tooltip and lays out a CSS view tree on the EDT every time it is shown,
 * a first-use cost and a source of EDT stalls (Icebase tickets #1341, #1551, #1562). A source scan:
 * the tooltips need a display to be built. Multi-line tooltips use a newline, which the table and
 * component tooltip UIs draw as lines. Text that comes from a translation with markup in it goes
 * through {@code ToolTipText.plain}.
 */
class TooltipsAreNotHtmlStructureTest {

  private static final Path SOURCES = Path.of("src/main/java");

  /** The scan finds these calls and the bodies of the overrides declared with these names. */
  private static final Pattern TOOLTIP_SITE =
      Pattern.compile("(setToolTipText|getToolTipText|getToolTipArray|createToolTip)\\(");

  private static final Pattern MARKUP = Pattern.compile("<html|<br", Pattern.CASE_INSENSITIVE);

  /** Files that may build HTML tooltips, and why. Empty: every site was converted. */
  private static final Map<String, String> ALLOWED = Map.of();

  /** Source text without comments and whitespace. */
  private static String code(Path file) throws IOException {
    String s = Files.readString(file, StandardCharsets.UTF_8);
    s = s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    return s.replaceAll("\\s+", "");
  }

  /** The call's arguments, and the body when the call is the head of a method declaration. */
  static String region(String code, int from) {
    int i = from;
    int depth = 1;
    while (i < code.length() && depth > 0) {
      char c = code.charAt(i++);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      }
    }
    String args = code.substring(from, i);
    // a declaration is "(...){body}" or "(...)throwsX{body}"; a call is followed by ; ) , or .
    int body = i;
    while (body < code.length() && Character.isLetterOrDigit(code.charAt(body))) {
      body++;
    }
    if (body >= code.length() || code.charAt(body) != '{') {
      return args;
    }
    int braces = 0;
    int j = body;
    do {
      char c = code.charAt(j++);
      if (c == '{') {
        braces++;
      } else if (c == '}') {
        braces--;
      }
    } while (j < code.length() && braces > 0);
    return args + code.substring(body, j);
  }

  /** The sites in {@code code} that build tooltip text with {@code <html} or {@code <br}. */
  static List<String> offenders(String code) {
    List<String> found = new ArrayList<>();
    Matcher m = TOOLTIP_SITE.matcher(code);
    while (m.find()) {
      if (MARKUP.matcher(region(code, m.end())).find()) {
        found.add(m.group(1));
      }
    }
    return found;
  }

  @Test
  void noTooltipInTheDesktopSourcesIsBuiltAsHtml() throws IOException {
    List<String> offending = new ArrayList<>();
    try (Stream<Path> files = Files.walk(SOURCES)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String name = SOURCES.relativize(file).toString().replace('\\', '/');
        if (ALLOWED.containsKey(name)) {
          continue;
        }
        List<String> sites = offenders(code(file));
        if (!sites.isEmpty()) {
          offending.add(name + " " + sites);
        }
      }
    }
    assertTrue(
        offending.isEmpty(),
        "tooltips must be plain text, with a newline for a line break and ToolTipText.plain for a"
            + " translation that carries markup: "
            + offending);
  }

  @Test
  void theScanSeesHtmlAndBreaksInCallsAndOverrides() {
    assertEquals(1, offenders("x.setToolTipText(\"<html>a</html>\");").size());
    assertEquals(1, offenders("x.setToolTipText(\"a\"+\"<br>\"+b);").size());
    assertEquals(
        1, offenders("publicStringgetToolTipText(){return\"<html>\"+a+\"</html>\";}").size());
    assertEquals(
        1,
        offenders("publicStringgetToolTipText(MouseEvente){return\"a<br>b\";}").size(),
        "an override with a parameter");
    assertTrue(offenders("x.setToolTipText(a+\"\\n\"+b);").isEmpty());
    assertTrue(offenders("x.setToolTipText(ToolTipText.plain(I18n.tr(\"<b>a</b>\")));").isEmpty());
    assertTrue(offenders("x.setText(\"<html>a</html>\");").isEmpty());
    assertTrue(
        offenders("x.getToolTipText();y.setText(\"<html>a</html>\");").isEmpty(),
        "a call is not a declaration");
  }
}
