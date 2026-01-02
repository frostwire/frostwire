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

package com.frostwire.search.nyaa;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V2 pattern-based search for Nyaa anime torrent search.
 * Nyaa returns complete torrent metadata on search page, no crawling needed.
 *
 * @author gubatron
 */
public class NyaaSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(NyaaSearchPattern.class);
    private static final String DOMAIN = "nyaa.si";
    private static final Map<String, Long> UNIT_TO_BYTES = new HashMap<>();
    private static Pattern searchPattern;
    private static final int MAX_RESULTS = 75;

    static {
        UNIT_TO_BYTES.put("bytes", 1L);
        UNIT_TO_BYTES.put("B", 1L);
        UNIT_TO_BYTES.put("KiB", 1024L);
        UNIT_TO_BYTES.put("MiB", 1024L * 1024L);
        UNIT_TO_BYTES.put("GiB", 1024L * 1024L * 1024L);
        UNIT_TO_BYTES.put("TiB", 1024L * 1024L * 1024L * 1024L);
        UNIT_TO_BYTES.put("PiB", 1024L * 1024L * 1024L * 1024L * 1024L);
    }

    public NyaaSearchPattern() {
        if (searchPattern == null) {
            searchPattern = Pattern.compile(
                    "(?is)<tr class=\"default\">.*?" +
                            "<img src=\"(?<thumbnailurl>.*?)\" alt=.*?" +
                            "<a href=\".*?\" class=\"comments\" title=\".*?\">.*?<i class=\"fa fa-comments-o\"></i>.*?" +
                            "<a href=\"(?<detailsurl>.*?)\" title=\"(?<displayname>.*?)\">.*?<td class=\"text-center\">.*?" +
                            "<a href=\"(?<torrenturl>.*?)\"><i class=\"fa fa-fw fa-download\"></i></a>.*?" +
                            "<a href=\"(?<magneturl>.*?)\"><i class=\"fa fa-fw fa-magnet\"></i></a>.*?" +
                            "<td class=\"text-center\">(?<filesize>.*?)</td>.*?" +
                            "<td class=\"text-center\" data-timestamp=\"(?<timestamp>.*?)\">.*?" +
                            "<td class=\"text-center\">(?<seeds>.*?)</td>");
        }
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/?f=0&c=0_0&q=" + encodedKeywords;
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            LOG.warn("Nyaa: Received empty response body");
            return results;
        }

        try {
            // Skip table header to find data rows
            int offset = responseBody.indexOf("</thead>");
            if (offset == -1) {
                offset = 0;
            }
            String reducedPage = responseBody.substring(offset);

            Matcher matcher = searchPattern.matcher(reducedPage);
            int resultCount = 0;

            while (matcher.find() && resultCount < MAX_RESULTS) {
                try {
                    String displayName = matcher.group("displayname");
                    String magnetUrl = matcher.group("magneturl");
                    String torrentUrl = matcher.group("torrenturl");
                    String filesizeStr = matcher.group("filesize");
                    String detailsUrl = matcher.group("detailsurl");
                    String thumbnailUrl = matcher.group("thumbnailurl");
                    String timestamp = matcher.group("timestamp");
                    String seeds = matcher.group("seeds");

                    if (StringUtils.isNullOrEmpty(displayName) || StringUtils.isNullOrEmpty(magnetUrl)) {
                        LOG.debug("Nyaa: Skipping result with missing required fields");
                        continue;
                    }

                    // CRITICAL: Decode HTML entities in display name to prevent invisible results
                    // Modern websites encode special characters like &amp;, &#x27;, etc.
                    displayName = HtmlManipulator.replaceHtmlEntities(displayName);

                    // Parse metadata
                    long fileSize = parseSize(filesizeStr);
                    long creationTime = parseLong(timestamp) * 1000;  // Convert to milliseconds
                    int seedCount = parseInt(seeds, 0);
                    String hash = parseHash(magnetUrl);

                    // Build complete URL strings
                    String fullDetailsUrl = "https://" + DOMAIN + detailsUrl;
                    String fullTorrentUrl = "https://" + DOMAIN + torrentUrl;

                    // Create filename with appropriate extension
                    // Check if displayName has a real video/archive file extension
                    // (not version numbers like .0, .1, .265, .8)
                    String filename;
                    String extension = FilenameUtils.getExtension(displayName);
                    boolean isRealFileExtension = isVideoOrArchiveExtension(extension);

                    if (isRealFileExtension) {
                        filename = displayName;  // Already has real file extension
                    } else {
                        filename = displayName + ".torrent";  // Add .torrent for tab filtering
                    }

                    LOG.debug("Nyaa: Filename - extension: '" + extension +
                             "', isReal: " + isRealFileExtension + ", final: '" + filename + "'");

                    LOG.debug("Nyaa: Created result - displayName: '" + displayName + "', hash: " + hash +
                             ", seeds: " + seedCount + ", size: " + fileSize);

                    // Create complete result (no crawling needed)
                    CompositeFileSearchResult result = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(filename)
                            .size(fileSize)
                            .detailsUrl(fullDetailsUrl)
                            .source("Nyaa")  // Must match SearchEngine name for UI routing
                            .creationTime(creationTime)
                            .torrent(magnetUrl, hash, seedCount, fullTorrentUrl)
                            .preliminary(false)  // All metadata available, complete result
                            .build();

                    results.add(result);
                    resultCount++;
                } catch (Exception e) {
                    LOG.warn("Nyaa: Error parsing result: " + e.getMessage());
                }
            }

            LOG.debug("Nyaa: Parsed " + resultCount + " results from response");
        } catch (Exception e) {
            LOG.error("Nyaa: Error parsing response: " + e.getMessage(), e);
        }

        return results;
    }

    private String parseHash(String magneturl) {
        if (StringUtils.isNullOrEmpty(magneturl)) {
            return "";
        }
        if (magneturl.startsWith("magnet:?xt=urn:btih:")) {
            if (magneturl.length() >= 52) {
                return magneturl.substring(20, 52);
            }
        }
        return "";
    }

    private long parseSize(String sizeStr) {
        if (StringUtils.isNullOrEmpty(sizeStr)) {
            return -1;
        }

        try {
            String[] parts = sizeStr.trim().split("\\s+");
            if (parts.length < 2) {
                return -1;
            }

            String amount = parts[0].trim();
            String unit = parts[1].trim();

            Long unitMultiplier = UNIT_TO_BYTES.get(unit);
            if (unitMultiplier == null) {
                unitMultiplier = 1L;
            }

            // Handle fractional and integer sizes
            if (amount.contains(".")) {
                float floatAmount = Float.parseFloat(amount);
                return (long) (floatAmount * unitMultiplier);
            } else {
                int intAmount = Integer.parseInt(amount);
                return intAmount * unitMultiplier;
            }
        } catch (Exception e) {
            LOG.debug("Nyaa: Failed to parse size: " + sizeStr);
            return -1;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return System.currentTimeMillis() / 1000;
        }
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean isVideoOrArchiveExtension(String extension) {
        if (StringUtils.isNullOrEmpty(extension)) {
            return false;
        }

        // List of real video and archive file extensions
        // Exclude version numbers like "0", "1", "265", etc.
        extension = extension.toLowerCase();
        return extension.equals("mkv") ||
               extension.equals("mp4") ||
               extension.equals("avi") ||
               extension.equals("flv") ||
               extension.equals("wmv") ||
               extension.equals("mov") ||
               extension.equals("webm") ||
               extension.equals("m4v") ||
               extension.equals("3gp") ||
               extension.equals("ogv") ||
               extension.equals("ts") ||
               extension.equals("mts") ||
               extension.equals("m2ts") ||
               extension.equals("mxf") ||
               extension.equals("mkv") ||
               extension.equals("zip") ||
               extension.equals("rar") ||
               extension.equals("7z") ||
               extension.equals("tar") ||
               extension.equals("gz");
    }
}
