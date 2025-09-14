/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

package com.frostwire.search.glotorrents;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GloTorrentsSearchPerformer extends SimpleTorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(GloTorrentsSearchPerformer.class);
    private static Pattern pattern;

    public GloTorrentsSearchPerformer(long token, String keywords, int timeoutMillis) {
        super("gtso.cc", token, keywords, timeoutMillis, 1, 0);
        if (pattern == null) {
            pattern = Pattern.compile("(?is)" +
                    "<td class='ttable_col2' nowrap='nowrap'>.*?<a title=\"(?<filename>.*?)\" href=\"(?<detailsURL>.*?)\"><b>.*?" +
                    "'nofollow' href=\"(?<magnet>.*?)\">.*?\"Magnet Download\".*?" +
                    "<td class='ttable_col1' align='center'>(?<filesize>\\d+\\.\\d+)\\p{Z}(?<unit>[KMGTP]B)</td>(.|\\n)*?" +
                    "<font color='green'><b>(?<seeds>.*?)</b></font>");
        }
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://gtso.cc/search_results.php?search=" + encodedKeywords + "&cat=0&incldead=0&lang=0&sort=seeders&order=desc";
    }

    private GloTorrentsSearchResult fromMatcher(SearchMatcher matcher) {
        String filename = matcher.group("filename");
        String detailsURL = "https://" + getDomainName() + matcher.group("detailsURL");
        String magnetURL = matcher.group("magnet");
        int magnetStart = "magnet:?xt=urn:btih:".length();
        String infoHash = magnetURL.substring(magnetStart, magnetStart + 40);
        String fileSizeMagnitude = matcher.group("filesize");
        String fileSizeUnit = matcher.group("unit");
        int seeds = Integer.parseInt(matcher.group("seeds").replace(",",""));
        return new GloTorrentsSearchResult(magnetURL, detailsURL, infoHash, filename, fileSizeMagnitude, fileSizeUnit, seeds, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        if (null == page || page.isEmpty()) {
            stopped = true;
            return Collections.emptyList();
        }
        final String HTML_PREFIX_MARKER = "class=\"ttable_headinner\"";
        int htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER) + HTML_PREFIX_MARKER.length();
        final String HTML_SUFFIX_MARKER = "<div class=\"pagination\">";
        int htmlSuffixIndex = page.indexOf(HTML_SUFFIX_MARKER);
        String reducedHtml = page.substring(htmlPrefixIndex, htmlSuffixIndex > 0 ? htmlSuffixIndex : page.length() - htmlPrefixIndex);
        ArrayList<GloTorrentsSearchResult> results = new ArrayList<>(0);
        SearchMatcher matcher = new SearchMatcher((pattern.matcher(reducedHtml)));
        boolean matcherFound;
        int MAX_RESULTS = 10;
        do {
            try {
                matcherFound = matcher.find();
            } catch (Throwable t) {
                matcherFound = false;
                LOG.error("searchPage() has failed.\n" + t.getMessage(), t);
            }
            if (matcherFound) {
                GloTorrentsSearchResult sr = fromMatcher(matcher);
                results.add(sr);
            } else {
                LOG.warn("GloTorrentsSearchResult search matcher broken. Please notify at https://github.com/frostwire/frostwire/issues/new");
            }
        } while (matcherFound && !isStopped() && results.size() < MAX_RESULTS);
        return results;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}
