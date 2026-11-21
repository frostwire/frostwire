/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * FrostWire no longer links to reddit.com anywhere in its apps (maintainer decision, 2026-11-21). A
 * source scan of the desktop sources, its message catalogs and the Android sources and layouts, so
 * a link or a Reddit button cannot come back unnoticed.
 */
class NoRedditLinksTest {

  private static final List<String> ROOTS =
      List.of(
          "src/main/java",
          "resources/org/limewire/gui/resources",
          "lib/messagebundles",
          "../android/src/main/java",
          "../android/res/layout",
          "../android/res/values");

  @Test
  void noSourceMentionsReddit() throws IOException {
    List<String> hits = new ArrayList<>();
    for (String root : ROOTS) {
      Path dir = Path.of(root);
      if (!Files.isDirectory(dir)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(dir)) {
        for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
          String name = file.getFileName().toString();
          if (name.contains("~") || name.endsWith(".orig")) {
            continue;
          }
          String text = Files.readString(file, StandardCharsets.ISO_8859_1);
          if (text.toLowerCase(java.util.Locale.ROOT).contains("reddit")) {
            hits.add(file.toString());
          }
        }
      }
    }
    assertTrue(hits.isEmpty(), "Reddit is mentioned in: " + hits);
  }
}
