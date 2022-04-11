/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.torlock;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TorLockSearchPerformer extends TorrentRegexSearchPerformer<TorLockSearchResult> {
    private static final int MAX_RESULTS = 15;
    private static final String REGEX = "(?is)<a href=/torrent/([0-9]*?/.*?\\.html)>";
    private static final String HTML_REGEX = "(?is)<a href=\".*?/tor/(?<torrentid>.*?).torrent\".*?" +
            "<dt>NAME</dt>.?<dd>(?<filename>.*?).torrent</dd>.*?" +
            "<dt>INFOHASH</dt><dd.*?>(?<infohash>.*?)</dd>.*?" +
            "<dt>SIZE</dt>.?<dd>(?<filesize>.*?) in.*?" +
            "<dt>ADDED</dt>.?<dd>Uploaded on (?<time>.*?) by.*?" +
            "<dt>SWARM</dt>.?<dd><b style=\"color:#FF5400\">(?<seeds>\\d*?)</b>";

    public TorLockSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, REGEX, HTML_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        String transformedKeywords = encodedKeywords.replace("%20", "-");
        return "https://" + getDomainName() + "/all/torrents/" + transformedKeywords + ".html";
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group(1);
        return new TorLockTempSearchResult(getDomainName(), itemId);
    }

    @Override
    protected int htmlPrefixOffset(String html) {
        int offset = html.indexOf("SIGN UP</a>");
        return Math.max(offset, 0);
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        int offset = html.indexOf(">Description</a></li>");
        return Math.max(offset, 0);
    }

    @Override
    protected TorLockSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new TorLockSearchResult(getDomainName(), sr.getDetailsUrl(), matcher);
    }

    @Override
    protected boolean isValidHtml(String html) {
        return html != null && !html.contains("Cloudflare");
    }

}
