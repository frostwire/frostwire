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
