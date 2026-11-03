/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.dnd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * One dropped file or folder is the content of a torrent: the Send File drop on the main window and
 * the Create Torrent dialog both take it through this handler (frostwire/frostwire#1474).
 */
class FileOrFolderTransferHandlerTest {

  private final List<File> dropped = new CopyOnWriteArrayList<>();
  private final List<Boolean> onEdt = new CopyOnWriteArrayList<>();
  private final FileOrFolderTransferHandler handler =
      new FileOrFolderTransferHandler(
          f -> {
            dropped.add(f);
            onEdt.add(SwingUtilities.isEventDispatchThread());
          });

  @Test
  void oneFileOrFolderIsHandedOverOnTheEdt() throws Exception {
    File folder = new File("some/folder");

    assertTrue(handler.importData(null, new FileTransferable(List.of(folder))));
    SwingUtilities.invokeAndWait(() -> {});

    assertEquals(List.of(folder), dropped);
    assertEquals(List.of(true), onEdt);
  }

  @Test
  void severalFilesAreRejected() throws Exception {
    assertFalse(
        handler.importData(
            null, new FileTransferable(List.of(new File("a.txt"), new File("b.txt")))));
    SwingUtilities.invokeAndWait(() -> {});

    assertTrue(dropped.isEmpty());
  }

  @Test
  void textIsNotAFile() {
    Transferable text = new StringSelection("magnet:?xt=urn:btih:0123");

    assertFalse(handler.canImport(null, text.getTransferDataFlavors()));
    assertFalse(handler.importData(null, text));
    assertTrue(handler.canImport(null, new DataFlavor[] {DataFlavor.javaFileListFlavor}));
  }
}
