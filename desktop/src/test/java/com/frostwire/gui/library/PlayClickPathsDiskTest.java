/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Icebase #60d22d3398dd, frostwire/frostwire#1581: Play and Open run on the EDT, and {@code
 * PlaybackUtil.isPlayableFile(File)} asks the disk twice ({@code exists}, {@code isDirectory})
 * before looking at the extension. {@code playInOS} and the launcher open the file off the EDT and
 * report a missing one, so the EDT click paths only look at the name.
 */
class PlayClickPathsDiskTest {

  @Test
  void theEdtClickPathsDecideByTheFileNameOnly() throws IOException {
    String[][] paths = {
      {
        "src/main/java/com/limegroup/gnutella/gui/GUIUtils.java",
        "private static boolean launchFile(final File file, boolean isPlaying)"
      },
      {
        "src/main/java/com/frostwire/gui/library/LibraryFilesTableMediator.java",
        "public void handleActionKey()"
      },
      {
        "src/main/java/com/frostwire/gui/bittorrent/TransferDetailFilesActionsRenderer.java",
        "public void actionPerformed(ActionEvent e)"
      },
    };
    for (String[] path : paths) {
      String body = body(Files.readString(Path.of(path[0])), path[1]);
      String stripped = body.replaceAll("\\s+", "");
      assertTrue(stripped.contains("PlaybackUtil.isPlayableFile("), path[1] + " in " + path[0]);
      assertTrue(
          stripped.contains(".getName())"), path[1] + " in " + path[0] + " must use the name");
      assertFalse(stripped.contains("isPlayableFile(file)"), path[0] + " checks the disk");
      assertFalse(
          stripped.contains("isPlayableFile(line.getFile())"), path[0] + " checks the disk");
    }
  }

  /** The text of the first method at {@code signature} (after the last match), braces matched. */
  private static String body(String source, String signature) {
    int start = source.indexOf(signature);
    assertTrue(start >= 0, "method not found: " + signature);
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
}
