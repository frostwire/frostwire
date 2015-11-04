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
import java.io.File;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.mp4.Mp4FileReader;
import org.jaudiotagger.tag.mp4.Mp4FieldKey;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.Mp4TagField;

import com.frostwire.logging.Logger;

/**
 * 
 * @author aldenml
 *
 */
class M4AParser extends JaudiotaggerParser {

    private static final Logger LOG = Logger.getLogger(MP3Parser.class);

    public M4AParser(File file) {
        super(file, new Mp4FileReader());
    }

    @Override
    public BufferedImage getArtwork() {
        BufferedImage image = super.getArtwork();

        if (image == null) {
            try {
                AudioFile audioFile = AudioFileIO.read(file);
                Mp4Tag mp4tag = (Mp4Tag) audioFile.getTag();
                if (mp4tag != null) {
                    Mp4TagField artField = mp4tag.getFirstField(Mp4FieldKey.ARTWORK);
                    if (artField != null) {
                        byte[] data = artField.getRawContentDataOnly();
                        image = imageFromData(data);
                    }
                }
                if (image == null) { // one more try
                    image = MP4Parser.getArtworkFromMP4(file);
                }
            } catch (Throwable e) {
                LOG.error("Unable to read cover art from m4a");
            }
        }

        return image;
    }
}
