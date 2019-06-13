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

import com.frostwire.util.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.mp3.MP3File;
import org.jaudiotagger.audio.mp3.MP3FileReader;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.ID3v24Frames;
import org.jaudiotagger.tag.images.Artwork;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * @author aldenml
 */
class MP3Parser extends JaudiotaggerParser {
    private static final Logger LOG = Logger.getLogger(MP3Parser.class);

    public MP3Parser(File file) {
        super(file, new MP3FileReader());
    }

    @Override
    public BufferedImage getArtwork() {
        BufferedImage image = super.getArtwork();
        if (image == null) {
            try {
                MP3File mp3 = new MP3File(file.getAbsoluteFile());
                if (mp3.hasID3v2Tag()) {
                    AbstractID3v2Tag tag = mp3.getID3v2Tag();
                    Artwork artwork = tag.getFirstArtwork();
                    if (artwork != null) {
                        byte[] data = artwork.getBinaryData();
                        image = imageFromData(data);
                    }
                }
            } catch (Throwable e) {
                LOG.error("Unable to read cover art from mp3");
            }
        }
        return image;
    }

    protected String getTitle(AudioFile audioFile) {
        return getValueSafe(super.getTitle(audioFile), audioFile, ID3v24Frames.FRAME_ID_TITLE);
    }

    protected String getArtist(AudioFile audioFile) {
        return getValueSafe(super.getArtist(audioFile), audioFile, ID3v24Frames.FRAME_ID_ARTIST);
    }

    protected String getAlbum(AudioFile audioFile) {
        return getValueSafe(super.getAlbum(audioFile), audioFile, ID3v24Frames.FRAME_ID_ALBUM);
    }

    protected String getComment(AudioFile audioFile) {
        return getValueSafe(super.getComment(audioFile), audioFile, ID3v24Frames.FRAME_ID_COMMENT);
    }

    protected String getGenre(AudioFile audioFile) {
        return getValueSafe(super.getGenre(audioFile), audioFile, ID3v24Frames.FRAME_ID_GENRE);
    }

    protected String getTrack(AudioFile audioFile) {
        return getValueSafe(super.getTrack(audioFile), audioFile, ID3v24Frames.FRAME_ID_TRACK);
    }

    protected String getYear(AudioFile audioFile) {
        return getValueSafe(super.getYear(audioFile), audioFile, ID3v24Frames.FRAME_ID_YEAR);
    }

    protected String getLyrics(AudioFile audioFile) {
        return getValueSafe(getValueSafe(super.getLyrics(audioFile), audioFile, ID3v24Frames.FRAME_ID_SYNC_LYRIC), audioFile, ID3v24Frames.FRAME_ID_UNSYNC_LYRICS);
    }

    private String getValueSafe(String currentValue, AudioFile audioFile, String identifier) {
        String value = currentValue;
        if (value == null || value.length() == 0) {
            if (audioFile instanceof MP3File && ((MP3File) audioFile).hasID3v2Tag()) {
                try {
                    AbstractID3v2Tag v2tag = ((MP3File) audioFile).getID3v2Tag();
                    value = v2tag.getFirst(identifier);
                } catch (Exception e) {
                    LOG.warn("Unable to get value for ID3v2 tag key: " + identifier);
                }
            }
        }
        return value;
    }
}
