/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * V2 pattern-based search for Torrentz2 torrent search.
 * Torrentz2 returns complete torrent metadata on search page, no crawling needed.
 *
 * @author gubatron
 */
public class Torrentz2SearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(Torrentz2SearchPattern.class);
    private static final String DOMAIN = "torrentz2.nz";
    private static Pattern searchPattern;
    private static Pattern sizePattern;
    private static Pattern seedersPattern;

    public Torrentz2SearchPattern() {
        if (searchPattern == null) {
            try {
                // New pattern for modern Torrentz2 HTML (2024+ redesign)
                // Capture title and magnet link - extract hash from magnet link instead of URL
                // The /torrent/XXXXX URLs use short hashes (24 chars), but magnet links have full SHA1 hashes (40 chars)
                // We match from title through the magnet link, capturing metadata in between
                searchPattern = Pattern.compile(
                    "(?is)<a\\s+href=\"/torrent/[a-f0-9]+\"[^>]*>\\s*(?<title>[^<]*?)\\s*</a>" +
                    "(?<metadata>.*?)" +  // Capture everything between title and magnet (includes size/seeders)
                    "<a\\s+href=\"(?<magnet>magnet:\\?[^\"]*)\"[^>]*>\\s*(?:<i[^>]*></i>)?\\s*Magnet"
                );

                // Extract size from: <i class="fas fa-download"></i>\s*<span>(?<size>.*?)</span>
                sizePattern = Pattern.compile("(?is)<i\\s+class=\"fas\\s+fa-download\"></i>\\s*<span>(?<size>[^<]+)</span>");

                // Extract seeders from: seeders</span>\s*</span>\s*<span class="inline-flex[^>]*>(?:.*?)<span class="font-medium">(?<seeders>\\d+)
                seedersPattern = Pattern.compile("(?is)seeders</span>.*?<span\\s+class=\"font-medium\">(?<seeders>\\d+)</span>");
            } catch (PatternSyntaxException e) {
                LOG.error("Error compiling Torrentz2 search results pattern", e);
            }
        }
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        // Torrentz2 expects spaces as + (not %20)
        // encodedKeywords come in with %20, so we replace them
        return "https://" + DOMAIN + "/search?q=" + encodedKeywords.replace("%20", "+");
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        try {
            // Find the start of torrents section
            int startOffset = responseBody.indexOf("Torrents");
            String reducedPage = startOffset > 0 ? responseBody.substring(startOffset) : responseBody;

            LOG.debug("Torrentz2: Searching for 'Torrents' marker - found at offset: " + startOffset);
            LOG.debug("Torrentz2: Response body length: " + responseBody.length());
            LOG.debug("Torrentz2: Reduced page length: " + reducedPage.length());

            Matcher matcher = searchPattern.matcher(reducedPage);
            int resultCount = 0;

            LOG.debug("Torrentz2: Pattern matching started");

            LOG.debug("Torrentz2: Starting pattern matching on " + reducedPage.length() + " characters");

            while (matcher.find() && resultCount < 100) {
                try {
                    String magnetLink = matcher.group("magnet");
                    String title = matcher.group("title");
                    String metadata = matcher.group("metadata");

                    LOG.debug("Torrentz2: Found match - title: " + title);

                    if (StringUtils.isNullOrEmpty(title) || StringUtils.isNullOrEmpty(magnetLink)) {
                        continue;
                    }

                    // Parse display name (remove HTML tags and decode entities)
                    String displayName = title.replaceAll("<.*?>", "").trim();
                    // Decode HTML entities like &amp; to &, &#x27; to ', etc.
                    displayName = HtmlManipulator.replaceHtmlEntities(displayName);
                    if (displayName.length() > 150) {
                        displayName = displayName.substring(0, 150);
                    }

                    // Decode HTML entities and URL encoding in magnet link
                    String decodedMagnetLink = magnetLink;
                    try {
                        // Replace specific HTML numeric entities we know are in magnet links
                        // &#x3D; -> =, &#x3B; -> ;, &#x26; -> & (though &amp; should handle this)
                        decodedMagnetLink = decodedMagnetLink.replace("&#x3D;", "=");    // =
                        decodedMagnetLink = decodedMagnetLink.replace("&#x3B;", ";");    // ;
                        decodedMagnetLink = decodedMagnetLink.replace("&#x26;", "&");    // &
                        decodedMagnetLink = decodedMagnetLink.replace("&#x2F;", "/");    // /
                        decodedMagnetLink = decodedMagnetLink.replace("&#x3A;", ":");    // :

                        // Replace named HTML entities
                        decodedMagnetLink = HtmlManipulator.replaceHtmlEntities(decodedMagnetLink);

                        // Finally URL decode (handles %3A -> :, %2F -> /, etc.)
                        decodedMagnetLink = URLDecoder.decode(decodedMagnetLink, "UTF-8");
                    } catch (Exception e) {
                        LOG.warn("Torrentz2: Failed to decode magnet link: " + e.getMessage(), e);
                        continue;
                    }
                    LOG.debug("Torrentz2: Magnet link decoded - original length: " + magnetLink.length() + ", decoded length: " + decodedMagnetLink.length());

                    // Verify the decoded magnet link has proper structure
                    if (!decodedMagnetLink.startsWith("magnet:?") || !decodedMagnetLink.contains("xt=urn:btih:")) {
                        LOG.warn("Torrentz2: Invalid magnet link structure after decoding, skipping result");
                        continue;
                    }

                    // Extract the actual SHA1 hash from the magnet link (40 hex chars)
                    String infoHash = UrlUtils.extractInfoHash(decodedMagnetLink);
                    if (StringUtils.isNullOrEmpty(infoHash)) {
                        LOG.warn("Torrentz2: Could not extract info hash from magnet link");
                        LOG.warn("Torrentz2: Original: " + magnetLink.substring(0, Math.min(100, magnetLink.length())));
                        LOG.warn("Torrentz2: Decoded: " + decodedMagnetLink.substring(0, Math.min(100, decodedMagnetLink.length())));
                        continue;
                    }
                    LOG.info("Torrentz2: Extracted hash from magnet link: " + infoHash);

                    // Update magnetLink to use decoded version for the result
                    magnetLink = decodedMagnetLink;

                    long size = -1;
                    int seeds = 0;

                    // Extract size and seeders from the captured metadata (between title and magnet link)
                    if (!StringUtils.isNullOrEmpty(metadata)) {
                        // Extract size: "1.95 GB", "2.5 MB", etc.
                        Matcher sizeMatcher = sizePattern.matcher(metadata);
                        if (sizeMatcher.find()) {
                            String sizeStr = sizeMatcher.group("size").trim();
                            size = parseSize(sizeStr);
                            LOG.debug("Torrentz2: Parsed size: '" + sizeStr + "' -> " + size + " bytes");
                        }

                        // Extract seeders count
                        Matcher seedMatcher = seedersPattern.matcher(metadata);
                        if (seedMatcher.find()) {
                            try {
                                seeds = Integer.parseInt(seedMatcher.group("seeders"));
                                LOG.debug("Torrentz2: Parsed seeders: " + seeds);
                            } catch (NumberFormatException e) {
                                LOG.debug("Torrentz2: Failed to parse seeders", e);
                            }
                        }
                    }

                    // Create details URL using the full SHA1 hash
                    String detailsUrl = "https://" + DOMAIN + "/torrent/" + infoHash;

                    long creationTime = System.currentTimeMillis();

                    // Create complete result - Torrentz2 provides everything on search page
                    CompositeFileSearchResult result = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(displayName + ".torrent")
                            .size(size)
                            .detailsUrl(detailsUrl)
                            .source("torrentz2")
                            .creationTime(creationTime)
                            .torrent(magnetLink, infoHash, seeds, magnetLink)
                            .preliminary(false)  // Complete result from search page
                            .build();

                    LOG.debug("Torrentz2: Created result - displayName: '" + displayName + "', hash: " + infoHash +
                             ", isTorrent: " + result.isTorrent() + ", size: " + result.getSize() + " bytes, seeds: " + seeds);
                    results.add(result);
                    resultCount++;
                } catch (Exception e) {
                    LOG.warn("Error parsing Torrentz2 result: " + e.getMessage(), e);
                }
            }
            LOG.debug("Torrentz2: Finished parsing - found " + resultCount + " results, returning " + results.size());
        } catch (Exception e) {
            LOG.error("Error parsing Torrentz2 response: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Parse size string like "1.95 GB", "2.5 MB", "512 KB" into bytes
     */
    private long parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            return -1;
        }

        sizeStr = sizeStr.trim().toUpperCase();

        // Extract number and unit
        String[] parts = sizeStr.split("\\s+");
        if (parts.length < 2) {
            return -1;
        }

        try {
            double size = Double.parseDouble(parts[0]);
            String unit = parts[1];

            // Convert to bytes
            switch (unit) {
                case "B":
                    return (long) size;
                case "KB":
                    return (long) (size * 1024);
                case "MB":
                    return (long) (size * 1024 * 1024);
                case "GB":
                    return (long) (size * 1024 * 1024 * 1024);
                case "TB":
                    return (long) (size * 1024 * 1024 * 1024 * 1024);
                default:
                    LOG.debug("Torrentz2: Unknown size unit: " + unit);
                    return -1;
            }
        } catch (NumberFormatException e) {
            LOG.debug("Torrentz2: Failed to parse size number from: " + sizeStr, e);
            return -1;
        }
    }

}
