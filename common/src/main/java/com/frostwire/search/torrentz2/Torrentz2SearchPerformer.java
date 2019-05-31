/*
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torrentz2;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

public class Torrentz2SearchPerformer extends TorrentRegexSearchPerformer<Torrentz2SearchResult> {

    private static final int MAX_RESULTS = 30;
    public static final String SEARCH_RESULTS_REGEX = "(?is)<dl><dt><a href=/(?<itemid>[a-f0-9]*)>(.*?)</a>.*?";
    public static final String TORRENT_DETAILS_PAGE_REGEX =
            "(?is)<title>(?<filename>.*?)</title>.*?" +
            "<div class=downlinks><div>age <span title=\"(?<time>.*?)\".*?" +
            "<div>info_hash: (?<infohash>[a-f0-9]{32,64})</div>.*?" + // considering future SHA-256 hashes 256/8 = 32 bytes * 2 characters = 64 characters
            "udp://(?<trackerURI>.*?)/announce.*?" +
            "Size: (?<filesize>.*?) (?<unit>[BKMGTPEZY]+)</div><h2>Torrent Contents</h2>";

    public Torrentz2SearchPerformer(long token, String keywords, int timeout) {
        super("torrentz2.eu", token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, SEARCH_RESULTS_REGEX, TORRENT_DETAILS_PAGE_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        String transformedKeywords = encodedKeywords.replace("0%20", "-");
        return "https://" + getDomainName() + "/verified?f=" + transformedKeywords;
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group("itemid");
        String transformedId = itemId.replaceFirst("/", "");
        return new Torrentz2TempSearchResult(getDomainName(), transformedId);
    }

    @Override
    protected int htmlSuffixOffset(String html) {
        int offset = html.indexOf("<div><h3>Latest Searches</h3>");
        return offset > 0 ? offset : 0;
    }

    @Override
    protected Torrentz2SearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new Torrentz2SearchResult(sr.getDetailsUrl(), matcher);
    }

    @Override
    protected boolean isValidHtml(String html) {
        return html != null && !html.contains("Cloudflare");
    }
}
