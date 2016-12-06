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

import com.frostwire.alexandria.Playlist;
import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.alexandria.TransferHandlerUtils;
import com.frostwire.gui.library.LibraryPlaylists.LibraryPlaylistsListCell;
import com.limegroup.gnutella.gui.dnd.DNDUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.io.File;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
class LibraryPlaylistsTransferHandler extends TransferHandler {

    private final JList<Object> list;

    public LibraryPlaylistsTransferHandler(JList<Object> list) {
        this.list = list;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return TransferHandlerUtils.supportCanImport(LibraryPlaylistsTableTransferable.ITEM_ARRAY, support, null, false);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }

        DropLocation location = support.getDropLocation();
        int index = list.locationToIndex(location.getDropPoint());
        if (index != -1) {
            Rectangle rect = list.getUI().getCellBounds(list, index, index);
            if (!rect.contains(location.getDropPoint())) {
                index = 0;
            }
            LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) list.getModel().getElementAt(index);

            //Playlist selectedPlaylist = getSelectedPlaylist();
            Playlist playlist = cell.getPlaylist();
            if (playlist == null) {
                try {
                    Transferable transferable = support.getTransferable();
                    if (DNDUtils.contains(transferable.getTransferDataFlavors(), LibraryPlaylistsTableTransferable.ITEM_ARRAY)) {
                        Object transferData = transferable.getTransferData(LibraryPlaylistsTableTransferable.ITEM_ARRAY);
                        
                        PlaylistItem[] playlistItems = null;
                        
                        if (transferData instanceof LibraryPlaylistsTableTransferable.Item[]) {
                            playlistItems = LibraryUtils.convertToPlaylistItems((LibraryPlaylistsTableTransferable.Item[]) transferData);
                        } else if (transferData instanceof LibraryPlaylistsTableTransferable.PlaylistItemContainer) {
                            playlistItems = LibraryUtils.convertToPlaylistItems((LibraryPlaylistsTableTransferable.PlaylistItemContainer) transferData);
                        }
                        
                        if (playlistItems != null) {
                            LibraryUtils.createNewPlaylist(playlistItems);
                        }
                    } else {
                        File[] files = DNDUtils.getFiles(support.getTransferable());
                        if (files.length == 1 && files[0].getAbsolutePath().endsWith(".m3u")) {
                            LibraryUtils.createNewPlaylist(files[0]);
                        } else {
                            LibraryUtils.createNewPlaylist(files);
                        }
                    }
                    list.setSelectedIndex(list.getModel().getSize() - 1);
                    LibraryMediator.instance().getLibraryPlaylists().refreshSelection();
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            } else {
                // importing to starred playlist
                try {
                    Transferable transferable = support.getTransferable();
                    if (DNDUtils.contains(transferable.getTransferDataFlavors(), LibraryPlaylistsTableTransferable.ITEM_ARRAY)) {
                        PlaylistItem[] playlistItems = LibraryUtils.convertToPlaylistItems((LibraryPlaylistsTableTransferable.PlaylistItemContainer) transferable.getTransferData(LibraryPlaylistsTableTransferable.ITEM_ARRAY));
                        LibraryUtils.asyncAddToPlaylist(playlist, playlistItems);
                    } else {
                        File[] files = DNDUtils.getFiles(support.getTransferable());
                        if (files.length == 1 && files[0].getAbsolutePath().endsWith(".m3u")) {
                            LibraryUtils.asyncAddToPlaylist(playlist, files[0]);
                        } else {
                            // importing a regular file
                            LibraryUtils.asyncAddToPlaylist(playlist, files);
                        }
                    }
                    //_list.setSelectedIndex(index);
                    //refreshSelection();
                } catch (Exception e) {
                    return false;
                }
            }
        } else {
            return false;
        }

        return false;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE | LINK;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        LibraryPlaylistsListCell cell = (LibraryPlaylistsListCell) list.getSelectedValue();
        if (cell != null && cell.getPlaylist() != null && cell.getPlaylist().getItems().size() > 0) {
            return new LibraryPlaylistsTableTransferable(cell.getPlaylist().getItems());
        } else {
            return null;
        }
    }
}
