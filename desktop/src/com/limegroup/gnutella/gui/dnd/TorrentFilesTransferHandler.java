/*
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

package com.limegroup.gnutella.gui.dnd;

import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;

/**
 * FileTransferHandler that imports drags of local torrent files into frostwire
 * by starting downloads for them if all files of the drag are torrent files.
 */
public class TorrentFilesTransferHandler extends LimeTransferHandler {
    /**
     *
     */
    private static final long serialVersionUID = 5478003116391589602L;

    @Override
    public boolean canImport(JComponent c, DataFlavor[] flavors, DropInfo ddi) {
        return canImport(c, flavors);
    }

    @Override
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        return DNDUtils.contains(transferFlavors, DataFlavor.javaFileListFlavor)
                || DNDUtils.contains(transferFlavors, FileTransferable.URIFlavor);
    }

    @Override
    public boolean importData(JComponent c, Transferable t, DropInfo ddi) {
        return importData(c, t);
    }

    @Override
    public boolean importData(JComponent comp, Transferable t) {
        if (!canImport(comp, t.getTransferDataFlavors()))
            return false;
        try {
            File[] files = DNDUtils.getFiles(t);
            if (areAllTorrentFiles(files)) {
                if (files.length == 1) {
                    GUIMediator.instance().openTorrentFile(files[0], true);
                } else {
                    for (File file : files) {
                        GUIMediator.instance().openTorrentFile(file, false);
                    }
                }
                return true;
            }
        } catch (UnsupportedFlavorException | IOException e) {
        }
        return false;
    }

    // made package private for tests
    private boolean areAllTorrentFiles(File[] files) {
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".torrent")) {
                return false;
            }
        }
        return true;
    }
}
