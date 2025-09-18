/*
 *     Created by Angel Leon (@gubatron)
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
