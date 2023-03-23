/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.

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

package com.frostwire.search.yt;

import com.frostwire.licenses.License;
import com.frostwire.search.AbstractFileSearchResult;

public class YTSearchResult extends AbstractFileSearchResult {
    private final String title;
    private final String detailsUrl;
    private final long creationTimestamp;
    private final String thumbnailUrl;
    private final int viewCount;

    private final long estimatedFileSizeInBytes;

    public YTSearchResult(String title, String detailsUrl, long creationTime, String thumbnailUrl, int viewCount, long estimatedFileSizeInBytes) {
        this.title = title;
        this.detailsUrl = detailsUrl;
        this.creationTimestamp = creationTime;
        this.thumbnailUrl = thumbnailUrl;
        this.viewCount = viewCount;
        this.estimatedFileSizeInBytes = estimatedFileSizeInBytes;
    }

    @Override
    public String toString() {
        return "YTSearchResult{" +
                "title='" + title + '\'' +
                ", detailsUrl='" + detailsUrl + '\'' +
                ", creationTimestamp=" + creationTimestamp +
                ", thumbnailUrl='" + thumbnailUrl + '\'' +
                ", viewCount=" + viewCount +
                '}';
    }

    @Override
    public String getDisplayName() {
        return title;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public long getCreationTime() {
        return creationTimestamp;
    }

    @Override
    public String getSource() {
        return "YT";
    }

    @Override
    public License getLicense() {
        return null;
    }

    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public int getViewCount() {
        return viewCount;
    }

    @Override
    public String getFilename() {
        return title + ".mp4";
    }

    @Override
    public long getSize() {
        return estimatedFileSizeInBytes;
    }
}
