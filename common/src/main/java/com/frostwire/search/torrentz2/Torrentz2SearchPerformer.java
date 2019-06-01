/*
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torrentz2;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.TorrentSearchPerformer;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is an example of a simple search performer, it can deduct all magnet urls and search result details right out of
 * the search result page in a single HTTP request.
 */
public class Torrentz2SearchPerformer extends TorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(Torrentz2SearchPerformer.class);
    private static final int MAX_RESULTS = 30;
    public static final String REGEX =
            "(?is)<dl><dt><a href=/(?<infohash>[a-f0-9]{40})>(?<filename>.*?)</a>.*?&#x2714;</span><span title=(\\d+)?>(?<age>.*?)</span><span>(?<filesize>.*?) (?<unit>[BKMGTPEZY]+)</span><span>(?<seeds>\\d+)</span><span>.*?";
    private String usualTrackers;
    private final Pattern PATTERN = Pattern.compile(REGEX);

    public Torrentz2SearchPerformer(long token, String keywords, int timeout) {
        super("torrentz2.eu", token, keywords, timeout, 1, 0);
        usualTrackers = "tr=udp://tracker.leechers-paradise.org:6969/announce&" +
                "tr=udp://tracker.coppersurfer.tk:6969/announce&" +
                "tr=udp://tracker.internetwarriors.net:1337/announce&" +
                "tr=udp://retracker.akado-ural.ru:80/announce&" +
                "tr=udp://tracker.moeking.me:6969/announce&" +
                "tr=udp://carapax.net:6969/announce&" +
                "tr=udp://retracker.baikal-telecom.net:2710/announce&" +
                "tr=udp://bt.dy20188.com:80/announce&" +
                "tr=udp://tracker.nyaa.uk:6969/announce&" +
                "tr=udp://carapax.net:6969/announce&" +
                "tr=udp://amigacity.xyz:6969/announce&" +
                "tr=udp://tracker.supertracker.net:1337/announce&" +
                "tr=udp://tracker.cyberia.is:6969/announce&" +
                "tr=udp://tracker.openbittorrent.com:80/announce&" +
                "tr=udp://tracker.msm8916.com:6969/announce&" +
                "tr=udp://tracker.sktorrent.net:6969/announce&";
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        String transformedKeywords = encodedKeywords.replace("0%20", "-");
        return "https://" + getDomainName() + "/verified?f=" + transformedKeywords;
    }

    private Torrentz2SearchResult fromMatcher(SearchMatcher matcher) {
        String infoHash = matcher.group("infohash");
        String detailsURL = "https://" + getDomainName() + "/" + infoHash;
        String filename = matcher.group("filename");
        String fileSizeMagnitude = matcher.group("filesize");
        String fileSizeUnit = matcher.group("unit");
        String ageString = matcher.group("age");
        int seeds = 20;
        try {
            seeds = Integer.parseInt(matcher.group("seeds"));
        } catch (Throwable ignored) {
        }
        return new Torrentz2SearchResult(detailsURL, infoHash, filename, fileSizeMagnitude, fileSizeUnit, ageString, seeds, usualTrackers);
    }

    @Override
    protected List<? extends Torrentz2SearchResult> searchPage(String page) {
        if (null == page || page.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Torrentz2SearchResult> results = new ArrayList<>(0);
        SearchMatcher matcher = new SearchMatcher((PATTERN.matcher(page)));
        boolean matcherFound;
        do {
            try {
                matcherFound = matcher.find();
            } catch (Throwable t) {
                matcherFound = false;
                LOG.error("searchPage() has failed.\n" + t.getMessage(), t);
            }

            if (matcherFound) {
                Torrentz2SearchResult sr = fromMatcher(matcher);
                if (sr != null) {
                    results.add(sr);
                    System.out.println("Adding a new search result -> " + sr.getDisplayName() + ":" + sr.getSize() + ":" + sr.getTorrentUrl());
                }
            }
        } while (matcherFound && !isStopped() && results.size() <= MAX_RESULTS);
        return results;
    }
}
