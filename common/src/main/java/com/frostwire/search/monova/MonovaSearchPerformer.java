/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.search.monova;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

import java.io.IOException;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MonovaSearchPerformer extends TorrentRegexSearchPerformer<MonovaSearchResult> {

    private static final int MAX_RESULTS = 20;
    private static final String REGEX = "(?is)<a href=\"//%s/(?<itemid>[A-Fa-f0-9]*?)\">";
    private static final String HTML_REGEX = "(?is)" +
            // filename
            "<div class=\"col-md-12.*?<h1>\\n(?<filename>.*?) </h1>.*?" +
            // creationtime
            "<td>Added:</td>.*?<td>(?<creationtime>.*?)</td>.*?" +
            // infohash
            "<td>Hash:</td>.*?<td>(?<infohash>[A-Fa-f0-9]{40})</td>.*?" +
            // size
            "<td>Total size:</td>.*?<td>(?<size>.*?)</td>.*?" +
            // download torrent url
            "<a id=\"download-file\" href=\"(?<torrenturl>.*?)\" class=\"btn";
    private static final int SIX_MONTHS_IN_SECS = 15552000;

    public MonovaSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 2 * MAX_RESULTS, MAX_RESULTS, String.format(REGEX, domainName), HTML_REGEX);
    }

    @Override
    public String getUrl(int page, String encodedKeywords) {
        return "http://" + getDomainName() + "/search?verified=1&sort=1&page=1&term=" + encodedKeywords;
    }

    @Override
    public String fetchSearchPage(String url) throws IOException {
        return fetch(url, "MONOVA=1; MONOVA-ADULT=0; MONOVA-NON-ADULT=1;", null);
    }

    @Override
    protected int preliminaryHtmlPrefixOffset(String page) {
        return page.indexOf("<div class=\"nav-wrapper\">");
    }

    @Override
    public CrawlableSearchResult fromMatcher(SearchMatcher matcher) {
        return new MonovaTempSearchResult(getDomainName(), matcher.group("itemid"));
    }

    @Override
    protected MonovaSearchResult fromHtmlMatcher(CrawlableSearchResult sr, SearchMatcher matcher) {
        MonovaSearchResult r = new MonovaSearchResult(sr.getDetailsUrl(), matcher);
        int now_in_seconds = (int) (System.currentTimeMillis() / 1000);
        int creation_time_in_seconds = (int) (r.getCreationTime() / 1000);
        int sr_age_in_seconds = now_in_seconds - creation_time_in_seconds;
        boolean less_than_6_months_old = sr_age_in_seconds < SIX_MONTHS_IN_SECS;
        return r.getTorrentUrl().contains("magnet") && less_than_6_months_old ? r : null;
    }
}
