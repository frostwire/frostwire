/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
final class BTDownloadTransferHandler extends LimeTransferHandler {
    private static final Logger LOG = Logger.getLogger(BTDownloadTransferHandler.class);

    BTDownloadTransferHandler() {
        super(COPY);
    }

    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        return DNDUtils.contains(transferFlavors, SearchResultTransferable.dataFlavor) ||
                DNDUtils.DEFAULT_TRANSFER_HANDLER.canImport(comp, transferFlavors);
    }

    @Override
    public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
        return canImport(c, flavors);
    }

    public boolean importData(JComponent comp, Transferable t) {
        if (DNDUtils.contains(t.getTransferDataFlavors(), SearchResultTransferable.dataFlavor)) {
            try {
                SearchResultTransferable srt = (SearchResultTransferable) t.getTransferData(SearchResultTransferable.dataFlavor);
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
            List<File> filesToDrop = getListOfFilesFromBTDownloads(downloads);
            return new FileTransferable(filesToDrop);
        }
        return null;
    }

    private List<File> getListOfFilesFromBTDownloads(BTDownload[] downloads) {
        List<File> files = new LinkedList<>();
        Set<File> ignore = TorrentUtil.getIgnorableFiles();
        for (BTDownload download : downloads) {
            File saveLocation = download.getSaveLocation();
            addFilesRecursively(files, saveLocation, ignore);
        }
        return files;
    }

    private void addFilesRecursively(List<File> files, File saveLocation, Set<File> ignore) {
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
