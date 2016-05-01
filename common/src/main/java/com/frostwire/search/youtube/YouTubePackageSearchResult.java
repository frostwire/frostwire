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

import com.frostwire.search.SearchResult;
import com.frostwire.search.StreamableSearchResult;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class YouTubePackageSearchResult extends YouTubeSearchResult implements StreamableSearchResult {

    private final List<SearchResult> children;
    private String streamUrl;

    YouTubePackageSearchResult(YouTubeSearchResult sr, List<SearchResult> children) {
        super(sr.getDetailsUrl(), sr.getDisplayName(), "-1", sr.getUser());
        this.children = children;

        for (SearchResult child : children) {
            if (child instanceof YouTubeCrawledStreamableSearchResult) {
                YouTubeCrawledStreamableSearchResult ssr = (YouTubeCrawledStreamableSearchResult) child;

                if (ssr.getVideo() != null) {
                    final int fmt = ssr.getVideo().fmt;
                    if (fmt == 18) {  // @see YouTubeExtractor.buildFormats()
                        streamUrl = ssr.getStreamUrl();
                        break;
                    }
                }
            }
        }
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof YouTubePackageSearchResult)) {
            return false;
        }
        return getDetailsUrl().equals(((YouTubePackageSearchResult) obj).getDetailsUrl());
    }

    @Override
    public String getStreamUrl() {
        return streamUrl;
    }

    public List<SearchResult> children() {
        return children;
    }
}
