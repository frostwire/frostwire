/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.limegroup.gnutella.util.Launcher;
import java.io.File;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * On macOS "Explore" selects the file in the Finder through osascript. File names come from
 * torrents, so a name with a double quote must not be able to close the AppleScript string literal
 * and append its own commands (e.g. {@code do shell script}): the path is passed as an argument.
 */
class LauncherFinderScriptTest {

  @Test
  void pathIsPassedAsAnArgumentNotSplicedIntoTheScript() throws Exception {
    File hostile =
        new File(
            System.getProperty("java.io.tmpdir"),
            "a\" & (do shell script \"touch /tmp/frostwire-pwned\") & \"b.mp3");

    String[] command = selectFileCommand(hostile);

    assertEquals("osascript", command[0]);
    assertEquals(hostile.getCanonicalPath(), command[command.length - 1]);
    for (int i = 0; i < command.length - 1; i++) {
      assertFalse(
          command[i].contains("do shell script"),
          "the file name leaked into the AppleScript source: " + command[i]);
    }
  }

  private static String[] selectFileCommand(File file) throws Exception {
    Method method = Launcher.class.getDeclaredMethod("selectFileCommand", File.class);
    method.setAccessible(true);
    return (String[]) method.invoke(null, file);
  }
}
