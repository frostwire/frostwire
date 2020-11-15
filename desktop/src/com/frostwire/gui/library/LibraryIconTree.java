/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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
import com.frostwire.alexandria.db.LibraryDatabase;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.mplayer.MediaPlaybackState;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author gubatron
 * @author aldenml
 */
class LibraryIconTree extends JTree {
    private static final Logger LOG = Logger.getLogger(LibraryIconTree.class.getName());
    private Image speaker;

    private LibraryIconTree() {
        loadIcons();
    }

    public LibraryIconTree(TreeModel dataModel) {
        super(dataModel);
        loadIcons();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        try {
            MediaPlayer player = MediaPlayer.instance();
            MediaPlaybackState playerState = player.getState();
            if (playerState != MediaPlaybackState.Stopped &&
                    playerState != MediaPlaybackState.Closed &&
                    playerState != MediaPlaybackState.Failed) {
                if (player.getCurrentMedia() != null && player.getCurrentPlaylist() == null && player.getPlaylistFilesView() != null) {
                    TreePath path = getAudioPath();
                    if (path != null) {
                        paintIcon(g, speaker, path);
                    }
                } else if (player.getCurrentMedia() != null && player.getCurrentPlaylist() != null && player.getPlaylistFilesView() != null) {
                    TreePath path = getPlaylistPath(player.getCurrentPlaylist());
                    if (path != null) {
                        paintIcon(g, speaker, path);
                    }
                }
            }
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "Error painting the speaker icon", e);
        }
    }

    private void loadIcons() {
        speaker = GUIMediator.getThemeImage("speaker").getImage();
    }

    private void paintIcon(Graphics g, Image image, TreePath path) {
        Rectangle rect = getUI().getPathBounds(this, path);
        if (rect != null) {
            Dimension lsize = rect.getSize();
            Point llocation = rect.getLocation();
            g.drawImage(image, llocation.x + getWidth() - speaker.getWidth(null) - 4, llocation.y + (lsize.height - speaker.getHeight(null)) / 2, null);
        }
    }

    private TreePath getAudioPath() {
        Enumeration<?> e = ((LibraryNode) getModel().getRoot()).depthFirstEnumeration();
        while (e.hasMoreElements()) {
            LibraryNode node = (LibraryNode) e.nextElement();
            if (node instanceof DirectoryHolderNode) {
                DirectoryHolder holder = ((DirectoryHolderNode) node).getDirectoryHolder();
                if (holder instanceof MediaTypeSavedFilesDirectoryHolder && ((MediaTypeSavedFilesDirectoryHolder) holder).getMediaType().equals(MediaType.getAudioMediaType())) {
                    return new TreePath(node.getPath());
                }
            }
        }
        return null;
    }

    private TreePath getPlaylistPath(Playlist playlist) {
        if (playlist.getId() == LibraryDatabase.STARRED_PLAYLIST_ID) {
            Enumeration<?> e = ((LibraryNode) getModel().getRoot()).depthFirstEnumeration();
            while (e.hasMoreElements()) {
                LibraryNode node = (LibraryNode) e.nextElement();
                if (node instanceof DirectoryHolderNode) {
                    DirectoryHolder holder = ((DirectoryHolderNode) node).getDirectoryHolder();
                    if (holder instanceof StarredDirectoryHolder) {
                        return new TreePath(node.getPath());
                    }
                }
            }
        }
        return null;
    }
}
