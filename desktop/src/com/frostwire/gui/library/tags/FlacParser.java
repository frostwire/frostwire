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
