/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, 2013, FrostWire(R). All rights reserved.
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
import com.limegroup.gnutella.gui.dnd.FileTransferable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
final class LibraryPlaylistsTableTransferable implements Transferable {
    static final DataFlavor ITEM_ARRAY = new DataFlavor(LibraryPlaylistsTableTransferable.Item[].class, "LibraryPlaylistTransferable.Item Array");
    static final DataFlavor PLAYLIST_ITEM_ARRAY = new DataFlavor(LibraryPlaylistsTableTransferable.Item[].class, "LibraryPlaylistTransferable.PlaylistItemArray");
    private final List<LibraryPlaylistsTableTransferable.Item> items;
    private final int playlistID;
    private final FileTransferable fileTransferable;
    private final int[] selectedIndexes;

    LibraryPlaylistsTableTransferable(List<PlaylistItem> playlistItems, int playlistID, int[] selectedIndexes) {
        items = LibraryUtils.convertToItems(playlistItems);
        List<File> files = new ArrayList<>(items.size());
        for (PlaylistItem item : playlistItems) {
            files.add(new File(item.getFilePath()));
        }
        fileTransferable = new FileTransferable(files);
        this.playlistID = playlistID;
        this.selectedIndexes = selectedIndexes;
    }

    LibraryPlaylistsTableTransferable(List<PlaylistItem> playlistItems) {
        items = LibraryUtils.convertToItems(playlistItems);
        List<File> files = new ArrayList<>(items.size());
        int playlistId = -1;
        for (PlaylistItem item : playlistItems) {
            files.add(new File(item.getFilePath()));
            if (playlistId == -1 && item.getPlaylist() != null) {
                playlistId = item.getPlaylist().getId();
            }
        }
        fileTransferable = new FileTransferable(files);
        this.selectedIndexes = null;
        this.playlistID = playlistId;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        List<DataFlavor> list = new ArrayList<>(Arrays.asList(fileTransferable.getTransferDataFlavors()));
        list.add(ITEM_ARRAY);
        if (selectedIndexes != null) {
            list.add(PLAYLIST_ITEM_ARRAY);
        }
        return list.toArray(new DataFlavor[0]);
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        if (flavor.equals(PLAYLIST_ITEM_ARRAY)) {
            return (selectedIndexes != null);
        } else {
            return flavor.equals(ITEM_ARRAY) || fileTransferable.isDataFlavorSupported(flavor);
        }
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (flavor.equals(PLAYLIST_ITEM_ARRAY)) {
            return new PlaylistItemContainer(playlistID, selectedIndexes, items);
        } else if (flavor.equals(ITEM_ARRAY)) {
            return items.toArray(new Item[0]);
        } else {
            return fileTransferable.getTransferData(flavor);
        }
    }

    public static final class PlaylistItemContainer implements Serializable {
        public final List<Item> items;
        final int playlistID;
        final int[] selectedIndexes;

        PlaylistItemContainer(int playlistID, int[] selectedIndexes, List<Item> items) {
            this.playlistID = playlistID;
            this.selectedIndexes = selectedIndexes;
            this.items = items;
        }
    }

    public static final class Item implements Serializable {
        public int id;
        public String filePath;
        public String fileName;
        public long fileSize;
        public String trackTitle;
        public boolean starred;
        String fileExtension;
        float trackDurationInSecs;
        String trackArtist;
        String trackAlbum;
        String coverArtPath;
        String trackBitrate;
        String trackComment;
        String trackGenre;
        String trackNumber;
        String trackYear;

        Item() {
        }
    }
}
