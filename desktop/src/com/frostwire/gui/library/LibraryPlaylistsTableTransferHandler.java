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
import com.frostwire.gui.library.LibraryPlaylistsTableTransferable.PlaylistItemContainer;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.dnd.DNDUtils;
import com.limegroup.gnutella.gui.dnd.MulticastTransferHandler;

import javax.swing.*;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Transfer Handler for the playlist tables on the right hand side of the library.
 *
 * @author aldenml
 * @author gubatron
 * @see LibraryPlaylistsTransferHandler for drag and drop handling on the left hand side playlist items.
 */
class LibraryPlaylistsTableTransferHandler extends TransferHandler {
    private final LibraryPlaylistsTableMediator mediator;
    private final TransferHandler fallbackTransferHandler;

    LibraryPlaylistsTableTransferHandler(LibraryPlaylistsTableMediator mediator) {
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
        JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
        int index = dl.getRow();
        try {
            if (support.isDataFlavorSupported(LibraryPlaylistsTableTransferable.PLAYLIST_ITEM_ARRAY)) {
                Transferable transferable = support.getTransferable();
                PlaylistItemContainer container;
                try {
                    container = (PlaylistItemContainer) transferable.getTransferData(LibraryPlaylistsTableTransferable.PLAYLIST_ITEM_ARRAY);
                    LibraryUtils.movePlaylistItemsToIndex(mediator.getCurrentPlaylist(), container.selectedIndexes, index);
                } catch (Throwable e) {
                    return false;
                }
            } else {
                int max = mediator.getTable().getModel().getRowCount();
                if (index < 0 || index > max) {
                    index = max;
                }
                Transferable transferable = support.getTransferable();
                if (DNDUtils.contains(transferable.getTransferDataFlavors(), LibraryPlaylistsTableTransferable.ITEM_ARRAY)) {
                    if (mediator.getCurrentPlaylist() != null) {
                        importPlaylistItemArrayData(transferable, index);
                    }
                } else {
                    if (mediator.getCurrentPlaylist() != null) {
                        File[] files = DNDUtils.getFiles(support.getTransferable());
                        if (files.length == 1 && files[0].getAbsolutePath().endsWith(".m3u")) {
                            LibraryUtils.asyncAddToPlaylist(mediator.getCurrentPlaylist(), files[0], index);
                        } else {
                            LibraryUtils.asyncAddToPlaylist(mediator.getCurrentPlaylist(), files, index);
                        }
                    }
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
        List<AbstractLibraryTableDataLine<PlaylistItem>> lines = mediator.getSelectedLines();
        List<PlaylistItem> playlistItems = new ArrayList<>(lines.size());
        for (AbstractLibraryTableDataLine<PlaylistItem> line : lines) {
            playlistItems.add(line.getInitializeObject());
        }
        int[] selectedIndexes = mediator.getSelectedIndexes();
        return new LibraryPlaylistsTableTransferable(playlistItems, mediator.getCurrentPlaylist().getId(), selectedIndexes);
    }

    private boolean canImport(TransferSupport support, boolean fallback) {
        if (!mediator.getMediaType().equals(MediaType.getAudioMediaType())) {
            return fallback && fallbackTransferHandler.canImport(support);
        }
        if (support.isDataFlavorSupported(LibraryPlaylistsTableTransferable.PLAYLIST_ITEM_ARRAY)) {
            Transferable transferable = support.getTransferable();
            PlaylistItemContainer container;
            try {
                container = (PlaylistItemContainer) transferable.getTransferData(LibraryPlaylistsTableTransferable.PLAYLIST_ITEM_ARRAY);
                if (mediator.getCurrentPlaylist().getId() == container.playlistID &&
                        mediator.getDataModel().getSortColumn() == LibraryPlaylistsTableDataLine.SORT_INDEX_IDX &&
                        mediator.getDataModel().isSortAscending()) {
                    // only allow playlist item D&D when you are dragging files
                    // within the same playlist and sorting ascending by the correct column
                    return true;
                }
            } catch (Throwable e) {
                // continue on with false return below
            }
        }
        return DNDUtils.supportCanImport(LibraryPlaylistsTableTransferable.ITEM_ARRAY, support, fallbackTransferHandler, true);
    }

    private void importPlaylistItemArrayData(Transferable transferable, int index) throws UnsupportedFlavorException, IOException {
        PlaylistItem[] playlistItems = LibraryUtils.convertToPlaylistItems((LibraryPlaylistsTableTransferable.Item[]) transferable.getTransferData(LibraryPlaylistsTableTransferable.PLAYLIST_ITEM_ARRAY));
        LibraryUtils.asyncAddToPlaylist(mediator.getCurrentPlaylist(), playlistItems, index);
    }
}
