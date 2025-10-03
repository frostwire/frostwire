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

package com.frostwire.search.idope;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IdopeSearchPerformer extends SimpleTorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(IdopeSearchPerformer.class);
    private static Pattern pattern;
    private boolean isDDOSProtectionActive;

    public IdopeSearchPerformer(long token, String keywords, int timeout) {
        super("idope.hair", token, keywords, timeout, 1, 0);
        if (pattern == null) {
            pattern = Pattern.compile("(?is)<img class=\"resultdivtopimg\".*?" +
                    "<a href=\"/torrent/(?<keyword>.*?)/(?<infohash>.*?)/\".*?" +
                    "<div  class=\"resultdivtopname\" >[\n][\\s|\t]+(?<filename>.*?)</div>.*?" +
                    "<div class=\"resultdivbottontime\">(?<age>.*?)</div>.*?" +
                    "<div class=\"resultdivbottonlength\">(?<filesize>.*?)\\p{Z}(?<unit>.*?)</div>.*?" +
                    "<div class=\"resultdivbottonseed\">(?<seeds>.*?)</div>");
        }
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/torrent-list/" + encodedKeywords;
    }

    private IdopeSearchResult fromMatcher(SearchMatcher matcher) {
        String infoHash = matcher.group("infohash");
        String detailsURL = "https://" + getDomainName() + "/torrent/" + matcher.group("keyword") + "/" + infoHash;
        String filename = matcher.group("filename");
        String fileSizeMagnitude = matcher.group("filesize");
        String fileSizeUnit = matcher.group("unit");
        String ageString = matcher.group("age");
        int seeds = Integer.parseInt(matcher.group("seeds"));
        return new IdopeSearchResult(detailsURL, infoHash, filename, fileSizeMagnitude, fileSizeUnit, ageString, seeds, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
    }

    @Override
    protected List<? extends IdopeSearchResult> searchPage(String page) {
        if (null == page || page.isEmpty()) {
            stopped = true;
            return Collections.emptyList();
        }

        final String HTML_PREFIX_MARKER = "<div id=\"div2child\">";
        int htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER) + HTML_PREFIX_MARKER.length();
        final String HTML_SUFFIX_MARKER = "<div id=\"rightdiv\">";
        int htmlSuffixIndex = page.indexOf(HTML_SUFFIX_MARKER);

        String reducedHtml = page.substring(htmlPrefixIndex, htmlSuffixIndex > 0 ? htmlSuffixIndex : page.length() - htmlPrefixIndex);

        ArrayList<IdopeSearchResult> results = new ArrayList<>(0);
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
                IdopeSearchResult sr = fromMatcher(matcher);
                results.add(sr);
            } else {
                isDDOSProtectionActive = reducedHtml.contains("Cloudflare");
                if (!isDDOSProtectionActive) {
                    LOG.warn("IdopeSearchPerformer search matcher broken. Please notify at https://github.com/frostwire/frostwire/issues/new");
                } else {
                    LOG.warn("IdopeSearchPerformer search matcher disabled. DDOS protection active.");
                }
            }
        } while (matcherFound && !isStopped() && results.size() < MAX_RESULTS);
        return results;
    }

    @Override
    public boolean isDDOSProtectionActive() {
        return isDDOSProtectionActive;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}
