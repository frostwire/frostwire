/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.frostclick;

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.torrent.TorrentSearchResult;

import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public class TorrentPromotionSearchResult implements TorrentSearchResult {

    private static final String FROSTCLICK_VENDOR = "FrostClick";
    private int uid = -1;
    private final Slide slide;
    private final long creationTime;

    public TorrentPromotionSearchResult(Slide slide) {
        this.slide = slide;
        this.creationTime = System.currentTimeMillis();
    }

    public String getDisplayName() {
        return slide.title;
    }

    @Override
    public long getSize() {
        return slide.size;
    }

    @Override
    public String getFilename() {
        return FilenameUtils.getName(slide.torrent);
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getHash() {
        return null;
    }

    @Override
    public String getDetailsUrl() {
        return slide.clickURL;
    }

    @Override
    public String getTorrentUrl() {
        return slide.torrent;
    }

    @Override
    public String getReferrerUrl() {
        return slide.clickURL;
    }

    @Override
    public String getSource() {
        return FROSTCLICK_VENDOR;
    }

    @Override
    public int getSeeds() {
        return -1;
    }

    @Override
    public License getLicense() {
        return Licenses.UNKNOWN;
    }

    @Override
    public String getThumbnailUrl() {
        return null;
    }
}
