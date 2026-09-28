/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The test JVM is headless, so this guards the source shape of the bug that made the setup wizard
 * vanish the instant it appeared (verified on screen): {@code showDialog()} made the dialog
 * non-modal and then disposed the owner frame right away, which disposes the dialog with it.
 */
class FramedDialogTest {

  private static String showDialogBody() throws Exception {
    String source =
        Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/limegroup/gnutella/gui/FramedDialog.java"),
            StandardCharsets.UTF_8);
    String compact = source.replaceAll("\\s+", "");
    int start = compact.indexOf("publicvoidshowDialog(){");
    int end = compact.indexOf("publicJDialoggetDialog()");
    assertTrue(start >= 0 && end > start);
    return compact.substring(start, end);
  }

  @Test
  void showDialogNeverForcesTheDialogNonModal() throws Exception {
    assertFalse(showDialogBody().contains("setModal(false)"));
  }

  @Test
  void ownerFrameIsDisposedOnlyAfterTheDialogReturns() throws Exception {
    String body = showDialogBody();
    int shown = body.indexOf("dialog.setVisible(true);");
    int disposed = body.indexOf("finally{dispose();}");
    assertTrue(shown >= 0, "dialog must be shown");
    assertTrue(disposed > shown, "dispose() must run after setVisible returns, in a finally");
  }
}
