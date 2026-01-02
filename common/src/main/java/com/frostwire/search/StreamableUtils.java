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

package com.frostwire.search;

import org.apache.commons.io.FilenameUtils;

import java.util.Arrays;

/**
 * Utility class for determining if files are streamable media.
 * Extracted from WebSearchPerformer to reduce coupling.
 */
public final class StreamableUtils {
    private static final String[] STREAMABLE_EXTENSIONS = new String[]{
            "mp3", "ogg", "wma", "wmv", "m4a", "aac", "flac",
            "mp4", "flv", "mov", "mpg", "mpeg", "3gp", "m4v", "webm"
    };

    private StreamableUtils() {
        // utility class
    }

    /**
     * Determines if a filename has a streamable media extension.
     *
     * @param filename the filename to check
     * @return true if the file extension is a streamable media type
     */
    public static boolean isStreamable(String filename) {
        return Arrays.asList(STREAMABLE_EXTENSIONS).contains(FilenameUtils.getExtension(filename));
    }
}
