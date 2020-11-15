/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Himanshu Sharma (HimanshuSharma789)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

package com.frostwire.search.one337x;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

/**
 * @author gubatron
 * @author aldenml
 * @author HimanshuSharma789
 */
public final class One337xSearchPerformer extends TorrentRegexSearchPerformer<One337xSearchResult> {
    public static final String SEARCH_RESULTS_REGEX = "(?is)<a href=\"/torrent/(?<itemId>[0-9]*)/(?<htmlFileName>.*?)\">(?<displayName>.*?)</a>";

    public static final String TORRENT_DETAILS_PAGE_REGEX = "(?is)<div class=\"box-info-heading clearfix\">.*?" +
            "<a class=\".*\" href=\"(?<magnet>.*?)\" onclick=\".*\">.*?" +
            "<strong>Language</strong>.*?<span>.*?</span>.*?" +
            "<strong>Total size</strong>.*?<span>(?<size>.*?)</span>.*?" +
            "<strong>Date uploaded</strong>.*?<span>(?<creationDate>.*?)</span>.*?" +
            "<strong>Seeders</strong>.*?<span class=\"seeds\">(?<seeds>[0-9]+)</span>";


    private static final int MAX_RESULTS = 20;

    public One337xSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, SEARCH_RESULTS_REGEX, TORRENT_DETAILS_PAGE_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search/" + encodedKeywords + "/1/";
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group("itemId");
        String htmlFileName = matcher.group("htmlFileName");
        String displayName = matcher.group("displayName");
        return new One337xTempSearchResult(getDomainName(), itemId, htmlFileName, displayName);
    }

    @Override
    protected One337xSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new One337xSearchResult(sr.getDetailsUrl(), sr.getDisplayName(), matcher);
    }

    @Override
    protected boolean isValidHtml(String html) {
        return html != null && !html.contains("Cloudflare");
    }

    @Override
    protected int htmlPrefixOffset(String html) {
        int offset = html.indexOf("<div class=\"col-9 page-content\">");
        return offset > 0 ? offset : 0;
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        int offset = html.indexOf("<div class=\"torrent-detail-info\"");
        return offset > 0 ? offset : html.length();
    }

}
