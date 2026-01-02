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

package com.frostwire.gui.library.tags;

import com.frostwire.util.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.generic.AudioFileReader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * @author aldenml
 */
class JaudiotaggerParser extends AbstractTagParser {
    private static final Logger LOG = Logger.getLogger(JaudiotaggerParser.class);
    private final AudioFileReader fileReader;

    JaudiotaggerParser(File file, AudioFileReader fileReader) {
        super(file);
        this.fileReader = fileReader;
    }

    public JaudiotaggerParser(File file) {
        this(file, null);
    }

    @Override
    public TagsData parse() {
        TagsData data = null;
        try {
            AudioFile audioFile = fileReader != null ? fileReader.read(file) : AudioFileIO.read(file);
            AudioHeader header = audioFile.getAudioHeader();
            int duration = header.getTrackLength();
            String bitrate = header.getBitRate();
            String title = getTitle(audioFile);
            String artist = getArtist(audioFile);
            String album = getAlbum(audioFile);
            String comment = getComment(audioFile);
            String genre = getGenre(audioFile);
            String track = getTrack(audioFile);
            String year = getYear(audioFile);
            String lyrics = getLyrics(audioFile);
            data = sanitize(duration, bitrate, title, artist, album, comment, genre, track, year, lyrics);
        } catch (Exception e) {
            LOG.warn("Unable to parse file using Jaudiotagger: " + file);
        }
        return data;
    }

    @Override
    public BufferedImage getArtwork() {
        BufferedImage data = null;
        try {
            AudioFile audioFile = fileReader != null ? fileReader.read(file) : AudioFileIO.read(file);
            Tag tag = audioFile.getTag();
            if (tag != null) {
                Artwork artwork = audioFile.getTag().getFirstArtwork();
                if (artwork != null) {
                    byte[] imageData = artwork.getBinaryData();
                    data = imageFromData(imageData);
                }
            }
        } catch (Exception e) {
            LOG.warn("Unable to read artwork of file using Jaudiotagger: " + file);
        }
        return data;
    }

    String getTitle(AudioFile audioFile) {
        return getValueSafe(audioFile.getTag(), FieldKey.TITLE);
    }

    String getArtist(AudioFile audioFile) {
        return getValueSafe(audioFile.getTag(), FieldKey.ARTIST);
    }

    String getAlbum(AudioFile audioFile) {
        return getValueSafe(audioFile.getTag(), FieldKey.ALBUM);
    }

    String getComment(AudioFile audioFile) {
        return getValueSafe(audioFile.getTag(), FieldKey.COMMENT);
    }

    String getGenre(AudioFile audioFile) {
        return getValueSafe(audioFile.getTag(), FieldKey.GENRE);
    }

    String getTrack(AudioFile audioFile) {
        return getValueSafe(audioFile.getTag(), FieldKey.TRACK);
    }

    String getYear(AudioFile audioFile) {
        return getValueSafe(audioFile.getTag(), FieldKey.YEAR);
    }

    String getLyrics(AudioFile audioFile) {
        return getValueSafe(audioFile.getTag(), FieldKey.LYRICS);
    }

    private String getValueSafe(Tag tag, FieldKey id) {
        String value = null;
        try {
            value = tag.getFirst(id);
        } catch (Exception e) {
            //LOG.warn("Unable to get value for key: " + id);
        }
        return value;
    }
}
