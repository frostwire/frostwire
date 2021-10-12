/*
 * Copyright (c) 2011-2021, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torrentz2;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentSearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is an example of a simple search performer, it can deduct all magnet urls and search result details right out of
 * the search result page in a single HTTP request.
 */
public class Torrentz2SearchPerformer extends TorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(Torrentz2SearchPerformer.class);
    private final Pattern pattern;
    private final Pattern infohashPattern;
    private final String unencodedKeywords;

    public Torrentz2SearchPerformer(long token, String keywords, int timeout) {
        //https://torrentz2.eu
        //https://torrentz2.unblockninja.com/
        super("torrentz2.unblockninja.com", token, keywords, timeout, 1, 0);
        //pattern = Pattern.compile("(?is)<dl><dt><a href=/(?<infohash>[a-f0-9]{40})>(?<filename>.*?)</a>.*?<span title=([0-9]+)>(?<age>.*?)</span>.*?<span>(?<filesize>.*?) (?<unit>[BKMGTPEZY]+)</span><span>(?<seeds>\\d+)</span>.*?</dd></dl>");
        //Pattern.compile("(?is)<td data-title=\"Name\">.*?<span>(?<filename>.*?)</span>.*?</td>.*?<td class=\"description-data\" data-title=\"Description\">Uploaded (?<uploaddate>.*?), Size (?<size>.*?), ULed.*?<a href=\"(?<magnet>.*?)\" target=\"_blank\" class=\"magnet-link\">");
        pattern = Pattern.compile("(?is)<td data-title=\"Name\">.*?<span>(?<filename>.*?)</span>.*?<td class=\"age-data\" data-title=\"Last Updated\">(?<age>.*?)</td>.*?<td data-title=\"Last Updated\">(?<seeds>\\d+)</td>.*?<td data-title=\"Size\">(?<filesize>.*?) (?<unit>[BKMGTPEZY]+)</td>.*?<td class=\"file-link\" data-title=\"Magnet\">.*?<a href=\"(?<magnet>.*?)\" target=\"_blank\" class=\"magnet-link\">");
        infohashPattern = Pattern.compile("([0-9A-Fa-f]{40})");
        unencodedKeywords = keywords;
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/kick.php?q=" + unencodedKeywords.replace(" ","+");
    }

    private Torrentz2SearchResult fromMatcher(SearchMatcher matcher) {
        String filename = matcher.group("filename");
        String ageString = matcher.group("age");
        String fileSizeMagnitude = matcher.group("filesize");
        String fileSizeUnit = matcher.group("unit");
        String magnetUrl = matcher.group("magnet");

        String infoHash = "";
        Matcher infohashMatcher = infohashPattern.matcher(magnetUrl);
        if (infohashMatcher.find()) {
            infoHash = infohashMatcher.group(1);
        }

        //String infoHash = matcher.group("infohash");
        String detailsURL = "https://" + getDomainName() + "/" + infoHash;


        int seeds = 20;
        try {
            seeds = Integer.parseInt(matcher.group("seeds"));
        } catch (Throwable ignored) {
        }
        return new Torrentz2SearchResult(detailsURL, infoHash, filename, fileSizeMagnitude, fileSizeUnit, ageString, seeds, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
    }

    @Override
    protected List<? extends Torrentz2SearchResult> searchPage(String page) {
        if (null == page || page.isEmpty()) {
            stopped = true;
            return Collections.emptyList();
        }
        ArrayList<Torrentz2SearchResult> results = new ArrayList<>(0);
        SearchMatcher matcher = new SearchMatcher((pattern.matcher(page.substring(page.indexOf("Total Results")))));
        boolean matcherFound;
        int MAX_RESULTS = 100;
        do {
            try {
                matcherFound = matcher.find();
            } catch (Throwable t) {
                matcherFound = false;
                LOG.error("searchPage() has failed.\n" + t.getMessage(), t);
            }
            if (matcherFound) {
                try {
                    Torrentz2SearchResult sr = fromMatcher(matcher);
                    results.add(sr);
                    LOG.info("Adding a new search result -> " + sr.getDisplayName() + ":" + sr.getSize() + ":" + sr.getTorrentUrl());
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                }
            } else if (results.size() < 5) {
                LOG.warn("Torrentz2SearchPerformer search matcher broken. Please notify at https://github.com/frostwire/frostwire/issues/new");
            }
        } while (matcherFound && !isStopped() && results.size() <= MAX_RESULTS);
        return results;
    }
}
