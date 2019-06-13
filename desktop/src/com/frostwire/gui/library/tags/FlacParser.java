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
import org.jaudiotagger.audio.flac.FlacFileReader;
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture;
import org.jaudiotagger.tag.flac.FlacTag;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * @author aldenml
 */
class FlacParser extends JaudiotaggerParser {
    private static final Logger LOG = Logger.getLogger(FlacParser.class);

    public FlacParser(File file) {
        super(file, new FlacFileReader());
    }

    @Override
    public BufferedImage getArtwork() {
        BufferedImage image = super.getArtwork();
        if (image == null) {
            try {
                AudioFile audioFile = AudioFileIO.read(file);
                FlacTag tag = (FlacTag) audioFile.getTag();
                if (tag != null) {
                    List<MetadataBlockDataPicture> images = tag.getImages();
                    if (images != null && !images.isEmpty()) {
                        MetadataBlockDataPicture picture = images.get(0);
                        byte[] data = picture.getImageData();
                        image = imageFromData(data);
                    }
                }
            } catch (Throwable e) {
                LOG.error("Unable to read cover art from flac");
            }
        }
        return image;
    }
}
