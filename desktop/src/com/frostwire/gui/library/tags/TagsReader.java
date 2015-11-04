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

import com.frostwire.logging.Logger;

/**
 * 
 * @author aldenml
 *
 */
public class TagsReader {

    private static final Logger LOG = Logger.getLogger(TagsReader.class);

    private final File file;

    public TagsReader(File file) {
        this.file = file;
    }

    public TagsData parse() {
        TagsData data = null;

        TagsParser parser = new TagsParserFactory().getInstance(file);

        if (parser != null) {
            data = parser.parse();

            // aldenml: fallback to mplayer parsing, refactor this logic (remove it)
            if (data == null || isEmpty(data)) {
                data = new MPlayerParser(file).parse();
            }
        } else {
            LOG.warn("Unable to create tags parser for file: " + file);
        }

        return data;
    }

    public BufferedImage getArtwork() {
        BufferedImage image = null;

        TagsParser parser = new TagsParserFactory().getInstance(file);
        if (parser != null) {
            image = parser.getArtwork();
        } else {
            LOG.warn("Unable to create tags parser for file: " + file);
        }

        return image;
    }

    private boolean isEmpty(TagsData data) {
        return false; // default behavior for now
    }
}
