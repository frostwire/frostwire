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

package com.frostwire.search.glotorrents;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * V2 pattern-based search for GloTorrents torrent search.
 * GloTorrents returns complete torrent metadata on search page, no crawling needed.
 *
 * @author gubatron
 */
public class GloTorrentsSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(GloTorrentsSearchPattern.class);
    private static final String DOMAIN = "gtso.cc";
    private static Pattern searchPattern;

    public GloTorrentsSearchPattern() {
        if (searchPattern == null) {
            try {
                searchPattern = Pattern.compile("(?is)" +
                        "<td class='ttable_col2' nowrap='nowrap'>.*?<a title=\"(?<filename>.*?)\" href=\"(?<detailsURL>.*?)\"><b>.*?" +
                        "'nofollow' href=\"(?<magnet>.*?)\">.*?\"Magnet Download\".*?" +
                        "<td class='ttable_col1' align='center'>(?<filesize>\\d+\\.\\d+)\\p{Z}(?<unit>[KMGTP]B)</td>(.|\\n)*?" +
                        "<font color='green'><b>(?<seeds>.*?)</b></font>");
            } catch (PatternSyntaxException e) {
                LOG.error("Error compiling search results pattern", e);
            }
        }
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/search_results.php?search=" + encodedKeywords + "&cat=0&incldead=0&lang=0&sort=seeders&order=desc";
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        try {
            // Extract main results area
            final String HTML_PREFIX_MARKER = "class=\"ttable_headinner\"";
            int htmlPrefixIndex = responseBody.indexOf(HTML_PREFIX_MARKER);
            if (htmlPrefixIndex == -1) {
                return results; // No results found
            }
            htmlPrefixIndex += HTML_PREFIX_MARKER.length();

            final String HTML_SUFFIX_MARKER = "<div class=\"pagination\">";
            int htmlSuffixIndex = responseBody.indexOf(HTML_SUFFIX_MARKER);
            String reducedHtml = responseBody.substring(htmlPrefixIndex,
                    htmlSuffixIndex > 0 ? htmlSuffixIndex : responseBody.length());

            Matcher matcher = searchPattern.matcher(reducedHtml);
            int resultCount = 0;

            while (matcher.find()) {
                try {
                    String filename = matcher.group("filename");
                    String magnetURL = matcher.group("magnet");
                    String fileSizeMagnitude = matcher.group("filesize");
                    String fileSizeUnit = matcher.group("unit");
                    String seedsStr = matcher.group("seeds");
                    String detailsURL = "https://" + DOMAIN + matcher.group("detailsURL");

                    if (StringUtils.isNullOrEmpty(filename) || StringUtils.isNullOrEmpty(magnetURL)) {
                        continue;
                    }

                    // Parse size
                    long size = parseSize(fileSizeMagnitude + " " + fileSizeUnit);

                    // Parse seeds
                    int seeds = 0;
                    try {
                        seeds = Integer.parseInt(seedsStr.replace(",", ""));
                    } catch (Exception e) {
                        seeds = 0;
                    }

                    // Create complete result - GloTorrents provides everything on search page
                    CompositeFileSearchResult result = CompositeFileSearchResult.builder()
                            .displayName(filename)
                            .filename(filename + ".torrent")
                            .size(size)
                            .detailsUrl(detailsURL)
                            .source("glotorrents")
                            .creationTime(System.currentTimeMillis())
                            .torrent(magnetURL, extractInfoHash(magnetURL), seeds, magnetURL + UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS)
                            .preliminary(false)  // Complete result from search page
                            .build();

                    results.add(result);
                    resultCount++;
                    if (resultCount >= 10) {
                        break;  // Limit to 10 results like original
                    }
                } catch (Exception e) {
                    LOG.warn("Error parsing GloTorrents result: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Error parsing GloTorrents response: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Extract info hash from magnet URL.
     * Format: magnet:?xt=urn:btih:XXXXXXXXXXXX...
     */
    private String extractInfoHash(String magnetURL) {
        try {
            if (magnetURL != null && magnetURL.startsWith("magnet:?xt=urn:btih:")) {
                int magnetStart = "magnet:?xt=urn:btih:".length();
                int endIndex = magnetStart + 40;
                if (endIndex <= magnetURL.length()) {
                    return magnetURL.substring(magnetStart, endIndex);
                }
            }
        } catch (Exception e) {
            LOG.warn("Error extracting info hash: " + e.getMessage());
        }
        return "";
    }

    /**
     * Parse size string like "1.23 GB", "456 MB" into bytes.
     */
    private long parseSize(String sizeStr) {
        if (StringUtils.isNullOrEmpty(sizeStr)) {
            return -1;
        }

        try {
            sizeStr = sizeStr.trim().toUpperCase();
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
                case "PB":
                    return (long) (value * 1024 * 1024 * 1024 * 1024 * 1024);
                default:
                    return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }
}
