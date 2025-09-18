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

import com.frostwire.gui.mplayer.MPlayer;
import com.frostwire.util.Logger;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * @author aldenml
 */
class MPlayerParser extends AbstractTagParser {
    private static final Logger LOG = Logger.getLogger(MPlayerParser.class);

    public MPlayerParser(File file) {
        super(file);
    }

    @Override
    public TagsData parse() {
        TagsData data = null;
        try {
            MPlayer mplayer = new MPlayer();
            try {
                Map<String, String> properties = mplayer.getProperties(file.getAbsolutePath());
                int duration = parseDuration(properties.get("ID_LENGTH"));
                String bitrate = parseBitrate(properties.get("ID_AUDIO_BITRATE"));
                String title = properties.get("Title");
                String artist = properties.get("Artist");
                String album = properties.get("Album");
                String comment = properties.get("Comment");
                String genre = properties.get("Genre");
                String track = properties.get("Track");
                String year = properties.get("Year");
                String lyrics = properties.get("Lyrics");
                data = sanitize(duration, bitrate, title, artist, album, comment, genre, track, year, lyrics);
            } finally {
                mplayer.dispose();
            }
        } catch (Exception e) {
            LOG.warn("Unable to parse file with mplayer: " + file, e);
        }
        return data;
    }

    @Override
    public BufferedImage getArtwork() {
        return null;
    }

    private int parseDuration(String durationInSecs) {
        try {
            return (int) Float.parseFloat(durationInSecs);
        } catch (Exception e) {
            return 0;
        }
    }

    private String parseBitrate(String bitrate) {
        if (bitrate == null) {
            return "";
        }
        try {
            return String.valueOf(Integer.parseInt(bitrate) / 1000);
        } catch (Exception e) {
            return bitrate;
        }
    }
}
