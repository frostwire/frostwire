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

package com.frostwire.search.torrentz2;

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * This is an example of a simple search performer, it can deduct all magnet urls and search result details right out of
 * the search result page in a single HTTP request.
 */
public class Torrentz2SearchPerformer extends SimpleTorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(Torrentz2SearchPerformer.class);
    private static Pattern pattern;
    private final String unencodedKeywords;

    public Torrentz2SearchPerformer(long token, String keywords, int timeout) {
        //https://torrentz2.eu
        //https://torrentz2.unblockninja.com/
        //https://torrentz2.nz/ 2023-02-26
        super("torrentz2.nz", token, keywords, timeout, 1, 0);
        if (pattern == null) {
            pattern = Pattern.compile("(?is)<dl><dt><a href=\".*?\" target=\"_blank\">(?<filename>.*?)</a></dt>"+
                    "<dd><span><a href=\"(?<magnet>.*?)\"><i class=\"fa-solid fa-magnet\"></i></a></span>" +
                    "<span title=\"\\d+\">(?<age>.*?)</span><span>(?<filesize>.*?)</span><span>(?<seeds>\\d+)</span>");
        }
        unencodedKeywords = keywords;
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search?q=" + unencodedKeywords.replace(" ","+");
    }

    private Torrentz2SearchResult fromMatcher(SearchMatcher matcher) {
        String filename = matcher.group("filename");
        String ageString = matcher.group("age");
        String fileSizeMagnitude = matcher.group("filesize");
        String fileSizeUnit = matcher.group("unit");
        String magnetUrl = matcher.group("magnet");

        String infoHash = UrlUtils.extractInfoHash(magnetUrl);

        //String infoHash = matcher.group("infohash");
        String detailsURL = "https://" + getDomainName() + "/" + infoHash;


        int seeds = 20;
        try {
            seeds = Integer.parseInt(Objects.requireNonNull(matcher.group("seeds")),20);
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
        int ffOffset = page.indexOf("Torrents");
        String pageString = page.substring(ffOffset > 0 ? ffOffset : 0);
        SearchMatcher matcher = new SearchMatcher((pattern.matcher(pageString)));
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
                    //LOG.info("Adding a new search result -> " + sr.getHash());
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                }
            } else if (results.size() < 5) {
                LOG.warn("Torrentz2SearchPerformer search matcher broken. Please notify at https://github.com/frostwire/frostwire/issues/new");
            }
        } while (matcherFound && !isStopped() && results.size() <= MAX_RESULTS);
        return results;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}
