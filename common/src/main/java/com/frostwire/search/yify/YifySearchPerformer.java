/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.search.yify;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class YifySearchPerformer extends TorrentRegexSearchPerformer<YifySearchResult> {

    private static final int MAX_RESULTS = 21;
    private static final String HTML_REGEX = "(?is)<h1 itemprop=\"name\">(?<displayName>.*?)</h1>.*?<img itemprop=\"image\" src=\"(?<cover>.*?)\".*?<dt>Size:</dt><dd>(?<size>.*?)</dd>.*?<dt>Language:</dt><dd>(?<language>.*?)</dd>.*?<dt>Seeds:</dt><dd>(?<seeds>\\d*?)</dd><dt>Peers:</dt><dd>(?<peers>\\d*?)</dd>.*?<span><a href=\"(?<magnet>.*?)\" id=\"dm\"";
    private static final String REGEX = "(?is)<figure> <a href=\"/movie/(?<itemId>[0-9]*)/(?<htmlFileName>.*?)\">";

    public YifySearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, REGEX, HTML_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search/" + encodedKeywords + "/";
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        String itemId = matcher.group("itemId");
        String htmlFileName = matcher.group("htmlFileName");

        return new YifyTempSearchResult(getDomainName(), itemId, htmlFileName);
    }

    @Override
    protected YifySearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        return new YifySearchResult(sr.getDetailsUrl(), matcher);
    }

    @Override
    protected List<? extends SearchResult> crawlResult(CrawlableSearchResult sr, byte[] data) throws Exception {
        try {
            return super.crawlResult(sr, data);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("in bencoded string")) {
                return fallbackToMagnet(sr);
            }

            throw e;
        }
    }

    private List<SearchResult> fallbackToMagnet(CrawlableSearchResult sr) {
        ArrayList<SearchResult> r = new ArrayList<>(1);
        if (sr instanceof YifySearchResult) {
            YifySearchResult yify = (YifySearchResult) sr;
            if (!yify.getTorrentUrl().startsWith("magnet")) {
                ((YifySearchResult) sr).switchToMagnet();
                r.add(sr);
            }
        }
        return r;
    }

    @Override
    protected boolean isValidHtml(String html) {
        return html != null && !html.contains("Cloudfare");
    }
}
