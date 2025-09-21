/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.search.limetorrents;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 */
public class LimeTorrentsSearchPerformer extends SimpleTorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(LimeTorrentsSearchPerformer.class);
    private static Pattern pattern;

    public LimeTorrentsSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 1);
        if (pattern == null) {
            pattern = Pattern.compile("(?is)<div class=\"tt-name\"><a href=\"http://itorrents.org/torrent/(?<infohash>.*?)\\.torrent\\?title=(?<filename>.*?)\" rel=\"nofollow\" class=\"csprite_dl14\"></a>" +
                    "<a href=\"/(?<detailUrl>.*?.html)\">(?<title>.*?)</.*?<div class=\"tt-options\"></div></td>.*?" +
                    "<td class=\"tdnormal\">(?<age>.*?) -.*?</a></td>.*?" + //they do have an HTML-DOM typo there with that weird </a> inside the </td>
                    "<td class=\"tdnormal\">(?<fileSizeMagnitude>.*?) (?<fileSizeUnit>[A-Z]+)</td>.*?" +
                    "<td class=\"tdseed\">(?<seeds>.*?)</td>.*?");
        }
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        String transformedKeywords = encodedKeywords.replace("%20", "-");
        return "https://" + getDomainName() + "/search/all/" + transformedKeywords + "/leechs/1/";
    }

    @Override
    protected List<LimeTorrentsSearchResult> searchPage(String page) {
        final String HTML_PREFIX_MARKER = "Health</th>";
        int htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER) + HTML_PREFIX_MARKER.length();
        final String HTML_SUFFIX_MARKER = "Next page";
        int htmlSuffixIndex = page.indexOf(HTML_SUFFIX_MARKER);
        String reducedHtml = page.substring(htmlPrefixIndex, htmlSuffixIndex > 0 ? htmlSuffixIndex : page.length() - htmlPrefixIndex);

        ArrayList<LimeTorrentsSearchResult> results = new ArrayList<>(0);
        SearchMatcher matcher = new SearchMatcher((pattern.matcher(reducedHtml)));
        boolean matcherFound;
        int MAX_RESULTS = 50;
        do {
            try {
                matcherFound = matcher.find();
            } catch (Throwable t) {
                matcherFound = false;
                LOG.error("searchPage() has failed.\n" + t.getMessage(), t);
            }
            if (matcherFound) {
                LimeTorrentsSearchResult sr = fromMatcher(matcher);
                if (sr.getSeeds() > 0) {
                    results.add(sr);
                }
                LOG.info("Adding a new search result -> " + sr.getDisplayName() + ":" + sr.getSize() + ":" + sr.getTorrentUrl());
            } else {
                LOG.warn("LimeTorrentsSearchPerformer::searchPage(String page): search matcher broken. Please notify at https://github.com/frostwire/frostwire/issues/new");
                LOG.warn("========");
                LOG.warn(page);
                LOG.warn("========");
            }
        } while (matcherFound && !isStopped() && results.size() <= MAX_RESULTS);
        return results;
    }

    private LimeTorrentsSearchResult fromMatcher(SearchMatcher matcher) {
        String infoHash = matcher.group("infohash");
        String detailsURL = "https://" + getDomainName() + "/" + matcher.group("detailUrl");
        String filename = matcher.group("filename");
        String title = matcher.group("title");
        String ageString = matcher.group("age");
        String fileSizeMagnitude = matcher.group("fileSizeMagnitude");
        String fileSizeUnit = matcher.group("fileSizeUnit");
        String seeds = matcher.group("seeds");
        return new LimeTorrentsSearchResult(detailsURL, infoHash, filename, fileSizeMagnitude, fileSizeUnit, ageString, seeds, title);
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}