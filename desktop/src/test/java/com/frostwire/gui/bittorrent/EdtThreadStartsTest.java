/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Starting a thread can wait on the JVM, so code that runs on the EDT hands work to an executor
 * whose workers are already running (GitHub #1457). These classes started threads from menu
 * actions, a renderer's click handler and a rebuild requested on the EDT.
 */
class EdtThreadStartsTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "com/frostwire/gui/components/slides/MultimediaSlideshowPanel.java",
        "com/frostwire/gui/bittorrent/BTDownloadMediatorAdvancedMenuFactory.java",
        "com/frostwire/gui/bittorrent/TransferDetailFilesActionsRenderer.java",
        "com/frostwire/gui/components/transfers/TransferDetailFiles.java",
        // Its warm-up is started from component constructors on the EDT (#1411).
        "com/limegroup/gnutella/gui/ShellFolderWarmUp.java"
      })
  void edtCodeDoesNotStartThreads(String file) throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir")).resolve("src/main/java").resolve(file),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertFalse(source.contains("newThread("), file + " starts a thread; use an executor");
  }
}
