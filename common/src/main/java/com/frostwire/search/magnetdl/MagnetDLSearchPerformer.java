/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
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

package com.frostwire.search.magnetdl;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 */
public class MagnetDLSearchPerformer extends SimpleTorrentSearchPerformer {
    private static Logger LOG = Logger.getLogger(MagnetDLSearchPerformer.class);
    private static Pattern pattern;
    private final String nonEncodedKeywords;
    private int minSeeds = 1;

    public MagnetDLSearchPerformer(long token, String keywords, int timeout) {
        super("magnetdl.com", token, keywords, timeout, 1, 0);
        nonEncodedKeywords = keywords;
        if (pattern == null) {
            pattern = Pattern.compile("(?is)<td class=\"m\"><a href=\"(?<magnet>.*?)\" title=.*?<img.*?</td>" +
                    "<td class=\"n\"><a href=\"(?<detailUrl>.*?)\" title=\"(?<title>.*?)\">.*?</td>" +
                    "<td>(?<age>.*?)</td>" +
                    "<td class=\"t[0-9]\">.*?</td><td>.*?</td>.*?<td>(?<fileSizeMagnitude>.*?) (?<fileSizeUnit>[A-Z]+)</td>" +
                    "<td class=\"s\">(?<seeds>.*?)</td>");
        }
    }

    public void setMinSeeds(int minSeeds) {
        this.minSeeds = minSeeds;
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        //disregard encoded keywords when it comes to the URL
        String transformedKeywords = nonEncodedKeywords.replace("%20", "-");
        return "https://" + getDomainName() + "/" + transformedKeywords.subSequence(0, 1) + "/" + transformedKeywords + "/";
    }

    @Override
    protected List<MagnetDLSearchResult> searchPage(String page) {
        final String HTML_PREFIX_MARKER = "<tbody>";
        int htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER) + HTML_PREFIX_MARKER.length();
        final String HTML_SUFFIX_MARKER = "Download Search Help";
        int htmlSuffixIndex = page.indexOf(HTML_SUFFIX_MARKER);

        String reducedHtml = page.substring(htmlPrefixIndex, htmlSuffixIndex > 0 ? htmlSuffixIndex : page.length() - htmlPrefixIndex);

        ArrayList<MagnetDLSearchResult> results = new ArrayList<>(0);
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
                MagnetDLSearchResult sr = fromMatcher(matcher);
                if (sr.getSeeds() >= minSeeds) {
                    //LOG.info("Adding a new search result -> " + sr.getHash());
                    results.add(sr);
                } 
            } else {
                LOG.warn("MagnetDLSearchPerformer::searchPage(String page): search matcher broken. Please notify at https://github.com/frostwire/frostwire/issues/new");
                //LOG.warn("========");
                //LOG.warn(reducedHtml);
                //LOG.warn("========");
                if (getListener() != null && results.size() < 5) {
                    getListener().onError(getToken(), new SearchError(0, "Search Matcher Broken"));
                }
            }
        } while (matcherFound && !isStopped() && results.size() <= MAX_RESULTS);
        return results;
    }

    private MagnetDLSearchResult fromMatcher(SearchMatcher matcher) {
        String magnet = matcher.group("magnet");
        String detailsURL = "https://" + getDomainName() + matcher.group("detailUrl");
        String title = matcher.group("title");
        String ageString = matcher.group("age");
        String fileSizeMagnitude = matcher.group("fileSizeMagnitude");
        String fileSizeUnit = matcher.group("fileSizeUnit");
        String seeds = matcher.group("seeds");
        return new MagnetDLSearchResult(detailsURL, magnet, fileSizeMagnitude, fileSizeUnit, ageString, seeds, title);
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}
