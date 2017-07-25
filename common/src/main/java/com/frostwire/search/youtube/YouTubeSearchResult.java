/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.search.youtube;

import com.frostwire.search.AbstractFileSearchResult;
import com.frostwire.search.CrawlableSearchResult;
import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public class YouTubeSearchResult extends AbstractFileSearchResult implements CrawlableSearchResult {

    private final String filename;
    private final String displayName;
    private final long creationTime;
    private final String videoUrl;
    private final String source;
    private final String user;
    private final long size;
    private final String thumbnailUrl;

    YouTubeSearchResult(String link, String title, String duration, String user) {
        this.filename = title + ".youtube";
        this.displayName = FilenameUtils.getBaseName(filename);
        this.creationTime = -1;
        if (link.startsWith("/")) {
            link = link.substring(1);
        }
        this.videoUrl = (link.startsWith("https://www.youtube.com/")) ? link : "https://www.youtube.com/" + link;
        this.user = user;
        this.source = "YouTube - " + user;
        this.size = buildSize(duration);

        this.thumbnailUrl = "http://img.youtube.com/vi/" + YouTubeExtractor.getVideoID(videoUrl) + "/hqdefault.jpg";
    }

    private long buildSize(String duration) {
        try {
            if (!duration.contains(":")) {
                return Integer.parseInt(duration);
            }

            String[] arr = duration.split(":");
            int m = Integer.parseInt(arr[0]);
            int s = Integer.parseInt(arr[1]);

            return m * 60 + s;
        } catch (Throwable t) {
            return UNKNOWN_SIZE;
        }

    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getSource() {
        return source;
    }

    public String getUser() {
        return user;
    }

    @Override
    public String getDetailsUrl() {
        return videoUrl;
    }

    @Override
    public boolean isComplete() {
        return false;
    }

    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof YouTubeSearchResult)) {
            return false;
        }

        return videoUrl.equals(((YouTubeSearchResult) obj).videoUrl);
    }
}
