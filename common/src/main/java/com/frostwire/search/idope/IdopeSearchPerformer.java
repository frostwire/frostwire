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
    private static Pattern oldPattern; // For fallback to original idope.se structure
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
            
            // Fallback pattern for original idope.se structure
            oldPattern = Pattern.compile("(?is)<img class=\"resultdivtopimg\".*?" +
                    "<a href=\"/torrent/(?<keyword>.*?)/(?<infohash>.*?)/\".*?" +
                    "<div  class=\"resultdivtopname\" >[\n][\\s|\t]+(?<filename>.*?)</div>.*?" +
                    "<div class=\"resultdivbottontime\">(?<age>.*?)</div>.*?" +
                    "<div class=\"resultdivbottonlength\">(?<filesize>.*?)\\p{Z}(?<unit>.*?)</div>.*?" +
                    "<div class=\"resultdivbottonseed\">(?<seeds>.*?)</div>");
        }
    }

    public IdopeSearchPerformer(String domain, long token, String keywords, int timeout) {
        super(domain, token, keywords, timeout, 1, 0);
        // Use the same patterns regardless of domain
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        // Try idope.io first with the new search format
        if (getDomainName().equals("idope.io")) {
            return "https://" + getDomainName() + "/search/?q=" + encodedKeywords;
        }
        // Fallback for idope.hair domain with different URL pattern
        if (getDomainName().equals("idope.hair")) {
            return "https://" + getDomainName() + "/lmsearch?q=" + encodedKeywords + "&cat=lmsearch";
        }
        // Default fallback
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

    private IdopeSearchResult fromOldMatcher(SearchMatcher matcher) {
        String infoHash = matcher.group("infohash");
        String detailsURL = "https://" + getDomainName() + "/torrent/" + matcher.group("keyword") + "/" + infoHash;
        String filename = matcher.group("filename");
        String fileSizeMagnitude = matcher.group("filesize");
        String fileSizeUnit = matcher.group("unit");
        String ageString = matcher.group("age");
        int seeds = 0;
        try {
            seeds = Integer.parseInt(matcher.group("seeds").trim());
        } catch (NumberFormatException e) {
            // Default to 0 if seeds can't be parsed
        }
        return new IdopeSearchResult(detailsURL, infoHash, filename, fileSizeMagnitude, fileSizeUnit, ageString, seeds, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
    }

    @Override
    protected List<? extends IdopeSearchResult> searchPage(String page) {
        if (null == page || page.isEmpty()) {
            stopped = true;
            return Collections.emptyList();
        }

        // Try multiple HTML structure patterns for different website layouts
        String HTML_PREFIX_MARKER = "<tbody>";
        String HTML_SUFFIX_MARKER = "</tbody>";
        int htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER);
        
        // If <tbody> not found, try alternative structures
        if (htmlPrefixIndex == -1) {
            // Try alternative structure for idope.hair or other layouts
            HTML_PREFIX_MARKER = "<div id=\"div2child\">";
            HTML_SUFFIX_MARKER = "<div id=\"rightdiv\">";
            htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER);
            
            if (htmlPrefixIndex == -1) {
                // Try looking for table structure without tbody
                HTML_PREFIX_MARKER = "<table";
                HTML_SUFFIX_MARKER = "</table>";
                htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER);
            }
            
            if (htmlPrefixIndex == -1) {
                // Check for DDOS protection or completely different structure
                isDDOSProtectionActive = page.contains("Cloudflare") || page.contains("ddos") || page.contains("bot protection");
                if (!isDDOSProtectionActive) {
                    LOG.warn("IdopeSearchPerformer search HTML structure not found. Page length: " + page.length() + ". Please notify at https://github.com/frostwire/frostwire/issues/new");
                    // Log first 500 characters for debugging
                    LOG.warn("First 500 chars of page: " + (page.length() > 500 ? page.substring(0, 500) : page));
                } else {
                    LOG.warn("IdopeSearchPerformer search disabled. DDOS protection active.");
                }
                return Collections.emptyList();
            }
        }
        
        htmlPrefixIndex += HTML_PREFIX_MARKER.length();
        int htmlSuffixIndex = page.indexOf(HTML_SUFFIX_MARKER, htmlPrefixIndex);

        String reducedHtml = page.substring(htmlPrefixIndex, htmlSuffixIndex > 0 ? htmlSuffixIndex : page.length());

        ArrayList<IdopeSearchResult> results = new ArrayList<>(0);
        
        // Try the new MagnetDL-like pattern first
        SearchMatcher matcher = new SearchMatcher((pattern.matcher(reducedHtml)));
        boolean matcherFound;
        boolean usingOldPattern = false;
        int MAX_RESULTS = 10;
        
        do {
            try {
                matcherFound = matcher.find();
            } catch (Throwable t) {
                matcherFound = false;
                LOG.error("searchPage() has failed with new pattern.\n" + t.getMessage(), t);
            }
            if (matcherFound) {
                IdopeSearchResult sr = fromMatcher(matcher);
                results.add(sr);
            }
        } while (matcherFound && !isStopped() && results.size() < MAX_RESULTS);
        
        // If new pattern didn't work, try the old pattern
        if (results.isEmpty() && !isStopped()) {
            LOG.info("IdopeSearchPerformer: Trying fallback to old pattern format");
            matcher = new SearchMatcher((oldPattern.matcher(reducedHtml)));
            usingOldPattern = true;
            
            do {
                try {
                    matcherFound = matcher.find();
                } catch (Throwable t) {
                    matcherFound = false;
                    LOG.error("searchPage() has failed with old pattern.\n" + t.getMessage(), t);
                }
                if (matcherFound) {
                    IdopeSearchResult sr = fromOldMatcher(matcher);
                    results.add(sr);
                }
            } while (matcherFound && !isStopped() && results.size() < MAX_RESULTS);
        }
        
        // If both patterns failed, check for DDOS protection
        if (results.isEmpty() && !isStopped()) {
            isDDOSProtectionActive = reducedHtml.contains("Cloudflare") || reducedHtml.contains("ddos") || reducedHtml.contains("bot protection");
            if (!isDDOSProtectionActive) {
                LOG.warn("IdopeSearchPerformer search matcher broken. Tried both new and old patterns. Please notify at https://github.com/frostwire/frostwire/issues/new");
                LOG.warn("Reduced HTML length: " + reducedHtml.length() + ", first 200 chars: " + (reducedHtml.length() > 200 ? reducedHtml.substring(0, 200) : reducedHtml));
            } else {
                LOG.warn("IdopeSearchPerformer search matcher disabled. DDOS protection active.");
            }
        } else if (!results.isEmpty()) {
            LOG.info("IdopeSearchPerformer: Found " + results.size() + " results using " + (usingOldPattern ? "old" : "new") + " pattern");
        }
        
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
