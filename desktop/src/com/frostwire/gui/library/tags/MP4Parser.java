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

import com.frostwire.mp4.*;
import com.frostwire.util.Logger;
import org.apache.commons.io.IOUtils;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * @author aldenml
 */
class MP4Parser extends AbstractTagParser {
    private static final Logger LOG = Logger.getLogger(MP4Parser.class);
    private static final String[] ID3_GENRES = {
            // ID3v1 Genres
            "Blues",
            "Classic Rock",
            "Country",
            "Dance",
            "Disco",
            "Funk",
            "Grunge",
            "Hip-Hop",
            "Jazz",
            "Metal",
            "New Age",
            "Oldies",
            "Other",
            "Pop",
            "R&B",
            "Rap",
            "Reggae",
            "Rock",
            "Techno",
            "Industrial",
            "Alternative",
            "Ska",
            "Death Metal",
            "Pranks",
            "Soundtrack",
            "Euro-Techno",
            "Ambient",
            "Trip-Hop",
            "Vocal",
            "Jazz+Funk",
            "Fusion",
            "Trance",
            "Classical",
            "Instrumental",
            "Acid",
            "House",
            "Game",
            "Sound Clip",
            "Gospel",
            "Noise",
            "AlternRock",
            "Bass",
            "Soul",
            "Punk",
            "Space",
            "Meditative",
            "Instrumental Pop",
            "Instrumental Rock",
            "Ethnic",
            "Gothic",
            "Darkwave",
            "Techno-Industrial",
            "Electronic",
            "Pop-Folk",
            "Eurodance",
            "Dream",
            "Southern Rock",
            "Comedy",
            "Cult",
            "Gangsta",
            "Top 40",
            "Christian Rap",
            "Pop/Funk",
            "Jungle",
            "Native American",
            "Cabaret",
            "New Wave",
            "Psychadelic",
            "Rave",
            "Showtunes",
            "Trailer",
            "Lo-Fi",
            "Tribal",
            "Acid Punk",
            "Acid Jazz",
            "Polka",
            "Retro",
            "Musical",
            "Rock & Roll",
            "Hard Rock",
            // The following genres are Winamp extensions
            "Folk",
            "Folk-Rock",
            "National Folk",
            "Swing",
            "Fast Fusion",
            "Bebob",
            "Latin",
            "Revival",
            "Celtic",
            "Bluegrass",
            "Avantgarde",
            "Gothic Rock",
            "Progressive Rock",
            "Psychedelic Rock",
            "Symphonic Rock",
            "Slow Rock",
            "Big Band",
            "Chorus",
            "Easy Listening",
            "Acoustic",
            "Humour",
            "Speech",
            "Chanson",
            "Opera",
            "Chamber Music",
            "Sonata",
            "Symphony",
            "Booty Bass",
            "Primus",
            "Porn Groove",
            "Satire",
            "Slow Jam",
            "Club",
            "Tango",
            "Samba",
            "Folklore",
            "Ballad",
            "Power Ballad",
            "Rhythmic Soul",
            "Freestyle",
            "Duet",
            "Punk Rock",
            "Drum Solo",
            "A capella",
            "Euro-House",
            "Dance Hall",
            // The following ones seem to be fairly widely supported as well
            "Goa",
            "Drum & Bass",
            "Club-House",
            "Hardcore",
            "Terror",
            "Indie",
            "Britpop",
            null,
            "Polsk Punk",
            "Beat",
            "Christian Gangsta",
            "Heavy Metal",
            "Black Metal",
            "Crossover",
            "Contemporary Christian",
            "Christian Rock",
            "Merengue",
            "Salsa",
            "Thrash Metal",
            "Anime",
            "JPop",
            "Synthpop",
            // 148 and up don't seem to have been defined yet.
    };

    MP4Parser(File file) {
        super(file);
    }

    private static BufferedImage getArtworkFromMP4(File file) {
        BufferedImage image = null;
        try {
            RandomAccessFile iso = new RandomAccessFile(file, "r");
            LinkedList<Box> boxes = IsoFile.head(iso, ByteBuffer.allocate(100 * 1024));
            try {
                AppleCoverBox data = Box.findFirst(boxes, Box.covr);
                if (data != null) {
                    byte[] imageData = data.value();
                    if (data.dataType() == 13) { // jpg
                        image = imageFromData(imageData);
                    } else if (data.dataType() == 14) { // png
                        try {
                            image = ImageIO.read(new ByteArrayInputStream(imageData, 0, imageData.length));
                        } catch (IIOException e) {
                            LOG.warn("Unable to decode png image from data tag");
                        }
                    }
                }
            } finally {
                IOUtils.closeQuietly(iso);
            }
        } catch (Throwable e) {
            //LOG.error("Unable to read cover art from mp4 file: " + file);
        }
        return image;
    }

    @Override
    public TagsData parse() {
        TagsData data = null;
        try {
            RandomAccessFile iso = new RandomAccessFile(file, "r");
            LinkedList<Box> boxes = IsoFile.head(iso, ByteBuffer.allocate(100 * 1024));
            try {
                int duration = getDuration(boxes);
                String bitrate = "";
                AppleItemListBox ilst = Box.findFirst(boxes, Box.ilst);
                String title = getBoxValue(ilst, Box.Cnam);
                String artist = getBoxValue(ilst, Box.CART);
                String album = getBoxValue(ilst, Box.Calb);
                String comment = getBoxValue(ilst, Box.Ccmt);
                String genre = getGenre(ilst);
                String track = ""; //getTrackNumberValue(ilst);
                String year = "";// getBoxValue(ilst, AppleRecordingYear2Box.class);
                String lyrics = "";
                data = sanitize(duration, bitrate, title, artist, album, comment, genre, track, year, lyrics);
            } finally {
                IOUtils.closeQuietly(iso);
            }
        } catch (Exception e) {
            LOG.warn("Unable to parse file using mp4parser: " + file);
        }
        return data;
    }

    @Override
    public BufferedImage getArtwork() {
        return getArtworkFromMP4(file);
    }

    private int getDuration(LinkedList<Box> boxes) {
        MovieHeaderBox mvhd = Box.findFirst(boxes, Box.mvhd);
        return (int) (mvhd.duration() / mvhd.timescale());
    }

    private <T extends AppleUtf8Box> String getBoxValue(AppleItemListBox ilst, int type) {
        T b = ilst.findFirst(type);
        return b != null ? b.value() : "";
    }

    private long getBoxLongValue(AppleItemListBox ilst) {
        AppleIntegerBox b = ilst.findFirst(Box.gnre);
        return b != null ? b.value() : -1;
    }

    private String getGenre(AppleItemListBox ilst) {
        String value = null;
        long valueId = getBoxLongValue(ilst);
        if (0 <= valueId && valueId < ID3_GENRES.length) {
            value = ID3_GENRES[(int) valueId];
        }
        if (value == null || value.equals("")) {
            value = getBoxValue(ilst, Box.Cgen);
        }
        return value;
    }
}
