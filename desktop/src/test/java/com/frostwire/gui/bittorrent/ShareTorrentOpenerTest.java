/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The share button and "Send to friend" read the transfer's torrent through jlibtorrent on the EDT
 * (Icebase #a32170a9ed67, GitHub #1458). The read now runs on a background worker; only the dialog
 * opens on the EDT.
 */
@Timeout(15)
class ShareTorrentOpenerTest {

  @Test
  void theTorrentIsReadOffTheEdtAndTheDialogOpensOnIt() throws Exception {
    AtomicBoolean readOnEdt = new AtomicBoolean(true);
    AtomicBoolean shownOnEdt = new AtomicBoolean();
    AtomicReference<String> shownTorrent = new AtomicReference<>();
    CountDownLatch shown = new CountDownLatch(1);

    // Clicked on the EDT, as the share button and the menu action are.
    SwingUtilities.invokeAndWait(
        () ->
            ShareTorrentOpener.open(
                () -> {
                  readOnEdt.set(SwingUtilities.isEventDispatchThread());
                  return "fixture torrent";
                },
                task -> new Thread(task, "fixture-background").start(),
                torrent -> {
                  shownOnEdt.set(SwingUtilities.isEventDispatchThread());
                  shownTorrent.set(torrent);
                  shown.countDown();
                }));

    assertTrue(shown.await(10, TimeUnit.SECONDS), "the dialog opens");
    assertFalse(readOnEdt.get(), "the torrent must not be read on the EDT");
    assertTrue(shownOnEdt.get(), "the dialog opens on the EDT");
    assertEquals("fixture torrent", shownTorrent.get());
  }

  @Test
  void aTorrentThatCantBeReadOpensNothing() throws Exception {
    AtomicBoolean shown = new AtomicBoolean();
    ShareTorrentOpener.<String>open(
        () -> {
          throw new IllegalArgumentException("fixture: the .torrent was moved");
        },
        Runnable::run,
        torrent -> shown.set(true));
    ShareTorrentOpener.<String>open(() -> null, Runnable::run, torrent -> shown.set(true));
    SwingUtilities.invokeAndWait(() -> {});

    assertFalse(shown.get());
  }

  @Test
  void bothShareEntryPointsUseTheOpener() throws Exception {
    Path main = Path.of(System.getProperty("user.dir")).resolve("src/main/java");
    for (String file :
        new String[] {
          "com/frostwire/gui/bittorrent/BittorrentDownload.java",
          "com/frostwire/gui/bittorrent/BTDownloadActions.java"
        }) {
      String source =
          Files.readString(main.resolve(file), StandardCharsets.UTF_8).replaceAll("\\s+", "");
      assertFalse(source.contains("newShareTorrentDialog("), file + " opens the dialog itself");
      assertTrue(source.contains("ShareTorrentOpener.open("), file);
    }
  }
}
