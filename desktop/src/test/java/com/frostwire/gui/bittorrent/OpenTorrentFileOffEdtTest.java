/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Icebase #57095c0f3115: BTDownloadMediator.openTorrentFile runs its body on the EDT, and the
 * external control server can call it while FrostWire is still starting. It used to add the torrent
 * through {@code BTEngine.getInstance()} right there, which parks the EDT until startup sets up the
 * engine (and is libtorrent work on the EDT afterwards). The engine call must stay in the
 * background half. Source-level because the mediator cannot be built without the whole GUI;
 * whitespace-stripped so reformatting cannot hide a call.
 */
class OpenTorrentFileOffEdtTest {

  @Test
  void openTorrentFileReachesTheEngineOnlyFromTheBackgroundQueue() throws IOException {
    String source =
        Files.readString(
                Path.of("src/main/java/com/frostwire/gui/bittorrent/BTDownloadMediator.java"))
            .replaceAll("\\s+", "");
    int start = source.indexOf("publicvoidopenTorrentFile(");
    int end = source.indexOf("privatevoidaddOpenedTorrentFile(", start);
    assertTrue(start >= 0 && end > start, "openTorrentFile or its background half not found");
    String edtPart = source.substring(start, end);

    assertFalse(
        edtPart.contains("BTEngine.getInstance()"),
        "openTorrentFile must not reach BTEngine on the EDT");
    assertTrue(
        edtPart.contains("BackgroundQueuedExecutorService.schedule(()->addOpenedTorrentFile("),
        "openTorrentFile must hand the engine work to the background queue");
  }
}
