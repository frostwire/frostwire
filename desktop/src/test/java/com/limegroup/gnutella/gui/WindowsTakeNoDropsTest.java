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
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Only the main window takes drops. Every other window removes Swing's drop targets before it is
 * realized, or the OS registers it for drag-and-drop when it opens (frostwire/frostwire#1471). A
 * source scan, so a new window can't forget it: the windows need a display to be built.
 */
class WindowsTakeNoDropsTest {

  private static final Path SOURCES = Path.of("src/main/java");

  private static final Pattern CREATES_WINDOW =
      Pattern.compile(
          "new(JDialog|JFrame|JWindow)\\(|extends(JDialog|JFrame|JWindow)\\b|\\.createDialog\\(");

  /** Calls that remove a window's drop targets before it is realized. */
  private static final List<String> REMOVES_DROP_TARGETS =
      List.of(
          "DropTargets.removeAll(",
          // GUILoader's invalid-installation dialog: prepareDialog removes them, then packs.
          "FatalBugManager.prepareDialog(");

  /** Files that create windows and keep drop targets, and why. */
  private static final Map<String, String> TAKES_DROPS =
      Map.of(
          "com/limegroup/gnutella/gui/LimeJFrame.java",
          "the main window: torrents and files dropped on it, Transfers, Library, search results",
          "com/limegroup/gnutella/gui/actions/FileMenuActions.java",
          "Open .Torrent or Magnet: a magnet link or URL dragged from a browser into its field",
          "com/frostwire/gui/components/transfers/HexHivePanel.java",
          "a developer test harness main(), not part of the app");

  /** Source text without comments and whitespace. */
  private static String code(Path file) throws IOException {
    String s = Files.readString(file, StandardCharsets.UTF_8);
    s = s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    return s.replaceAll("\\s+", "");
  }

  private static List<Path> javaSources() throws IOException {
    try (Stream<Path> files = Files.walk(SOURCES)) {
      return files.filter(p -> p.toString().endsWith(".java")).toList();
    }
  }

  private static String name(Path file) {
    return SOURCES.relativize(file).toString().replace('\\', '/');
  }

  @Test
  void everyWindowButTheMainOneRemovesItsDropTargets() throws IOException {
    List<String> missing = new ArrayList<>();
    for (Path file : javaSources()) {
      String code = code(file);
      if (CREATES_WINDOW.matcher(code).find()
          && REMOVES_DROP_TARGETS.stream().noneMatch(code::contains)
          && !code.contains("classDropTargets")
          && !TAKES_DROPS.containsKey(name(file))) {
        missing.add(name(file));
      }
    }
    assertTrue(
        missing.isEmpty(),
        "these create a window without DropTargets.removeAll before it is realized: " + missing);
  }

  @Test
  void inputDialogsGoThroughDropTargets() throws IOException {
    List<String> direct = new ArrayList<>();
    for (Path file : javaSources()) {
      if (code(file).contains("JOptionPane.showInputDialog(")) {
        direct.add(name(file));
      }
    }
    assertTrue(
        direct.isEmpty(),
        "JOptionPane.showInputDialog realizes its text field's drop target; use"
            + " DropTargets.showInputDialog: "
            + direct);
  }

  @Test
  void fileChoosersAreNoDropFileChoosers() throws IOException {
    List<String> direct = new ArrayList<>();
    for (Path file : javaSources()) {
      if (code(file).contains("newJFileChooser(")
          && !name(file).equals("com/limegroup/gnutella/gui/NoDropFileChooser.java")) {
        direct.add(name(file));
      }
    }
    assertTrue(
        direct.isEmpty(),
        "a JFileChooser's dialog keeps its drop targets; use NoDropFileChooser: " + direct);
  }
}
