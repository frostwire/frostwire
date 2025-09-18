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
        return (title.length() > 150 ? title.substring(0, 150) : title) + ".mp4";
    }

    @Override
    public long getSize() {
        return estimatedFileSizeInBytes;
    }
}
