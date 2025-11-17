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

package com.frostwire.gui.library.tags;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author aldenml
 */
class TagsParserFactory {
    private static final List<String> MP3_EXTENSIONS = Collections.singletonList("mp3");
    private static final List<String> M4A_EXTENSIONS = Collections.singletonList("m4a");
    private static final List<String> MP4_EXTENSIONS = Arrays.asList("mp4", "m4v", "mov", "3gp");
    private static final List<String> OGG_EXTENSIONS = Collections.singletonList("ogg");
    private static final List<String> FLAC_EXTENSIONS = Collections.singletonList("flac");
    private static final List<String> JAUDIOTAGGER_EXTENSIONS = Arrays.asList("wma", "wav");

    public TagsParser getInstance(File file) {
        String ext = FilenameUtils.getExtension(file.getName());
        if (isMP3(ext)) {
            return new MP3Parser(file);
        } else if (isM4A(ext)) {
            return new MP4Parser(file); // M4AParser(file);
        } else if (isMP4(ext)) {
            return new MP4Parser(file);
        } else if (isOgg(ext)) {
            return new OggParser(file);
        } else if (isFlac(ext)) {
            return new FlacParser(file);
        } else if (isJaudiotagger(ext)) {
            return new JaudiotaggerParser(file);
        } else {
            return null; // No parser available for this file type
        }
    }

    private boolean isMP3(String ext) {
        return MP3_EXTENSIONS.contains(ext);
    }

    private boolean isM4A(String ext) {
        return M4A_EXTENSIONS.contains(ext);
    }

    private boolean isMP4(String ext) {
        return MP4_EXTENSIONS.contains(ext);
    }

    private boolean isOgg(String ext) {
        return OGG_EXTENSIONS.contains(ext);
    }

    private boolean isFlac(String ext) {
        return FLAC_EXTENSIONS.contains(ext);
    }

    private boolean isJaudiotagger(String ext) {
        return JAUDIOTAGGER_EXTENSIONS.contains(ext);
    }
}
