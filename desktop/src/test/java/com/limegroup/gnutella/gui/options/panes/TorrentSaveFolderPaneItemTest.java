/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Applying a new torrent save folder must not ask libtorrent to move storage on the EDT. */
public class TorrentSaveFolderPaneItemTest {

  @Test
  public void moveTorrentStorageDoesNotRunOnTheEdt() throws Exception {
    File dir = new File("new-save-folder");
    AtomicReference<File> movedTo = new AtomicReference<>();
    AtomicBoolean movedOnEdt = new AtomicBoolean(true);
    CountDownLatch moved = new CountDownLatch(1);

    SwingUtilities.invokeAndWait(
        () ->
            TorrentSaveFolderPaneItem.moveTorrentStorage(
                dir,
                d -> {
                  movedTo.set(d);
                  movedOnEdt.set(SwingUtilities.isEventDispatchThread());
                  moved.countDown();
                }));

    assertTrue(moved.await(10, TimeUnit.SECONDS), "storage was never moved");
    assertEquals(dir, movedTo.get());
    assertFalse(movedOnEdt.get(), "moved storage on the EDT");
  }
}
