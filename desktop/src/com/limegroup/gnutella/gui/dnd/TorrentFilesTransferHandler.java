/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
