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

import com.frostwire.search.StreamableSearchResult;
import com.frostwire.search.youtube.YouTubeExtractor.LinkInfo;

import static com.frostwire.search.youtube.YouTubeUtils.buildDownloadUrl;
import static com.frostwire.search.youtube.YouTubeUtils.isDash;

/**
 * @author gubatron
 * @author aldenml
 */
public final class YouTubeCrawledStreamableSearchResult extends YouTubeCrawledSearchResult implements StreamableSearchResult {

    private final String streamUrl;

    public YouTubeCrawledStreamableSearchResult(YouTubeSearchResult sr, LinkInfo video, LinkInfo audio, LinkInfo minQuality) {
        super(sr, video, audio);
        if (audio != null && isDash(audio)) {
            streamUrl = buildDownloadUrl(null, minQuality);
        } else {
            streamUrl = getDownloadUrl();
        }
    }

    @Override
    public String getStreamUrl() {
        return streamUrl;
    }
}
