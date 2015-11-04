/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

import org.limewire.util.OSUtils;

import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.gui.library.LibraryPlaylistsTableTransferable.PlaylistItemContainer;
import com.frostwire.gui.player.MediaPlayer;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.dnd.DNDUtils;
import com.limegroup.gnutella.gui.dnd.MulticastTransferHandler;

class LibraryPlaylistsTableTransferHandler extends TransferHandler {

    private static final long serialVersionUID = -360187293186425556L;

    private final LibraryPlaylistsTableMediator mediator;
    private final TransferHandler fallbackTransferHandler;

    public LibraryPlaylistsTableTransferHandler(LibraryPlaylistsTableMediator mediator) {
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
                    
                } catch (Exception e) {
                    return false;
                }
            } else {
                
                int max = mediator.getTable().getModel().getRowCount();
                if (index < 0 || index > max)
                   index = max;
                
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
        } catch (Exception e) {
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
        List<PlaylistItem> playlistItems = new ArrayList<PlaylistItem>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            playlistItems.add(lines.get(i).getInitializeObject());
        }
        
        int[] selectedIndexes = mediator.getSelectedIndexes();
        return new LibraryPlaylistsTableTransferable(playlistItems, mediator.getCurrentPlaylist().getId(), selectedIndexes);
    }

    private boolean canImport(TransferSupport support, boolean fallback) {
        //support.setShowDropLocation(false);
        if (!mediator.getMediaType().equals(MediaType.getAudioMediaType())) {
            return fallback ? fallbackTransferHandler.canImport(support) : false;
        }
        if (mediator.getCurrentPlaylist() != null && mediator.getCurrentPlaylist().getId() == LibraryDatabase.STARRED_PLAYLIST_ID) {
            return false;
        }

        if (support.isDataFlavorSupported(LibraryPlaylistsTableTransferable.PLAYLIST_ITEM_ARRAY)) {
            
            Transferable transferable = support.getTransferable();
            PlaylistItemContainer container;
            
            try {
                container = (PlaylistItemContainer) transferable.getTransferData(LibraryPlaylistsTableTransferable.PLAYLIST_ITEM_ARRAY);
                if ( mediator.getCurrentPlaylist().getId() == container.playlistID &&
                     mediator.getDataModel().getSortColumn() == LibraryPlaylistsTableDataLine.SORT_INDEX_IDX &&
                     mediator.getDataModel().isSortAscending() ) {
                    
                    // only allow playlist item D&D when you are dragging files 
                    // within the same playlist and sorting ascending by the correct column
                    return true; 
                }
            } catch (Exception e) {
                // continue on with false return below
            }
            
        } else if (support.isDataFlavorSupported(LibraryPlaylistsTableTransferable.ITEM_ARRAY)) {
            return true;
        } else if (DNDUtils.containsFileFlavors(support.getDataFlavors())) {
        	if (OSUtils.isMacOSX()) {
        		return true;
        	}
            try {
                File[] files = DNDUtils.getFiles(support.getTransferable());
                for (File file : files) {
                    if (MediaPlayer.isPlayableFile(file)) {
                        return true;
                    } else if (file.isDirectory()) {
                        if (LibraryUtils.directoryContainsAudio(file)) {
                            return true;
                        }
                    }
                }
                if (files.length == 1 && files[0].getAbsolutePath().endsWith(".m3u")) {
                    return true;
                }
                return fallback ? fallbackTransferHandler.canImport(support) : false;
            } catch (InvalidDnDOperationException e) {
                // this case seems to be something special with the OS
                return true;
            } catch (Exception e) {
                return fallback ? fallbackTransferHandler.canImport(support) : false;
            }
        }

        return false;
    }
    
    private void importPlaylistItemArrayData(Transferable transferable, int index) throws UnsupportedFlavorException, IOException {
        PlaylistItem[] playlistItems = LibraryUtils.convertToPlaylistItems((LibraryPlaylistsTableTransferable.Item[]) transferable.getTransferData(LibraryPlaylistsTableTransferable.ITEM_ARRAY));
        LibraryUtils.asyncAddToPlaylist(mediator.getCurrentPlaylist(), playlistItems, index);
    }
}
