/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

/**
 * At startup the EDT built the Tip of the Day dialog before anything had decoded its icon, and
 * waited on the toolkit's image fetcher for the "question" PNG while the main window's images were
 * loading (Icebase #c4a9ac533a8d, GitHub #1462).
 */
class TipOfTheDayPreloadTest {

  @Test
  void thePreloadDecodesTheDialogsIconOffTheEdt() throws Exception {
    Thread worker = new Thread(TipOfTheDayMediator::preloadResources, "TOTD");
    worker.start();
    worker.join(30_000);

    ImageIcon preloaded = ResourceManager.getThemeImage(TipOfTheDayMediator.ICON_NAME);
    assertSame(
        preloaded,
        ResourceManager.getThemeImage(TipOfTheDayMediator.ICON_NAME),
        "the dialog gets the decoded icon from the cache");
  }

  /** Source check: showDialogsForFirstVisibility runs on the EDT inside the startup event. */
  @Test
  void theDialogIsBuiltOnlyAfterThePreload() throws Exception {
    String source =
        Files.readString(
                Path.of("src/main/java/com/limegroup/gnutella/gui/GUIMediator.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    String method =
        source.substring(source.indexOf("privatestaticvoidshowDialogsForFirstVisibility()"));
    method = method.substring(0, method.indexOf("staticvoidcloseStartupDialogs()"));
    int preload = method.indexOf("TipOfTheDayMediator.preloadResources()");
    int build = method.indexOf("TipOfTheDayMediator.instance()");
    assertTrue(preload >= 0, "the TOTD thread preloads the dialog's resources");
    assertTrue(preload < build, "the dialog is built after the preload");
    assertFalse(
        method.contains("TipOfTheDayMediator.instance();ThreadExecutor"),
        "no eager construction on the EDT before the TOTD thread");
  }
}
