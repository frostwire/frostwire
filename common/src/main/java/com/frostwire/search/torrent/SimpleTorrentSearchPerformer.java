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

package com.frostwire.search.torrent;

import com.frostwire.search.CrawlPagedWebSearchPerformer;
import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchResult;

import java.util.List;

/**
 * Extend this search performer if you can obtain all you need directly from a search results
 * page, otherwise extend TorrentRegexSearchPerformer
 * @author gubatron
 * @author aldenml
 */
public abstract class SimpleTorrentSearchPerformer extends CrawlPagedWebSearchPerformer<TorrentCrawlableSearchResult> {

    protected SimpleTorrentSearchPerformer(String domainName, long token, String keywords, int timeout, int pages, int numCrawls) {
        super(domainName, token, keywords, timeout, pages, numCrawls);
    }

    @Override
    protected String getCrawlUrl(TorrentCrawlableSearchResult sr) {
        return sr.getTorrentUrl();
    }

    @Override
    protected List<? extends SearchResult> crawlResult(TorrentCrawlableSearchResult sr, byte[] data) {
        return PerformersHelper.crawlTorrentInfo(this, sr, data);
    }
}