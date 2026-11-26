/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * GUIMediator.setAppVisible forced an invalidate and validate of the whole content pane after the
 * main window became visible, a Java 1.5 workaround that laid the window out a second time on the
 * EDT (Icebase #bf6bd56df8f5, #0079a1281dda, frostwire/frostwire#1552).
 */
public class AppVisibleDoesNotRevalidateTest {
  @Test
  public void setAppVisibleDoesNotRevalidateTheContentPane() throws IOException {
    String source =
        Files.readString(Path.of("src/main/java/com/limegroup/gnutella/gui/GUIMediator.java"))
            .replaceAll("\\s+", "");
    int start = source.indexOf("staticvoidsetAppVisible(finalbooleanvisible){");
    assertTrue(start >= 0, "setAppVisible moved: update this test");
    String method =
        source.substring(start, source.indexOf("showDialogsForFirstVisibility();", start));
    assertFalse(
        method.contains("getContentPane().validate()"),
        "setAppVisible validates the content pane again");
    assertFalse(
        method.contains("getContentPane().invalidate()"),
        "setAppVisible invalidates the content pane again");
    assertTrue(method.contains("SearchMediator.requestSearchFocus();"));
  }
}
