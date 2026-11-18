/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Icebase #8e8c062251e8 and #51520a6e3dc2, frostwire/frostwire#1579 and #1583: on Windows, getting
 * the system clipboard sets up native OLE, and writing to it waits (with retries) while another
 * program holds it, so neither belongs on the EDT. Only these files may ask the toolkit for the
 * system clipboard, and each does it off the EDT: the copy helper and the paste helper run it on a
 * worker, the search field's reader runs in the clipboard focus listener's worker, and the magnet
 * listener looks it up lazily, in a holder class its parser worker initializes. Every other copy
 * action calls {@code GUIMediator.setClipboardContent}.
 */
class ClipboardOffEdtStructureTest {

  private static final Set<String> ALLOWED =
      Set.of(
          "com/limegroup/gnutella/gui/GUIMediator.java",
          "com/limegroup/gnutella/gui/util/AsyncPaste.java",
          "com/limegroup/gnutella/gui/search/MagnetClipboardListener.java",
          "com/limegroup/gnutella/gui/search/IceBridgeUrlHandler.java",
          "com/frostwire/gui/searchfield/GoogleSearchField.java");

  @Test
  void onlyTheOffEdtHelpersAskForTheSystemClipboard() throws IOException {
    Path root = Path.of("src/main/java");
    Set<String> found = new TreeSet<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file :
          (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        if (Files.readString(file).contains("getSystemClipboard()")) {
          found.add(root.relativize(file).toString());
        }
      }
    }
    assertEquals(new TreeSet<>(ALLOWED), found, "files that call getSystemClipboard()");
  }

  @Test
  void theMagnetListenerDoesNotLookUpTheClipboardWhenItsClassLoads() throws IOException {
    String code =
        Files.readString(
                Path.of(
                    "src/main/java/com/limegroup/gnutella/gui/search/MagnetClipboardListener.java"))
            .replaceAll("\\s+", "");
    assertFalse(
        code.contains("privatestaticfinalClipboardCLIPBOARD=Toolkit"),
        "MainFrame loads the class on the EDT at startup");
    assertFalse(code.contains("CLIPBOARD.setContents") || code.contains("(CLIPBOARD)"));
  }
}
