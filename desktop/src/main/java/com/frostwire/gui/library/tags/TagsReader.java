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

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * @author aldenml
 * @author gubatron
 */
public class TagsReader {
    private static final Logger LOG = Logger.getLogger(TagsReader.class);
    private final File file;
    private final TagsParser parser;

    public TagsReader(File file) {
        this.file = file;
        parser = new TagsParserFactory().getInstance(file);
    }

    public TagsData parse() {
        TagsData data = null;
        if (parser != null) {
            data = parser.parse();
        } else {
            LOG.warn("Unable to create tags parser for file: " + file);
        }
        return data;
    }

    public BufferedImage getArtwork() {
        BufferedImage image = null;
        if (parser != null) {
            image = parser.getArtwork();
        } else {
            LOG.warn("Unable to create tags parser for file: " + file);
        }
        return image;
    }

    @SuppressWarnings("unused")
    private boolean isEmpty(TagsData data) {
        return false; // default behavior for now
    }

    public File getFile() {
        return file;
    }
}
