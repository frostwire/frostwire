/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.dnd.DNDUtils;
import com.limegroup.gnutella.gui.dnd.DropInfo;
import com.limegroup.gnutella.gui.dnd.FileTransferable;
import com.limegroup.gnutella.gui.dnd.LimeTransferHandler;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.gui.search.SearchResultDataLine;
import com.limegroup.gnutella.gui.search.SearchResultMediator;
import com.limegroup.gnutella.gui.search.SearchResultTransferable;
import com.limegroup.gnutella.gui.util.DesktopParallelExecutor;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
final class BTDownloadTransferHandler extends LimeTransferHandler {
  private static final Logger LOG = Logger.getLogger(BTDownloadTransferHandler.class);

  BTDownloadTransferHandler() {
    super(COPY);
  }

  /**
   * Turns down drags that started on this table: dropping a transfer back on the transfers list
   * does nothing useful, and the default handlers would list the transfer's files on the EDT to
   * find out (Icebase #ea5453ae3ccf, GitHub #1511).
   */
  private static boolean isTransfersDrag(DataFlavor[] flavors) {
    return DNDUtils.contains(flavors, FileTransferable.LISTED_ON_DROP_FLAVOR);
  }

  public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
    if (isTransfersDrag(transferFlavors)) {
      return false;
    }
    return DNDUtils.contains(transferFlavors, SearchResultTransferable.dataFlavor)
        || DNDUtils.DEFAULT_TRANSFER_HANDLER.canImport(comp, transferFlavors);
  }

  @Override
  public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
    return canImport(c, flavors);
  }

  public boolean importData(JComponent comp, Transferable t) {
    if (isTransfersDrag(t.getTransferDataFlavors())) {
      return false;
    }
    if (DNDUtils.contains(t.getTransferDataFlavors(), SearchResultTransferable.dataFlavor)) {
      try {
        SearchResultTransferable srt =
            (SearchResultTransferable) t.getTransferData(SearchResultTransferable.dataFlavor);
        SearchResultMediator rp = srt.getResultPanel();
        SearchResultDataLine[] lines = srt.getTableLines();
        SearchMediator.downloadFromPanel(rp, lines);
        return true;
      } catch (Throwable e) {
        LOG.error("Error importing DnD data", e);
      }
    }
    return DNDUtils.DEFAULT_TRANSFER_HANDLER.importData(comp, t);
  }

  @Override
  public boolean importData(JComponent c, Transferable t, DropInfo ddi) {
    return importData(c, t);
  }

  @Override
  protected Transferable createTransferable(JComponent c) {
    BTDownload[] downloads = BTDownloadMediator.instance().getSelectedBTDownloads();
    if (downloads.length > 0) {
      return transferableFor(downloads, TorrentUtil::getIgnorableFiles);
    }
    return null;
  }

  /**
   * Walking a download's folder (and finding its incomplete parts) touches the disk, so it never
   * runs on the EDT when the drag starts, and it starts on a background thread, not when a drop
   * target asks. macOS asks on the EDT (the toolkit translates the data in a PeerEvent), so the
   * walk started then froze the window for as long as the folder took to list (Icebase
   * #5ca136ac5d64: 2.4 s of EDT CPU); by the time the user drops, the background walk is usually
   * done and the target gets the list at once.
   */
  static FileTransferable transferableFor(BTDownload[] downloads, Supplier<Set<File>> ignorable) {
    BTDownload[] dragged = downloads.clone();
    FutureTask<List<File>> listing = new FutureTask<>(() -> listFiles(dragged, ignorable.get()));
    DesktopParallelExecutor.execute(listing);
    return FileTransferable.listedOnDrop(() -> listed(listing), () -> saveLocations(dragged));
  }

  /** The background walk's files, waiting for it if the target asked before it finished. */
  private static List<File> listed(FutureTask<List<File>> listing) {
    try {
      return listing.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new ArrayList<>();
    } catch (ExecutionException e) {
      LOG.error("Error listing the dragged transfers' files", e);
      return new ArrayList<>();
    }
  }

  /** The transfers' own folders or files, for the drag image: known without touching the disk. */
  private static List<File> saveLocations(BTDownload[] downloads) {
    List<File> locations = new ArrayList<>();
    for (BTDownload download : downloads) {
      File saveLocation = download.getSaveLocation();
      if (saveLocation != null) {
        locations.add(saveLocation);
      }
    }
    return locations;
  }

  private static List<File> listFiles(BTDownload[] downloads, Set<File> ignore) {
    List<File> files = new ArrayList<>();
    for (BTDownload download : downloads) {
      File saveLocation = download.getSaveLocation();
      if (saveLocation != null) {
        addFilesRecursively(files, saveLocation, ignore);
      }
    }
    return files;
  }

  private static void addFilesRecursively(List<File> files, File saveLocation, Set<File> ignore) {
    if (saveLocation.isFile()) {
      if (!ignore.contains(saveLocation)) {
        files.add(saveLocation);
      }
    } else if (saveLocation.isDirectory()) {
      File[] listFiles = saveLocation.listFiles();
      if (listFiles != null) {
        for (File f : listFiles) {
          addFilesRecursively(files, f, ignore);
        }
      }
    }
  }
}
