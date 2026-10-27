/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The transfer detail panels refresh every second. A refresh whose texts keep their sizes must not
 * ask for a layout: each request invalidates the hierarchy up to the main window, whose next
 * validate pass re-lays out every tab (frostwire/frostwire#1342).
 *
 * <p>The panels need the theme and settings to be built, which a headless test can't load, so this
 * checks their sources: every label the refresh sets must be a SizeStableLabel, whose behavior
 * SizeStableLabelTest covers.
 */
class TransferDetailRevalidationTest {

  private static final Pattern SET_TEXT = Pattern.compile("(\\w+)\\.setText\\(");

  @Test
  void generalTabRefreshOnlySetsSizeStableLabels() throws Exception {
    String source = readSource("TransferDetailGeneral.java");
    assertRefreshSetsOnlySizeStableLabels(source, methodBody(source, "public void updateData("));
  }

  private static void assertRefreshSetsOnlySizeStableLabels(String source, String refresh) {
    Set<String> updated = new TreeSet<>();
    Matcher matcher = SET_TEXT.matcher(refresh);
    while (matcher.find()) {
      updated.add(matcher.group(1));
    }
    assertFalse(updated.isEmpty(), "the refresh must set label texts");
    for (String label : updated) {
      assertTrue(
          source.contains(label + " = new SizeStableLabel()"),
          label + " is set on every refresh, it must be a SizeStableLabel");
    }
  }

  /** The text of the method whose declaration starts with {@code signature}, braces balanced. */
  static String methodBody(String source, String signature) {
    int start = source.indexOf(signature);
    assertTrue(start >= 0, signature + " not found");
    int open = source.indexOf('{', start);
    int depth = 0;
    for (int i = open; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(open, i + 1);
      }
    }
    throw new AssertionError("unbalanced braces after " + signature);
  }

  static String readSource(String fileName) throws Exception {
    String relative = "src/main/java/com/frostwire/gui/components/transfers/" + fileName;
    Path path = Path.of(relative);
    if (!Files.exists(path)) {
      path = Path.of("desktop", relative);
    }
    return Files.readString(path);
  }
}
