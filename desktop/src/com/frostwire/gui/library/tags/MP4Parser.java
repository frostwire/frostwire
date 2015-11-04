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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;

import com.frostwire.mp4.*;
import org.apache.commons.io.IOUtils;

import com.frostwire.logging.Logger;

/**
 * 
 * @author aldenml
 *
 */
class MP4Parser extends AbstractTagParser {

    private static final Logger LOG = Logger.getLogger(MP4Parser.class);

    public MP4Parser(File file) {
        super(file);
    }

    @Override
    public TagsData parse() {
        TagsData data = null;

        try {
            BoxParser parser = new PropertyBoxParserImpl() {
                @Override
                public Box parseBox(DataSource byteChannel, Container parent) throws IOException {
                    Box box = super.parseBox(byteChannel, parent);

                    if (box instanceof AbstractBox) {
                        ((AbstractBox) box).parseDetails();
                    }

                    return box;
                }
            };
            IsoFile iso = new IsoFile(new FileDataSourceImpl(file), parser);

            try {

                int duration = getDuration(iso);
                String bitrate = getBitRate(iso);

                AppleItemListBox ilst = (AppleItemListBox) Path.getPath(iso.getMovieBox(), "/moov/udta/meta/ilst");
                
                String title = getBoxValue(ilst, AppleNameBox.class);
                String artist = getBoxValue(ilst, AppleArtistBox.class);
                String album = getBoxValue(ilst, AppleAlbumBox.class);
                String comment = getBoxValue(ilst, AppleCommentBox.class);
                String genre = getGenre(ilst);
                String track = getTrackNumberValue(ilst);
                String year = getBoxValue(ilst, AppleRecordingYear2Box.class);

                data = sanitize(duration, bitrate, title, artist, album, comment, genre, track, year);

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

    static BufferedImage getArtworkFromMP4(File file) {
        BufferedImage image = null;

        try {
            BoxParser parser = new PropertyBoxParserImpl() {
                @Override
                public Box parseBox(DataSource byteChannel, Container parent) throws IOException {
                    Box box = super.parseBox(byteChannel, parent);

                    if (box instanceof AbstractBox) {
                        ((AbstractBox) box).parseDetails();
                    }

                    return box;
                }
            };
            IsoFile iso = new IsoFile(new FileDataSourceImpl(file), parser);

            try {

                AppleCoverBox data = (AppleCoverBox) Path.getPath(iso.getMovieBox(), "/moov/udta/meta/ilst/covr");
                data.parseDetails();
                if (data != null) {
                    byte[] imageData = data.getCoverData();
                    if (data.getDataType() == 13) { // jpg
                        image = imageFromData(imageData);
                    } else if (data.getDataType() == 14) { // png
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

    private int getDuration(IsoFile iso) {
        MovieHeaderBox mvhd = iso.getMovieBox().getMovieHeaderBox();
        return (int) (mvhd.getDuration() / mvhd.getTimescale());
    }

    private String getBitRate(IsoFile iso) {
        return ""; // deep research of atoms per codec
    }

    private <T extends Utf8AppleDataBox> String getBoxValue(AppleItemListBox ilst, Class<T> clazz) {
        String value = "";
        List<T> boxes = ilst.getBoxes(clazz);
        if (boxes != null && !boxes.isEmpty()) {
            value = boxes.get(0).getValue();
        }
        return value;
    }
    
    private String getTrackNumberValue(AppleItemListBox ilst) {
        String value = "";
        List<AppleTrackNumberBox> boxes = ilst.getBoxes(AppleTrackNumberBox.class);
        if (boxes != null && !boxes.isEmpty()) {
            value = String.valueOf(boxes.get(0).getA());
        }
        return value;
    }
    
    private <T extends AppleVariableSignedIntegerBox> long getBoxLongValue(AppleItemListBox ilst, Class<T> clazz) {
        long value = -1;
        List<T> boxes = ilst.getBoxes(clazz);
        if (boxes != null && !boxes.isEmpty()) {
            value = boxes.get(0).getValue();
        }
        return value;
    }

    private String getGenre(AppleItemListBox ilst) {
        String value = null;
        
        long valueId = getBoxLongValue(ilst, AppleGenreIDBox.class);
        
        if (0 <= valueId && valueId < ID3_GENRES.length) {
            value = ID3_GENRES[(int)valueId];
        }
        
        if (value == null || value.equals("")) {
            value = getBoxValue(ilst, AppleGenreBox.class);
        }
        return value;
    }
    
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
}
