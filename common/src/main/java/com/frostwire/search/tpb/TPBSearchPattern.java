/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.search.tpb;
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
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * V2 pattern-based search for The Pirate Bay (TPB).
 * Returns complete torrent metadata on search page.
 * Supports both new and old TPB HTML formats with automatic fallback.
 *
 * CRITICAL NUANCE: Source name is "TPB" (UPPERCASE) - must match SearchEngine registration exactly
 *
 * @author gubatron
 */
public class TPBSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(TPBSearchPattern.class);
    private static final int MAX_RESULTS = 20;

    // Size unit mapping: TPB uses KiB, MiB, GiB, TiB, PiB (binary units)
    private static final Map<String, String> UNIT_MAPPER;
    static {
        UNIT_MAPPER = new HashMap<>();
        UNIT_MAPPER.put("B", "B");
        UNIT_MAPPER.put("KiB", "KB");
        UNIT_MAPPER.put("MiB", "MB");
        UNIT_MAPPER.put("GiB", "GB");
        UNIT_MAPPER.put("TiB", "TB");
        UNIT_MAPPER.put("PiB", "PB");
    }

    private static Pattern newTPBPattern;
    private static Pattern oldTPBPattern;
    private static java.util.regex.Pattern commonDatePattern;
    private static java.util.regex.Pattern olderDatePattern;
    private static java.util.regex.Pattern dateTimePattern;

    private String domain;

    public TPBSearchPattern() {
        this(null);
    }

    public TPBSearchPattern(String domain) {
        this.domain = domain;
        if (this.domain == null) {
            this.domain = "pirate-bay.info";  // Fallback mirror (verified working)
        }
        if (newTPBPattern == null) {
            try {
                // NEW TPB PATTERN: Newer HTML format with upload date as separate column
                // Captures: detailsUrl, filename, creationTime, magnet, size, seeds, leeches
                newTPBPattern = Pattern.compile(
                    "(?is)<td class=\\\"vertTh\\\">.*?<a href=\\\"[^\\\"]*?\\\" title=\\\"More from this category\\\">(.*?)</a>.*?</td>.*?" +
                    "<a href=\\\"(?<detailsUrl>[^\\\"]*?)\\\" title=\\\"Details for (?<filename>[^\\\"]*?)\\\">.*?" +
                    "<td>(?<creationTime>.*?)</td>.*?" +
                    "<td><nobr><a href=\\\"magnet(?<magnet>.*?)\\\" title=\\\"Download this torrent using magnet\\\">.*?" +
                    "<td align=\\\"right\\\">(?<size>.*?)</td>.*?" +
                    "<td align=\\\"right\\\">(?<seeds>.*?)</td>.*?" +
                    "<td align=\\\"right\\\">(?<leeches>.*?)</td>.*?" +
                    "</tr>"
                );

                // OLD TPB PATTERN: Older HTML format with size/date in description area
                // Captures: detailsUrl, filename, magnet, creationTime, size, seeds
                oldTPBPattern = Pattern.compile(
                    "(?is)<td class=\\\"vertTh\\\">.*?<a href=\\\"[^\\\"]*?\\\" title=\\\"More from this category\\\">(.*?)</a>.*?</td>.*?" +
                    "<a href=\\\"(?<detailsUrl>[^\\\"]*?)\\\" class=\\\"detLink\\\" title=\\\"Details for (?<filename>[^\\\"]*?)\\\">.*?" +
                    "</a>.*?</div>.*?" +
                    "<a href=\\\"magnet(?<magnet>.*?)\\\" title=\\\"Download this torrent using magnet\\\">.*?" +
                    "Uploaded (?<creationTime>[^,]*?), Size (?<size>.*?), ULed by.*?" +
                    "<td align=\\\"right\\\">(?<seeds>.*?)</td>.*?" +
                    "</tr>"
                );

                // Date parsing patterns
                commonDatePattern = java.util.regex.Pattern.compile("([\\d]{2})-([\\d]{2})");
                olderDatePattern = java.util.regex.Pattern.compile("([\\d]{2})-([\\d]{2})&nbsp;([\\d]{4})");
                dateTimePattern = java.util.regex.Pattern.compile("([\\d]{2})-([\\d]{2})&nbsp;(\\d\\d:\\d\\d)");
            } catch (PatternSyntaxException e) {
                LOG.error("Error compiling TPB search patterns", e);
            }
        }
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        // Use the domain passed in constructor (from SearchEngine's mirror detection)
        // Falls back to default mirror if none provided
        return "https://" + domain + "/search/" + encodedKeywords + "/0/7/0";
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        // CRITICAL: Reject Cloudflare pages - TPB may be behind CF protection
        if (responseBody.contains("Cloudflare")) {
            LOG.warn("TPB: Got Cloudflare page, likely blocked");
            return results;
        }

        try {
            int resultCount = 0;
            Matcher matcher = null;

            // Try NEW pattern first (modern TPB HTML)
            if (newTPBPattern != null) {
                matcher = newTPBPattern.matcher(responseBody);
                if (matcher.find()) {
                    LOG.debug("TPB: Using NEW HTML format pattern");
                    matcher.reset();
                } else {
                    matcher = null;
                }
            }

            // Fall back to OLD pattern if new pattern didn't match
            if (matcher == null && oldTPBPattern != null) {
                matcher = oldTPBPattern.matcher(responseBody);
                LOG.debug("TPB: Using OLD HTML format pattern (fallback)");
            }

            if (matcher == null) {
                LOG.warn("TPB: No pattern matched the HTML response");
                return results;
            }

            while (matcher.find() && resultCount < MAX_RESULTS) {
                try {
                    String detailsUrl = matcher.group("detailsUrl");
                    String filename = matcher.group("filename");
                    String magnetRaw = matcher.group("magnet");
                    String creationTimeStr = matcher.group("creationTime");
                    String sizeStr = matcher.group("size");
                    String seedsStr = matcher.group("seeds");

                    if (StringUtils.isNullOrEmpty(filename) || StringUtils.isNullOrEmpty(magnetRaw)) {
                        continue;
                    }

                    // NUANCE: Decode HTML entities TWICE (legacy behavior preserved)
                    filename = HtmlManipulator.replaceHtmlEntities(filename);
                    filename = HtmlManipulator.replaceHtmlEntities(filename);

                    // Build filename: replace unsafe chars and add .torrent extension
                    filename = buildFilename(filename);
                    String displayName = FilenameUtils.getBaseName(filename);

                    // Construct magnet link from captured part
                    String magnetLink = "magnet" + magnetRaw;

                    // Extract info hash: substring(20, 60) for 40-char SHA1, then lowercase
                    String infoHash = extractInfoHash(magnetLink);
                    if (StringUtils.isNullOrEmpty(infoHash)) {
                        LOG.warn("TPB: Could not extract info hash from magnet link");
                        continue;
                    }

                    // Parse size (handles B, KiB, MiB, GiB, TiB, PiB with &nbsp; separator)
                    long size = parseSize(sizeStr);

                    // Parse seeds count
                    int seeds = parseSeeds(seedsStr);

                    // Parse creation time (handles "Today", "Y-day", and date formats)
                    long creationTime = parseCreationTime(creationTimeStr);

                    // Create complete result - TPB provides all metadata on search page
                    CompositeFileSearchResult result = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(filename)
                            .size(size)
                            .detailsUrl(detailsUrl)
                            .source("TPB")  // CRITICAL: Uppercase to match SearchEngine registration
                            .creationTime(creationTime)
                            .torrent(magnetLink, infoHash, seeds, magnetLink)
                            .preliminary(false)  // Complete result from search page
                            .build();

                    LOG.debug("TPB: Created result - displayName: '" + displayName + "', hash: " + infoHash +
                             ", seeds: " + seeds + ", size: " + size);
                    results.add(result);
                    resultCount++;
                } catch (Exception e) {
                    LOG.warn("Error parsing TPB result: " + e.getMessage(), e);
                }
            }
            LOG.debug("TPB: Finished parsing - found " + resultCount + " results, returning " + results.size());
        } catch (Exception e) {
            LOG.error("Error parsing TPB response: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Extract info hash (40 char SHA1) from magnet link.
     * Magnet format: magnet:?xt=urn:btih:XXXXXXXX... (hash starts at position 20)
     */
    private String extractInfoHash(String magnetLink) {
        try {
            // NUANCE: Exactly as in legacy - substring(20, 60) for 40-char hash, then lowercase
            if (magnetLink.length() >= 60) {
                return magnetLink.substring(20, 60).toLowerCase();
            }
        } catch (Exception e) {
            LOG.warn("Error extracting hash from magnet link: " + e.getMessage());
        }
        return null;
    }

    /**
     * Build filename: replace unsafe characters and add .torrent extension.
     * NUANCE: Exact character set from legacy: [\\/:*?"<>|[]]+
     */
    private String buildFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|\\[\\]]+", "_") + ".torrent";
    }

    /**
     * Parse size with unit mapping.
     * NUANCE: Split by "&nbsp;" (not space) - TPB uses non-breaking space
     * Units: B, KiB, MiB, GiB, TiB, PiB (binary units)
     */
    private long parseSize(String sizeStr) {
        if (StringUtils.isNullOrEmpty(sizeStr)) {
            return -1;
        }

        try {
            String[] parts = sizeStr.split("&nbsp;");
            if (parts.length < 2) {
                return -1;
            }

            String amount = parts[0].trim();
            String unitStr = parts[1].trim();
            String unit = UNIT_MAPPER.get(unitStr);

            if (unit == null) {
                LOG.debug("TPB: Unknown size unit: " + unitStr);
                return -1;
            }

            double value = Double.parseDouble(amount);
            return convertToBytes(value, unit);
        } catch (Exception e) {
            LOG.debug("TPB: Failed to parse size: " + sizeStr, e);
            return -1;
        }
    }

    /**
     * Convert size value from unit to bytes.
     */
    private long convertToBytes(double value, String unit) {
        switch (unit) {
            case "B":
                return (long) value;
            case "KB":
                return (long) (value * 1024);
            case "MB":
                return (long) (value * 1024 * 1024);
            case "GB":
                return (long) (value * 1024 * 1024 * 1024);
            case "TB":
                return (long) (value * 1024 * 1024 * 1024 * 1024);
            case "PB":
                return (long) (value * 1024 * 1024 * 1024 * 1024 * 1024);
            default:
                return -1;
        }
    }

    /**
     * Parse seeds count.
     */
    private int parseSeeds(String seedsStr) {
        try {
            return Integer.parseInt(seedsStr.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Parse creation time with support for various date formats.
     * Handles: "Today", "Y-day", MM-DD, MM-DD&nbsp;YYYY, MM-DD&nbsp;HH:MM
     *
     * NUANCE: Legacy has Calendar.set(year, month, date) bug (month not 0-indexed)
     * We fix this: Calendar uses 0-indexed months, so we subtract 1
     */
    private long parseCreationTime(String timeStr) {
        if (StringUtils.isNullOrEmpty(timeStr)) {
            return System.currentTimeMillis();
        }

        try {
            // Handle recent uploads
            if (timeStr.contains("Today") || timeStr.contains("<b>")) {
                return System.currentTimeMillis();
            } else if (timeStr.contains("Y-day")) {
                return System.currentTimeMillis() - (24 * 60 * 60 * 1000);
            }

            // Try to parse date patterns
            java.util.regex.Matcher olderMatcher = olderDatePattern.matcher(timeStr);
            java.util.regex.Matcher commonMatcher = commonDatePattern.matcher(timeStr);
            java.util.regex.Matcher timeMatcher = dateTimePattern.matcher(timeStr);

            java.util.regex.Matcher activeMatcher = olderMatcher.matches() ? olderMatcher : commonMatcher;
            if (!activeMatcher.matches() && timeMatcher.matches()) {
                activeMatcher = timeMatcher;
            }

            if (!activeMatcher.matches()) {
                LOG.debug("TPB: Could not parse date: " + timeStr);
                return System.currentTimeMillis();
            }

            int month = Integer.parseInt(activeMatcher.group(1));
            int date = Integer.parseInt(activeMatcher.group(2));
            int year;

            if (olderMatcher.matches() && olderMatcher.groupCount() == 3) {
                year = Integer.parseInt(activeMatcher.group(3));
            } else {
                year = Calendar.getInstance().get(Calendar.YEAR);
            }

            // NUANCE FIX: Calendar months are 0-indexed (Jan=0, Dec=11)
            // Legacy had a bug here, we fix it: subtract 1 from month
            Calendar cal = Calendar.getInstance();
            cal.clear();
            cal.set(year, month - 1, date);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            LOG.debug("TPB: Error parsing date: " + timeStr, e);
            return System.currentTimeMillis();
        }
    }
}
