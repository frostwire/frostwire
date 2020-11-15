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

import com.frostwire.jpeg.JPEGImageIO;
import com.frostwire.util.Logger;
import org.apache.commons.io.FilenameUtils;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;

/**
 * @author aldenml
 */
abstract class AbstractTagParser implements TagsParser {
    private static final Logger LOG = Logger.getLogger(AbstractTagParser.class);
    final File file;

    AbstractTagParser(File file) {
        this.file = file;
    }

    static BufferedImage imageFromData(byte[] data) {
        BufferedImage image = null;
        try {
            try {
                image = ImageIO.read(new ByteArrayInputStream(data, 0, data.length));
            } catch (IIOException e) {
                image = JPEGImageIO.read(new ByteArrayInputStream(data, 0, data.length));
            }
        } catch (Throwable e) {
            LOG.error("Unable to create artwork image from bytes");
        }
        return image;
    }

    TagsData sanitize(int duration, String bitrate, String title, String artist, String album, String comment, String genre, String track, String year, String lyrics) {
        if (title == null || title.length() == 0) {
            title = FilenameUtils.getBaseName(file.getAbsolutePath());
        }
        if (duration < 0) {
            duration = 0;
        }
        if (artist == null) {
            artist = "";
        }
        if (album == null) {
            album = "";
        }
        if (bitrate == null) {
            bitrate = "";
        }
        if (comment == null) {
            comment = "";
        }
        if (genre == null) {
            genre = "";
        } else {
            genre = genre.replaceFirst("\\(.*\\)", "");
        }
        if (track == null) {
            track = "";
        } else {
            int index = track.indexOf('/');
            if (index != -1) {
                track = track.substring(0, index);
            }
        }
        if (year == null) {
            year = "";
        }
        if (lyrics == null) {
            lyrics = "";
        }
        return new TagsData(duration, bitrate, title, artist, album, comment, genre, track, year, lyrics);
    }
}
