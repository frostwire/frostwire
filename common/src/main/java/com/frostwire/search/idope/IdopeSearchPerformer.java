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
        super("idope.io", token, keywords, timeout, 1, 0);
        if (pattern == null) {
            // Updated pattern for MagnetDL-like structure (idope.io now uses similar HTML structure)
            // Alternative domain idope.hair uses different URL pattern: /lmsearch?q=keywords&cat=lmsearch
            pattern = Pattern.compile("(?is)<td class=\"m\"><a href=\"(?<magnet>.*?)\" title=.*?<img.*?</td>" +
                    "<td class=\"n\"><a href=\"(?<detailUrl>.*?)\" title=\"(?<title>.*?)\">.*?</td>" +
                    "<td>(?<age>.*?)</td>" +
                    "<td class=\"t[0-9]\">.*?</td><td>.*?</td>.*?<td>(?<fileSizeMagnitude>.*?) (?<fileSizeUnit>[A-Z]+)</td>" +
                    "<td class=\"s\">(?<seeds>.*?)</td>");
        }
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/search/?q=" + encodedKeywords;
    }

    private IdopeSearchResult fromMatcher(SearchMatcher matcher) {
        String magnet = matcher.group("magnet");
        String detailsURL = "https://" + getDomainName() + matcher.group("detailUrl");
        String title = matcher.group("title");
        String ageString = matcher.group("age");
        String fileSizeMagnitude = matcher.group("fileSizeMagnitude");
        String fileSizeUnit = matcher.group("fileSizeUnit");
        String seedsStr = matcher.group("seeds");
        int seeds = 0;
        try {
            seeds = Integer.parseInt(seedsStr.trim());
        } catch (NumberFormatException e) {
            // Default to 0 if seeds can't be parsed
        }
        
        // Extract info hash from magnet URL
        String infoHash = "";
        if (magnet != null && magnet.contains("xt=urn:btih:")) {
            int start = magnet.indexOf("xt=urn:btih:") + 12;
            int end = magnet.indexOf("&", start);
            if (end == -1) end = magnet.length();
            if (start < magnet.length() && end > start) {
                infoHash = magnet.substring(start, Math.min(start + 40, end));
                // Ensure we have a valid 40-character hash
                if (infoHash.length() < 40) {
                    LOG.warn("IdopeSearchPerformer: Invalid info hash length: " + infoHash.length());
                }
            }
        } else {
            LOG.warn("IdopeSearchPerformer: Could not extract info hash from magnet URL: " + (magnet != null ? magnet.substring(0, Math.min(magnet.length(), 50)) : "null"));
        }
        
        return new IdopeSearchResult(detailsURL, infoHash, title, fileSizeMagnitude, fileSizeUnit, ageString, seeds, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
    }

    @Override
    protected List<? extends IdopeSearchResult> searchPage(String page) {
        if (null == page || page.isEmpty()) {
            stopped = true;
            return Collections.emptyList();
        }

        // Updated HTML markers for MagnetDL-like structure
        final String HTML_PREFIX_MARKER = "<tbody>";
        int htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER);
        if (htmlPrefixIndex == -1) {
            // Fallback to old structure or check for DDOS protection
            isDDOSProtectionActive = page.contains("Cloudflare");
            if (!isDDOSProtectionActive) {
                LOG.warn("IdopeSearchPerformer search HTML structure not found. Please notify at https://github.com/frostwire/frostwire/issues/new");
            } else {
                LOG.warn("IdopeSearchPerformer search disabled. DDOS protection active.");
            }
            return Collections.emptyList();
        }
        
        htmlPrefixIndex += HTML_PREFIX_MARKER.length();
        final String HTML_SUFFIX_MARKER = "</tbody>";
        int htmlSuffixIndex = page.indexOf(HTML_SUFFIX_MARKER, htmlPrefixIndex);

        String reducedHtml = page.substring(htmlPrefixIndex, htmlSuffixIndex > 0 ? htmlSuffixIndex : page.length());

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
