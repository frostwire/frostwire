/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
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
