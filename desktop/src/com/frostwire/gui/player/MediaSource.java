/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.player;

import com.frostwire.alexandria.PlaylistItem;

import java.io.File;

/**
 * A wrapper for the source of an audio file that is currently playing
 */
public class MediaSource {
    /**
     * current audio source that is loaded in the music player
     */
    private final File file;
    private final String url;
    private final PlaylistItem playlistItem;
    // NOTE: these can be initialized by derived classes
    // to customize display text
    String titleText = "";
    String toolTipText = "";

    public MediaSource(File file) {
        if (file == null) {
            throw new NullPointerException("File cannot be null");
        }
        this.file = file;
        this.url = null;
        this.playlistItem = null;
        // initialize display text (File)
        titleText = this.file.getName();
        toolTipText = this.file.getAbsolutePath();
    }

    MediaSource(String url) {
        if (url == null) {
            throw new NullPointerException("Url cannot be null");
        }
        this.file = null;
        this.url = url;
        this.playlistItem = null;
        // initialize display text (URL)
        titleText = "internet "; // generic internet stream
        toolTipText = "";
    }

    public MediaSource(PlaylistItem playlistItem) {
        if (playlistItem == null) {
            throw new NullPointerException("PlaylistItem cannot be null");
        }
        this.file = null;
        this.url = null;
        this.playlistItem = playlistItem;
        // initialize display text (playlist)
        String artistName = playlistItem.getTrackArtist();
        String songTitle = playlistItem.getTrackTitle();
        String albumToolTip = (playlistItem.getTrackAlbum() != null && playlistItem.getTrackAlbum().length() > 0) ? " - " + playlistItem.getTrackAlbum() : "";
        String yearToolTip = (playlistItem.getTrackYear() != null && playlistItem.getTrackYear().length() > 0) ? " (" + playlistItem.getTrackYear() + ")" : "";
        titleText = artistName + " - " + songTitle;
        toolTipText = artistName + " - " + songTitle + albumToolTip + yearToolTip;
    }

    @Override
    public String toString() {
        String name;
        if (getFile() != null) {
            name = getFile().getName();
        } else {
            name = url;
        }
        return "[MediaSource@" + hashCode() + ": " + name + "]";
    }

    public File getFile() {
        return file;
    }

    public String getURL() {
        return url;
    }

    public PlaylistItem getPlaylistItem() {
        return playlistItem;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MediaSource)) {
            return false;
        }
        MediaSource o = (MediaSource) obj;
        if (file != null && o.file != null) {
            return file.equals(o.file);
        }
        if (url != null && o.url != null) {
            return url.equals(o.url);
        }
        return playlistItem != null && o.playlistItem != null && playlistItem.equals(o.playlistItem);
    }

    public String getTitleText() {
        return titleText;
    }

    @SuppressWarnings("unused")
    public String getToolTipText() {
        return toolTipText;
    }
}
