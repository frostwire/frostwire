/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.dnd;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Takes one dropped file or folder, the content of one torrent, and hands it to {@code onDropped}
 * on the EDT, after the drop has completed. Rejects a drop of several files.
 */
public class FileOrFolderTransferHandler extends LimeTransferHandler {

  private final Consumer<File> onDropped;

  public FileOrFolderTransferHandler(Consumer<File> onDropped) {
    if (onDropped == null) {
      throw new NullPointerException("onDropped must not be null");
    }
    this.onDropped = onDropped;
  }

  @Override
  public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
    return canImport(c, flavors);
  }

  @Override
  public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
    return DNDUtils.containsFileFlavors(transferFlavors);
  }

  @Override
  public boolean importData(JComponent c, Transferable t, DropInfo ddi) {
    return importData(c, t);
  }

  @Override
  public boolean importData(JComponent comp, Transferable t) {
    if (!canImport(comp, t.getTransferDataFlavors())) {
      return false;
    }
    try {
      final File[] files = DNDUtils.getFiles(t);
      if (files.length == 1) {
        SwingUtilities.invokeLater(() -> onDropped.accept(files[0]));
        return true;
      }
    } catch (Throwable ignored) {
      // Not a readable file list.
    }
    return false;
  }
}
