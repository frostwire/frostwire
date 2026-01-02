/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.util;

import org.limewire.util.FileUtils;

import java.io.File;

/**
 * Utility class for audio/media playback support
 * Replaced custom MediaPlayer with OS default player
 */
public class PlaybackUtil {

    private static final String[] PLAYABLE_EXTENSIONS = new String[]{
        "mp3", "ogg", "wav", "wma", "wmv", "m4a", "aac", "flac", "mp4", "flv",
        "avi", "mov", "mkv", "mpg", "mpeg", "3gp", "m4v", "webm"
    };

    /**
     * Get list of playable file extensions
     */
    public static String[] getPlayableExtensions() {
        return PLAYABLE_EXTENSIONS;
    }

    /**
     * Check if a file is playable
     */
    public static boolean isPlayableFile(File file) {
        return file != null && file.exists() && !file.isDirectory() &&
               FileUtils.hasExtension(file.getAbsolutePath(), PLAYABLE_EXTENSIONS);
    }

    /**
     * Check if a file path is playable
     */
    public static boolean isPlayableFile(String filename) {
        return filename != null && FileUtils.hasExtension(filename, PLAYABLE_EXTENSIONS);
    }

    /**
     * Check if a file is currently being played
     * With OS default player, we cannot track this accurately
     */
    public static boolean isThisBeingPlayed(String filename) {
        return false; // Cannot track playback with OS default player
    }

    /**
     * Check if a file is currently being played
     * With OS default player, we cannot track this accurately
     */
    public static boolean isThisBeingPlayed(File file) {
        return false; // Cannot track playback with OS default player
    }
}
