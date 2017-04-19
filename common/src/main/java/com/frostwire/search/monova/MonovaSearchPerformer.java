/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.search.monova;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentRegexSearchPerformer;

import java.io.IOException;
import java.util.Date;

/**
 * @author gubatron
 * @author aldenml
 */
public final class MonovaSearchPerformer extends TorrentRegexSearchPerformer<MonovaSearchResult> {

    private static final int MAX_RESULTS = 20;
    private static final String REGEX = "(?is)<a href=\"//%s/torrent/(?<itemid>[0-9]*?)/(?<filename>.*?)\">";
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
    private static int SIX_MONTHS_IN_SECS = 15552000;

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
        String fileName = matcher.group("filename").replace("&amp;", "&").replace("\n", "");
        return new MonovaTempSearchResult(getDomainName(), matcher.group("itemid"), fileName);
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
