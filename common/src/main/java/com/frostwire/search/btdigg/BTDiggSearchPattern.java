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

package com.frostwire.search.btdigg;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * V2 pattern-based search for BTDigg torrent search.
 * BTDigg returns complete torrent metadata on search page, no crawling needed.
 *
 * @author gubatron
 */
public class BTDiggSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(BTDiggSearchPattern.class);
    private static final String DOMAIN = "btdig.com";
    private static Pattern searchPattern;

    public BTDiggSearchPattern() {
        if (searchPattern == null) {
            try {
                searchPattern = Pattern.compile("(?is)<a style=\"color:rgb\\(0, 0, 204\\);text-decoration:underline;font-size:150%\" href=\"(?<detailUrl>.*?)\">(?<displayName>.*?)</a>.*?" +
                        "<span class=\"torrent_size\" style=\"color:#666;padding-left:10px\">(?<size>.*?)</span>.*?" +
                        "<div class=\"torrent_magnet\".*?a href=\"(?<magnet>.*?)\" title=");
            } catch (PatternSyntaxException e) {
                LOG.error("Error compiling search results pattern", e);
            }
        }
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/search?q=" + encodedKeywords + "&p=0&order=2";
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        // Check for DDOS protection (captcha)
        if (responseBody.contains("0 results found") || responseBody.indexOf("class=\"torrent_name\"") == -1) {
            return results;
        }

        try {
            int startOffset = responseBody.indexOf("class=\"torrent_name\"");
            String reducedPage = startOffset > 0 ? responseBody.substring(startOffset) : responseBody;

            Matcher matcher = searchPattern.matcher(reducedPage);
            int resultCount = 0;

            while (matcher.find()) {
                try {
                    String displayName = matcher.group("displayName");
                    String magnetLink = matcher.group("magnet");
                    String sizeStr = matcher.group("size");
                    String detailsUrl = matcher.group("detailUrl");

                    if (StringUtils.isNullOrEmpty(displayName) || StringUtils.isNullOrEmpty(magnetLink)) {
                        continue;
                    }

                    // Clean up display name
                    displayName = displayName.replaceAll("<.*?>", "").trim();
                    if (displayName.length() > 150) {
                        displayName = displayName.substring(0, 150);
                    }

                    // Parse size (e.g., "1.23 GB", "456 MB")
                    long size = parseSize(sizeStr);

                    // Create complete result - BTDigg provides everything on search page
                    CompositeFileSearchResult result = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(displayName + ".torrent")
                            .size(size)
                            .detailsUrl(detailsUrl)
                            .source("btdigg")
                            .creationTime(System.currentTimeMillis())
                            .torrent(magnetLink, "", 0, magnetLink)  // Magnet link as both URL and hash URL
                            .preliminary(false)  // Complete result from search page
                            .build();

                    results.add(result);
                    resultCount++;
                } catch (Exception e) {
                    LOG.warn("Error parsing BTDigg result: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Error parsing BTDigg response: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Parse size string like "1.23 GB", "456 MB" into bytes.
     * Note: BTDigg uses char 160 (non-breaking space) instead of regular spaces
     */
    private long parseSize(String sizeStr) {
        if (StringUtils.isNullOrEmpty(sizeStr)) {
            return -1;
        }

        try {
            // Replace non-breaking space (char 160) with regular space
            sizeStr = sizeStr.replace((char) 160, ' ').trim().toUpperCase();
            String[] parts = sizeStr.split("\\s+");
            if (parts.length != 2) {
                return -1;
            }

            double value = Double.parseDouble(parts[0]);
            String unit = parts[1];

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
                default:
                    return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }
}
