/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.search.pixabay;

import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.StreamableSearchResult;

import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PixabayVideoSearchResult extends PixabayItemSearchResult implements HttpSearchResult, StreamableSearchResult {

    private final String displayName;
    private final String filename;
    private final String thumbnailUrl;

    PixabayVideoSearchResult(PixabayItem item) {
        super(item);
        this.displayName = item.type + "-" + item.id + ".mp4";
        this.filename = displayName;
        this.thumbnailUrl = String.format(Locale.US, "https://i.vimeocdn.com/video/%s_100x75.jpg", item.picture_id);
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    @Override
    public String getDownloadUrl() {
        return item.videos.tiny.url;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getSize() {
        return item.videos.tiny.size;
    }

    @Override
    public String getStreamUrl() {
        return item.videos.tiny.url;
    }
}
