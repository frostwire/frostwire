/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.frostwire.gui.bittorrent.TorrentFilesFolderComponent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Options Apply runs on the EDT and reaches every pane, so the Torrent Files folder must only be
 * checked on disk when the user changed it (Icebase #858f116569f1; frostwire/frostwire#1567).
 */
public class TorrentFilesFolderPaneItemTest {

  /** A component whose typed folder is {@code path}, recording every validation of it. */
  private static TorrentFilesFolderComponent component(String path, List<String> diskChecks) {
    return new TorrentFilesFolderComponent(false) {
      @Override
      public String getTorrentFilesFolderPath() {
        return path;
      }

      @Override
      public boolean isTorrentFilesFolderPathValid(boolean checkExist) {
        diskChecks.add(path);
        return super.isTorrentFilesFolderPathValid(checkExist);
      }
    };
  }

  @Test
  public void applyWithUnchangedFolderDoesNotCheckTheDisk(@TempDir Path dir) throws Exception {
    String folder = dir.toFile().getAbsolutePath();
    List<String> diskChecks = new CopyOnWriteArrayList<>();
    List<File> applied = new CopyOnWriteArrayList<>();
    AtomicReference<Boolean> unchanged = new AtomicReference<>();
    AtomicReference<Boolean> changed = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () -> {
          TorrentFilesFolderComponent component = component(folder, diskChecks);
          try {
            unchanged.set(
                TorrentFilesFolderPaneItem.applyIfChanged(
                    folder, component, e -> {}, applied::add));
            assertEquals(List.of(), diskChecks, "checked the unchanged folder on disk");
            changed.set(
                TorrentFilesFolderPaneItem.applyIfChanged(
                    folder + "-old", component, e -> {}, applied::add));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });

    assertEquals(Boolean.FALSE, unchanged.get());
    assertEquals(Boolean.TRUE, changed.get());
    assertEquals(List.of(folder), diskChecks, "a changed folder must still be checked");
    assertEquals(List.of(new File(folder)), applied);
  }

  @Test
  public void applyWithChangedMissingFolderIsRejected(@TempDir Path dir) throws Exception {
    String missing = dir.resolve("missing").toFile().getAbsolutePath();
    List<String> diskChecks = new CopyOnWriteArrayList<>();
    List<String> errors = new CopyOnWriteArrayList<>();
    List<File> applied = new CopyOnWriteArrayList<>();
    AtomicReference<Throwable> thrown = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () ->
            thrown.set(
                assertThrows(
                    IOException.class,
                    () ->
                        TorrentFilesFolderPaneItem.applyIfChanged(
                            dir.toFile().getAbsolutePath(),
                            component(missing, diskChecks),
                            errors::add,
                            applied::add))));

    assertEquals(List.of(missing), diskChecks);
    assertEquals(1, errors.size(), "the user was not told why");
    assertFalse(errors.get(0) == null || errors.get(0).isEmpty());
    assertEquals(List.of(), applied, "applied a folder that does not exist");
  }
}
