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
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.ogg.OggFileReader;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * @author aldenml
 */
class OggParser extends JaudiotaggerParser {
    private static final Logger LOG = Logger.getLogger(OggParser.class);

    public OggParser(File file) {
        super(file, new OggFileReader());
    }

    @Override
    public BufferedImage getArtwork() {
        BufferedImage image = super.getArtwork();
        if (image == null) {
            try {
                AudioFile audioFile = AudioFileIO.read(file);
                VorbisCommentTag tag = (VorbisCommentTag) audioFile.getTag();
                if (tag != null) {
                    byte[] data = tag.getArtworkBinaryData();
                    if (data != null) {
                        image = imageFromData(data);
                    }
                }
            } catch (Throwable e) {
                LOG.error("Unable to read cover art from ogg");
            }
        }
        return image;
    }
}
