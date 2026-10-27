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

import com.frostwire.gui.bittorrent.TorrentSaveFolderComponent;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

  /** Options Apply runs on the EDT; an unchanged save folder must not be checked on disk. */
  @Test
  public void applyWithUnchangedFolderDoesNotCheckTheDisk() throws Exception {
    List<String> diskChecks = new CopyOnWriteArrayList<>();
    List<File> applied = new CopyOnWriteArrayList<>();
    AtomicReference<Boolean> unchanged = new AtomicReference<>();
    AtomicReference<Boolean> changed = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () -> {
          TorrentSaveFolderComponent component =
              new TorrentSaveFolderComponent(false) {
                @Override
                public String getTorrentSaveFolderPath() {
                  return "/downloads";
                }

                @Override
                public boolean isTorrentSaveFolderPathValid(boolean checkExist) {
                  diskChecks.add(Thread.currentThread().getName());
                  return true;
                }
              };
          try {
            unchanged.set(
                TorrentSaveFolderPaneItem.applyIfChanged("/downloads", component, applied::add));
            assertEquals(List.of(), diskChecks, "checked the unchanged save folder on disk");
            changed.set(TorrentSaveFolderPaneItem.applyIfChanged("/old", component, applied::add));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

    assertEquals(Boolean.FALSE, unchanged.get());
    assertEquals(Boolean.TRUE, changed.get());
    assertEquals(1, diskChecks.size(), "a changed folder must still be checked");
    assertEquals(List.of(new File("/downloads")), applied);
  }
}
