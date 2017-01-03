/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.search.extratorrent;

import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentCrawlableSearchResult;
import com.frostwire.search.torrent.TorrentJsonSearchPerformer;
import com.frostwire.util.JsonUtils;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class ExtratorrentSearchPerformer extends TorrentJsonSearchPerformer<ExtratorrentItem, ExtratorrentSearchResult> {

    public ExtratorrentSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "http://extratorrent.cc/json/?search=" + encodedKeywords;
    }

    @Override
    protected List<ExtratorrentItem> parseJson(String json) {
        ExtratorrentResponse response = JsonUtils.toObject(json, ExtratorrentResponse.class);
        for (ExtratorrentItem item : response.list) {
            item.link = item.link.replaceAll("extratorrent.com", "extratorrent.cc");
            item.torrentLink = item.torrentLink.replaceAll("extratorrent.com", "extratorrent.cc");
        }
        return response.list;
    }

    @Override
    protected List<? extends SearchResult> crawlResult(TorrentCrawlableSearchResult sr, byte[] data) throws Exception {
        return PerformersHelper.crawlTorrent(this, sr, data);
    }

    @Override
    protected ExtratorrentSearchResult fromItem(ExtratorrentItem item) {
        return new ExtratorrentSearchResult(item);
    }
}
