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

import com.frostwire.alexandria.Playlist;
import com.frostwire.gui.library.LibraryPlaylists.LibraryPlaylistsListCell;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.mplayer.MediaPlaybackState;
import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.awt.*;

/**
 * @author gubatron
 * @author aldenml
 */
class LibraryIconList extends JList<Object> {
    private Image speaker;
    private Image loading;

    LibraryIconList(ListModel<Object> dataModel) {
        super(dataModel);
        loadIcons();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        MediaPlayer player = MediaPlayer.instance();
        if (player.getState() != MediaPlaybackState.Stopped &&
                player.getState() != MediaPlaybackState.Closed &&
                player.getState() != MediaPlaybackState.Failed) {
            if (player.getCurrentMedia() != null && player.getCurrentPlaylist() != null && player.getPlaylistFilesView() != null) {
                int index = getPlaylistIndex(player.getCurrentPlaylist());
                if (index != -1) {
                    paintIcon(g, speaker, index);
                }
            }
        }
        paintImportingIcons(g);
    }

    private void loadIcons() {
        speaker = GUIMediator.getThemeImage("speaker").getImage();
        loading = GUIMediator.getThemeImage("indeterminate_small_progress").getImage();
    }

    private void paintIcon(Graphics g, Image image, int index) {
        Rectangle rect = getUI().getCellBounds(this, index, index);
        Dimension lsize = rect.getSize();
        Point llocation = rect.getLocation();
        g.drawImage(image, llocation.x + lsize.width - speaker.getWidth(null) - 4, llocation.y + (lsize.height - speaker.getHeight(null)) / 2, null);
    }

    private int getPlaylistIndex(Playlist playlist) {
        int n = getModel().getSize();
        for (int i = 0; i < n; i++) {
            Object value = getModel().getElementAt(i);
            if (value instanceof LibraryPlaylistsListCell) {
                Playlist p = ((LibraryPlaylistsListCell) value).getPlaylist();
                if (p != null && p.equals(playlist)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void paintImportingIcons(Graphics g) {
        int n = getModel().getSize();
        for (int i = 0; i < n; i++) {
            Object value = getModel().getElementAt(i);
            if (value instanceof LibraryPlaylistsListCell) {
                Playlist p = ((LibraryPlaylistsListCell) value).getPlaylist();
                if (LibraryMediator.instance().getLibraryPlaylists().isPlaylistImporting(p)) {
                    paintIcon(g, loading, i);
                }
            } else {
                return;
            }
        }
    }
}
