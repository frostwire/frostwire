/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
            return new MPlayerParser(file);
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
