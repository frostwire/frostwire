/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), alejandroarturom
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

package com.frostwire.search.limetorrents;

import com.frostwire.search.AbstractSearchResult;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.util.UrlUtils;

/**
 * Created by alejandroarturom on 26-08-16.
 */
final class LimeTorrentsTempSearchResult extends AbstractSearchResult implements CrawlableSearchResult {
    private final String detailsUrl;

    LimeTorrentsTempSearchResult(String domainName, String itemId) {
        // sometimes the itemId needs to be url encoded
        itemId = UrlUtils.encode(itemId);
        this.detailsUrl = "https://" + domainName + "/torrent/" + itemId + ".html";
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getSource() {
        return null;
    }

    @Override
    public boolean isComplete() {
        return false;
    }
}
