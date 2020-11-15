/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.search.limetorrents;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentSearchPerformer;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 */
public class LimeTorrentsSearchPerformer extends TorrentSearchPerformer {
    private static Logger LOG = Logger.getLogger(LimeTorrentsSearchPerformer.class);
    private final Pattern pattern;
    private static final String SEARCH_RESULT_PAGE_REGEX =
            "(?is)<div class=\"tt-name\"><a href=\"http://itorrents.org/torrent/(?<infohash>.*?)\\.torrent\\?title=(?<filename>.*?)\" rel=\"nofollow\" class=\"csprite_dl14\"></a>" +
                    "<a href=\"/(?<detailUrl>.*?.html)\">(?<title>.*?)</.*?<div class=\"tt-options\"></div></td>.*?" +
                    "<td class=\"tdnormal\">(?<age>.*?) -.*?</a></td>.*?" + //they do have an HTML-DOM typo there with that weird </a> inside the </td>
                    "<td class=\"tdnormal\">(?<fileSizeMagnitude>.*?) (?<fileSizeUnit>[A-Z]+)</td>.*?" +
                    "<td class=\"tdseed\">(?<seeds>.*?)</td>.*?";

    public LimeTorrentsSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1, 1);
        pattern = Pattern.compile(SEARCH_RESULT_PAGE_REGEX);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
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
}