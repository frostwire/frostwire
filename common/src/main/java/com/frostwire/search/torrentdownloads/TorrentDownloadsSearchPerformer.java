/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Alejandro Arturo Martinez (@alejandroarturom)
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

package com.frostwire.search.torrentdownloads;

import com.frostwire.regex.Pattern;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexCrawlerSearchPerformer;

/**
 * @author alejandroarturom
 */
public class TorrentDownloadsSearchPerformer extends TorrentRegexCrawlerSearchPerformer<TorrentDownloadsSearchResult> {
    private static final int MAX_RESULTS = 20;
    private static final String PRELIMINARY_REGEX = "(?is)<a href=\"/torrent/([0-9]*?/.*?)\">*?";
    private static final String HTML_REGEX = "(?is).*?<li><a rel=\"nofollow\" href=\"http://itorrents.org/torrent/(?<torrentid>.*?).torrent?(.*?)\">.*?" +
            "<span>Name:.?</span>(?<filename>.*?)(<a.*>)?</a></p></div>.*?" +
            "<span>Total Size:.?</span>(?<filesize>.*?)&nbsp;(?<unit>[A-Z]+)</p></div>.*?" +
            "<span>Magnet:.*?</span>.*?<a href=\"(?<magnet>.*?)\".*?" +
            "<span>Seeds:.?</span>.?(?<seeds>\\d*?)</p></div>.*?" +
            "<span>Torrent added:.?</span>.?(?<time>[0-9\\-]+).*</p></div>.*?";
    private boolean isDDOSProtectionActive;

    public TorrentDownloadsSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, PRELIMINARY_REGEX, HTML_REGEX);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        String transformedKeywords = encodedKeywords.replace("%20", "+");
        return "https://" + getDomainName() + "/search/?search=" + transformedKeywords;
    }

    @Override
    public Pattern getAltPattern() {
        return null;
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group(1);
        return new TorrentDownloadsTempSearchResult(getDomainName(), itemId);
    }

    @Override
    protected int htmlPrefixOffset(String html) {
        int offset = html.indexOf("Torrent Search Results<span>");
        return Math.max(offset, 0);
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        int offset = html.indexOf("<h1>RECENT SEARCHES");
        return Math.max(offset, 0);
    }

    @Override
    protected TorrentDownloadsSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new TorrentDownloadsSearchResult(getDomainName(), sr.getDetailsUrl(), matcher);
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
}