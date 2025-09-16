/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Himanshu Sharma (HimanshuSharma789)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
        super("idope.se", token, keywords, timeout, 1, 0);
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
