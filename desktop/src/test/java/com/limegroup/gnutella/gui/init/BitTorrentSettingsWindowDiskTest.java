/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.settings.SharingSettings;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Clicking Next on the setup wizard's BitTorrent page checked and created the Torrent Data folder
 * on the EDT, which froze the wizard on a slow disk (Icebase #9325f45dd41f). The page must only
 * read the typed folder on the EDT and leave the disk to the background.
 */
@Timeout(30)
class BitTorrentSettingsWindowDiskTest {

  @TempDir Path tmp;

  private final List<Runnable> queued = new CopyOnWriteArrayList<>();
  private final AtomicBoolean finished = new AtomicBoolean();
  private final AtomicReference<String> error = new AtomicReference<>();
  private File savedFolder;

  @BeforeEach
  void rememberFolder() {
    savedFolder = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
  }

  @AfterEach
  void restoreFolder() {
    SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(savedFolder);
  }

  @Test
  void nextCreatesTheTorrentDataFolderOffTheEdt() throws Exception {
    File folder = tmp.resolve("Torrent Data").toFile();

    leavePage(folder);

    assertFalse(folder.exists(), "the folder must not be created on the EDT");
    assertFalse(finished.get(), "the wizard waits for the folder before it leaves the page");
    assertEquals(1, queued.size(), "the folder is checked and created by the background queue");

    queued.get(0).run(); // the test thread, off the EDT
    SwingUtilities.invokeAndWait(() -> {});

    assertTrue(folder.isDirectory());
    assertTrue(finished.get());
    assertNull(error.get());
    assertEquals(folder, SharingSettings.TORRENT_DATA_DIR_SETTING.getValue());
  }

  @Test
  void aFolderThatCannotBeCreatedKeepsTheUserOnThePage() throws Exception {
    File notAFolder = Files.createFile(tmp.resolve("file")).toFile();
    File folder = new File(notAFolder, "Torrent Data");

    leavePage(folder);
    queued.get(0).run();
    SwingUtilities.invokeAndWait(() -> {});

    assertTrue(finished.get());
    assertNotNull(error.get(), "the error is shown instead of moving on");
    assertTrue(error.get().contains(folder.toString()), error.get());
    assertEquals(savedFolder, SharingSettings.TORRENT_DATA_DIR_SETTING.getValue());
  }

  /** Types {@code folder} on the page, picks seeding and applies it on the EDT, as Next does. */
  private void leavePage(File folder) throws Exception {
    SetupManager manager =
        new SetupManager(() -> SetupManager.Pages.NONE, status -> {}, queued::add);
    SwingUtilities.invokeAndWait(
        () ->
            manager.applySettings(
                new TypedPage(manager, folder),
                true,
                e -> {
                  error.set(e);
                  finished.set(true);
                }));
  }

  /** The BitTorrent page with a folder typed and seeding picked; its theme can't load headless. */
  private static final class TypedPage extends BitTorrentSettingsWindow {
    private final File folder;

    TypedPage(SetupManager manager, File folder) {
      super(manager);
      this.folder = folder;
    }

    @Override
    String torrentDataFolderPath() {
      return folder.getAbsolutePath();
    }

    @Override
    Boolean seedingChoice() {
      return Boolean.TRUE;
    }
  }
}
