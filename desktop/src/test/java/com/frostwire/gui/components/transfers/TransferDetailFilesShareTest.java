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
import org.junit.jupiter.api.Test;

/**
 * Structural regression test: the per-file Share affordance in the transfer detail Files tab exists
 * (share button, complete-gated, creates and seeds a torrent from the file), so the old TODO asking
 * for it must be gone.
 */
class TransferDetailFilesShareTest {

  @Test
  void shareColumnTodoIsGone() throws Exception {
    String dataLine =
        readSource(
            "desktop/src/main/java/com/frostwire/gui/components/transfers/TransferDetailFilesDataLine.java");
    assertFalse(
        dataLine.contains("TODO"),
        "TransferDetailFilesDataLine must not carry the stale Share-column TODO");
  }

  @Test
  void shareRefusesMissingFiles() throws Exception {
    String renderer =
        readSource(
            "desktop/src/main/java/com/frostwire/gui/bittorrent/TransferDetailFilesActionsRenderer.java");
    assertTrue(
        renderer.contains("!file.exists()"),
        "share must refuse files that no longer exist on disk");
    String util = readSource("desktop/src/main/java/com/frostwire/gui/bittorrent/TorrentUtil.java");
    int start = util.indexOf("boolean dhtTrackedOnly,\n      TorrentType torrentType)");
    assertTrue(start >= 0, "makeTorrentAndDownload core not found");
    String body = util.substring(start, start + 2500);
    assertTrue(
        body.contains("!file.exists()"),
        "makeTorrentAndDownload must refuse missing files for all callers");
  }

  @Test
  void filesActionsRendererOffersShare() throws Exception {
    String renderer =
        readSource(
            "desktop/src/main/java/com/frostwire/gui/bittorrent/TransferDetailFilesActionsRenderer.java");
    assertTrue(renderer.contains("shareButton"), "renderer must have a share button");
    assertTrue(
        renderer.contains("TorrentUtil.makeTorrentAndDownload"),
        "share must create and seed a torrent from the file");
    assertTrue(
        renderer.contains("if (!transferItemHolder.complete)"),
        "share/play must be gated on the file being complete");
  }

  private static String readSource(String moduleRelative) throws Exception {
    String stripped =
        moduleRelative.startsWith("desktop/")
            ? moduleRelative.substring("desktop/".length())
            : moduleRelative;
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      for (String candidate : new String[] {moduleRelative, stripped}) {
        Path file = dir.resolve(candidate);
        if (Files.exists(file)) {
          return Files.readString(file);
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("source not found: " + moduleRelative);
  }
}
