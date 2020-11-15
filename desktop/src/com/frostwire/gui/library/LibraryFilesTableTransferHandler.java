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
package com.frostwire.gui.library;

import com.frostwire.alexandria.PlaylistItem;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.dnd.DNDUtils;
import com.limegroup.gnutella.gui.dnd.FileTransferable;
import com.limegroup.gnutella.gui.dnd.MulticastTransferHandler;

import javax.swing.*;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The Transfer Handler for any of the File tables in the library.
 *
 * @author gubatron
 * @author aldenml
 * @see LibraryFilesTransferHandler for the handler on the left hand side tree.
 */
class LibraryFilesTableTransferHandler extends TransferHandler {
    private final LibraryFilesTableMediator mediator;
    private final TransferHandler fallbackTransferHandler;

    LibraryFilesTableTransferHandler(LibraryFilesTableMediator mediator) {
        this.mediator = mediator;
        this.fallbackTransferHandler = new MulticastTransferHandler(DNDUtils.DEFAULT_TRANSFER_HANDLERS);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return canImport(support, true);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support, false)) {
            return fallbackTransferHandler.importData(support);
        }
        try {
            Transferable transferable = support.getTransferable();
            if (DNDUtils.contains(transferable.getTransferDataFlavors(), LibraryPlaylistsTableTransferable.ITEM_ARRAY)) {
                PlaylistItem[] playlistItems = LibraryUtils.convertToPlaylistItems((LibraryPlaylistsTableTransferable.Item[]) transferable
                        .getTransferData(LibraryPlaylistsTableTransferable.ITEM_ARRAY));
                LibraryUtils.createNewPlaylist(playlistItems);
            } else {
                File[] files = DNDUtils.getFiles(support.getTransferable());
                if (files.length == 1 && files[0].getAbsolutePath().endsWith(".m3u")) {
                    LibraryUtils.createNewPlaylist(files[0]);
                } else {
                    LibraryUtils.createNewPlaylist(files);
                }
            }
        } catch (Throwable e) {
            return fallbackTransferHandler.importData(support);
        }
        return false;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE | LINK;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        List<AbstractLibraryTableDataLine<File>> lines = mediator.getSelectedLines();
        List<File> files = new ArrayList<>(lines.size());
        for (AbstractLibraryTableDataLine<File> line : lines) {
            files.add(line.getFile());
        }
        return new FileTransferable(files);
    }

    private boolean canImport(TransferSupport support, boolean fallback) {
        support.setShowDropLocation(false);
        if (!mediator.getMediaType().equals(MediaType.getAudioMediaType())) {
            return fallback && fallbackTransferHandler.canImport(support);
        }
        return DNDUtils.supportCanImport(LibraryPlaylistsTableTransferable.ITEM_ARRAY, support, fallbackTransferHandler, fallback);
    }
}
