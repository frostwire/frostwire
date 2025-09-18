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
