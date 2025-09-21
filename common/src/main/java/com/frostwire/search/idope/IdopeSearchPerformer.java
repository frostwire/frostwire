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
    private static Pattern modernPattern; // For modern Bootstrap-based structure
    private boolean isDDOSProtectionActive;

    public IdopeSearchPerformer(long token, String keywords, int timeout) {
        // Switch to idope.hair as primary since idope.io uses dynamic JavaScript loading
        super("idope.hair", token, keywords, timeout, 1, 0);
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
            
            // Modern pattern for Bootstrap-based layouts (more generic)
            modernPattern = Pattern.compile("(?is)<a[^>]*href=\"(?<magnet>magnet:[^\"]+)\"[^>]*>.*?" +
                    "(?:<div[^>]*>|<span[^>]*>|<p[^>]*>)(?<title>[^<]+)(?:</div>|</span>|</p>).*?" +
                    "(?:<div[^>]*>|<span[^>]*>)(?<fileSizeMagnitude>[0-9\\.]+)\\s*(?<fileSizeUnit>[KMGT]?B)(?:</div>|</span>).*?" +
                    "(?:<div[^>]*>|<span[^>]*>)(?<seeds>[0-9]+)(?:</div>|</span>)");
        }
    }

    public IdopeSearchPerformer(String domain, long token, String keywords, int timeout) {
        super(domain, token, keywords, timeout, 1, 0);
        // Use the same patterns regardless of domain
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        // Use idope.hair as primary since idope.io loads results via JavaScript
        if (getDomainName().equals("idope.hair")) {
            return "https://" + getDomainName() + "/lmsearch?q=" + encodedKeywords + "&cat=lmsearch";
        }
        // Fallback for idope.io domain (though it uses dynamic loading)
        if (getDomainName().equals("idope.io")) {
            return "https://" + getDomainName() + "/search/?q=" + encodedKeywords;
        }
        // Default fallback - use idope.hair pattern
        return "https://idope.hair/lmsearch?q=" + encodedKeywords + "&cat=lmsearch";
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

    private IdopeSearchResult fromModernMatcher(SearchMatcher matcher) {
        String magnet = matcher.group("magnet");
        String title = matcher.group("title");
        String fileSizeMagnitude = matcher.group("fileSizeMagnitude");
        String fileSizeUnit = matcher.group("fileSizeUnit");
        String seedsStr = matcher.group("seeds");
        
        // For modern pattern, we might not have age or detail URL
        String ageString = "Unknown";
        String detailsURL = "https://" + getDomainName() + "/"; // Fallback URL
        
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
            }
        }
        
        return new IdopeSearchResult(detailsURL, infoHash, title, fileSizeMagnitude, fileSizeUnit, ageString, seeds, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
    }

    private void logHtmlStructureDebug(String page) {
        LOG.warn("Full page length: " + page.length());
        LOG.warn("First 1000 chars of page: " + (page.length() > 1000 ? page.substring(0, 1000) : page));
        
        // Check for JavaScript-loaded content indicators
        if (page.contains("<!--firstresult();-->") || page.contains("firstresult()")) {
            LOG.warn("DETECTED: Page uses JavaScript to load search results dynamically");
            LOG.warn("This indicates the search results are not in the initial HTML but loaded via AJAX");
            LOG.warn("Consider switching to idope.hair domain which may use static HTML");
        }
        
        // Log what structural elements we can find
        if (page.contains("<main>")) {
            LOG.warn("Found <main> tag at position: " + page.indexOf("<main>"));
        }
        if (page.contains("container-fluid")) {
            LOG.warn("Found container-fluid class at position: " + page.indexOf("container-fluid"));
        }
        if (page.contains("<table")) {
            LOG.warn("Found table elements at position: " + page.indexOf("<table"));
        }
        if (page.contains("<tbody")) {
            LOG.warn("Found tbody elements at position: " + page.indexOf("<tbody"));
        }
        if (page.contains("bootstrap")) {
            LOG.warn("Found Bootstrap framework");
        }
        
        // Look for potential content indicators
        String[] searchTerms = {"magnet:", "torrent", "download", "size", "seed", "leech", "results", "search-results"};
        for (String term : searchTerms) {
            if (page.toLowerCase().contains(term.toLowerCase())) {
                int pos = page.toLowerCase().indexOf(term.toLowerCase());
                LOG.warn("Found potential content indicator: " + term + " at position: " + pos);
                // Show context around the term
                int start = Math.max(0, pos - 100);
                int end = Math.min(page.length(), pos + 200);
                LOG.warn("Context around '" + term + "': " + page.substring(start, end).replaceAll("\\s+", " "));
            }
        }
        
        // Look for common result container patterns
        String[] containerPatterns = {"class=\"result", "class=\"item", "class=\"row", "id=\"result", "search-result", "torrent-item"};
        for (String pattern : containerPatterns) {
            if (page.toLowerCase().contains(pattern.toLowerCase())) {
                int pos = page.toLowerCase().indexOf(pattern.toLowerCase());
                LOG.warn("Found potential result container: " + pattern + " at position: " + pos);
            }
        }
        
        // Check for JavaScript-based dynamic loading patterns
        String[] jsPatterns = {"jquery", "ajax", "fetch(", "XMLHttpRequest", ".load(", "getJSON"};
        for (String pattern : jsPatterns) {
            if (page.toLowerCase().contains(pattern.toLowerCase())) {
                LOG.warn("Found JavaScript dynamic loading indicator: " + pattern);
            }
        }
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
                // Try looking for search results container or content area
                HTML_PREFIX_MARKER = "search-results";
                htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER);
                if (htmlPrefixIndex > 0) {
                    // Find the start of the div that contains this class
                    int divStart = page.lastIndexOf("<div", htmlPrefixIndex);
                    if (divStart != -1) {
                        htmlPrefixIndex = divStart;
                        HTML_PREFIX_MARKER = "<div";
                        HTML_SUFFIX_MARKER = "</div>";
                    } else {
                        htmlPrefixIndex = -1;
                    }
                }
            }
            
            if (htmlPrefixIndex == -1) {
                // Try looking for container with results or content
                HTML_PREFIX_MARKER = "results";
                htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER);
                if (htmlPrefixIndex > 0) {
                    // Find the start of the container that has results
                    int containerStart = page.lastIndexOf("<div", htmlPrefixIndex);
                    if (containerStart != -1) {
                        htmlPrefixIndex = containerStart;
                        HTML_PREFIX_MARKER = "<div";
                        HTML_SUFFIX_MARKER = "</section>";  // Try broader end marker
                    } else {
                        htmlPrefixIndex = -1;
                    }
                }
            }
            
            if (htmlPrefixIndex == -1) {
                // Try the main section but use the whole body for parsing
                HTML_PREFIX_MARKER = "<body";
                HTML_SUFFIX_MARKER = "</body>";
                htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER);
                if (htmlPrefixIndex == -1) {
                    // Last resort: try from main tag onwards but include everything after it
                    HTML_PREFIX_MARKER = "<main>";
                    htmlPrefixIndex = page.indexOf(HTML_PREFIX_MARKER);
                    if (htmlPrefixIndex != -1) {
                        HTML_SUFFIX_MARKER = ""; // No end marker, take everything
                    }
                }
            }
            
            if (htmlPrefixIndex == -1) {
                // Check for DDOS protection or completely different structure
                isDDOSProtectionActive = page.contains("Cloudflare") || page.contains("ddos") || page.contains("bot protection");
                if (!isDDOSProtectionActive) {
                    LOG.warn("IdopeSearchPerformer search HTML structure not found. Page length: " + page.length() + ". Please notify at https://github.com/frostwire/frostwire/issues/new");
                    // Log sections that might contain relevant markers
                    logHtmlStructureDebug(page);
                } else {
                    LOG.warn("IdopeSearchPerformer search disabled. DDOS protection active.");
                }
                return Collections.emptyList();
            }
        }
        
        htmlPrefixIndex += HTML_PREFIX_MARKER.length();
        int htmlSuffixIndex = -1;
        if (!HTML_SUFFIX_MARKER.isEmpty()) {
            htmlSuffixIndex = page.indexOf(HTML_SUFFIX_MARKER, htmlPrefixIndex);
        }

        String reducedHtml = page.substring(htmlPrefixIndex, htmlSuffixIndex > 0 ? htmlSuffixIndex : page.length());

        ArrayList<IdopeSearchResult> results = new ArrayList<>(0);
        
        // Try the new MagnetDL-like pattern first
        SearchMatcher matcher = new SearchMatcher((pattern.matcher(reducedHtml)));
        boolean matcherFound;
        String usingPattern = "new";
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
        
        // If new pattern didn't work, try the modern pattern
        if (results.isEmpty() && !isStopped()) {
            LOG.info("IdopeSearchPerformer: Trying modern Bootstrap pattern");
            matcher = new SearchMatcher((modernPattern.matcher(reducedHtml)));
            usingPattern = "modern";
            
            do {
                try {
                    matcherFound = matcher.find();
                } catch (Throwable t) {
                    matcherFound = false;
                    LOG.error("searchPage() has failed with modern pattern.\n" + t.getMessage(), t);
                }
                if (matcherFound) {
                    IdopeSearchResult sr = fromModernMatcher(matcher);
                    results.add(sr);
                }
            } while (matcherFound && !isStopped() && results.size() < MAX_RESULTS);
        }
        
        // If modern pattern didn't work, try the old pattern
        if (results.isEmpty() && !isStopped()) {
            LOG.info("IdopeSearchPerformer: Trying fallback to old pattern format");
            matcher = new SearchMatcher((oldPattern.matcher(reducedHtml)));
            usingPattern = "old";
            
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
        
        // If all patterns failed, check for DDOS protection
        if (results.isEmpty() && !isStopped()) {
            isDDOSProtectionActive = reducedHtml.contains("Cloudflare") || reducedHtml.contains("ddos") || reducedHtml.contains("bot protection");
            if (!isDDOSProtectionActive) {
                LOG.warn("IdopeSearchPerformer search matcher broken. Tried all patterns (new, modern, old). Please notify at https://github.com/frostwire/frostwire/issues/new");
                LOG.warn("Reduced HTML length: " + reducedHtml.length() + ", first 500 chars: " + (reducedHtml.length() > 500 ? reducedHtml.substring(0, 500) : reducedHtml));
            } else {
                LOG.warn("IdopeSearchPerformer search matcher disabled. DDOS protection active.");
            }
        } else if (!results.isEmpty()) {
            LOG.info("IdopeSearchPerformer: Found " + results.size() + " results using " + usingPattern + " pattern");
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
