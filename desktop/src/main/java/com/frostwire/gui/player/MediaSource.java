/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.player;

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

    // NOTE: these can be initialized by derived classes
    // to customize display text
    String titleText;
    String toolTipText;

    public MediaSource(File file) {
        if (file == null) {
            throw new NullPointerException("File cannot be null");
        }
        this.file = file;
        this.url = null;
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

        // initialize display text (URL)
        titleText = "internet "; // generic internet stream
        toolTipText = "";
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

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MediaSource o)) {
            return false;
        }
        if (file != null && o.file != null) {
            return file.equals(o.file);
        }
        if (url != null && o.url != null) {
            return url.equals(o.url);
        }
        return false;
    }

    @SuppressWarnings("unused")
    public String getToolTipText() {
        return toolTipText;
    }

    public boolean isFile() {
        return file != null;
    }

    public boolean isURL() {
        return url != null;
    }
}
