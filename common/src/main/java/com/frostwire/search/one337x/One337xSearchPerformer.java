/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Himanshu Sharma (HimanshuSharma789)
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

package com.frostwire.search.one337x;

import com.frostwire.regex.Pattern;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexCrawlerSearchPerformer;

/**
 * @author gubatron
 * @author aldenml
 * @author HimanshuSharma789
 */
public final class One337xSearchPerformer extends TorrentRegexCrawlerSearchPerformer<One337xSearchResult> {
    public static final String SEARCH_RESULTS_REGEX = "(?is)<a href=\"/torrent/(?<itemId>[0-9]*)/(?<htmlFileName>.*?)\">(?<displayName>.*?)</a>";

    public static final String TORRENT_DETAILS_PAGE_REGEX = "(?is)<div class=\"box-info-heading clearfix\">.*?" +
            "<a class=\".*\" href=\"(?<magnet>.*?)\" onclick=\".*\">.*?" +
            "<strong>Language</strong>.*?<span>.*?</span>.*?" +
            "<strong>Total size</strong>.*?<span>(?<size>.*?)</span>.*?" +
            "<strong>Date uploaded</strong>.*?<span>(?<creationDate>.*?)</span>.*?" +
            "<strong>Seeders</strong>.*?<span class=\"seeds\">(?<seeds>[0-9]+)</span>";


    private static final int MAX_RESULTS = 20;
    private boolean isDDOSProtectionActive;

    public One337xSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, SEARCH_RESULTS_REGEX, TORRENT_DETAILS_PAGE_REGEX);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search/" + encodedKeywords + "/1/";
    }

    @Override
    public Pattern getAltPattern() {
        return null;
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
        isDDOSProtectionActive = !(html != null && !html.contains("Cloudflare"));
        return !isDDOSProtectionActive;
    }

    @Override
    public boolean isDDOSProtectionActive() {
        return isDDOSProtectionActive;
    }

    @Override
    public boolean isCrawler() {
        return true;
    }


    @Override
    protected int htmlPrefixOffset(String html) {
        int offset = html.indexOf("<div class=\"col-9 page-content\">");
        return Math.max(offset, 0);
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        int offset = html.indexOf("<div class=\"torrent-detail-info\"");
        return offset > 0 ? offset : html.length();
    }
}
