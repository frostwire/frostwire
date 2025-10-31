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

package com.frostwire.search.one337x;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchListener;
import com.frostwire.search.CrawlingStrategy;
import com.frostwire.search.FileSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UserAgentGenerator;
import com.frostwire.util.http.HttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * Crawling strategy for 1337X torrent search results.
 * Fetches detail pages to extract magnet links, file sizes, seeders, and upload dates.
 *
 * The search page only provides title and link - this strategy crawls the detail page
 * to get complete metadata needed for the final search result.
 *
 * @author gubatron
 */
public class One337xCrawlingStrategy implements CrawlingStrategy {
    private static final Logger LOG = Logger.getLogger(One337xCrawlingStrategy.class);
    private static final String DEFAULT_USER_AGENT = UserAgentGenerator.getUserAgent();
    private static Pattern detailsPattern;

    private final HttpClient httpClient;
    private final int timeout;
    private final int maxCrawls;

    public One337xCrawlingStrategy() {
        this(HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH), 30000, 100);
    }

    public One337xCrawlingStrategy(HttpClient httpClient, int timeout, int maxCrawls) {
        this.httpClient = httpClient;
        this.timeout = timeout;
        this.maxCrawls = maxCrawls;

        if (detailsPattern == null) {
            try {
                detailsPattern = Pattern.compile("(?is)<div class=\"box-info-heading clearfix\">.*?" +
                        "<a class=\".*\" href=\"(?<magnet>.*?)\" onclick=\".*\">.*?" +
                        "<strong>Language</strong>.*?<span>.*?</span>.*?" +
                        "<strong>Total size</strong>.*?<span>(?<size>.*?)</span>.*?" +
                        "<strong>Date uploaded</strong>.*?<span>(?<creationDate>.*?)</span>.*?" +
                        "<strong>Seeders</strong>.*?<span class=\"seeds\">(?<seeds>[0-9]+)</span>");
            } catch (PatternSyntaxException e) {
                LOG.error("Error compiling details pattern", e);
            }
        }
    }

    @Override
    public void crawlResults(List<FileSearchResult> results, SearchListener listener, long token) {
        int crawlCount = 0;
        List<FileSearchResult> crawledResults = new ArrayList<>();

        for (FileSearchResult result : results) {
            if (crawlCount >= maxCrawls) {
                break;
            }

            try {
                String detailsUrl = result.getDetailsUrl();
                if (detailsUrl != null && !detailsUrl.isEmpty()) {
                    String detailsHtml = httpClient.get(detailsUrl, timeout, DEFAULT_USER_AGENT, null, null, null);
                    if (detailsHtml != null) {
                        FileSearchResult crawledResult = extractTorrentDetails(result, detailsHtml);
                        if (crawledResult != null) {
                            crawledResults.add(crawledResult);
                            crawlCount++;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error crawling 1337X torrent details from " + result.getDetailsUrl() + ": " + e.getMessage());
            }
        }

        // Report only crawled results to listener (preliminary results are resolved)
        if (listener != null && !crawledResults.isEmpty()) {
            listener.onResults(token, (List) crawledResults);
        }
    }

    /**
     * Extracts torrent metadata from detail page HTML and creates a complete result.
     *
     * @param preliminaryResult the preliminary result from search page
     * @param detailsHtml the HTML from the torrent detail page
     * @return a complete FileSearchResult with metadata, or null if extraction failed
     */
    private FileSearchResult extractTorrentDetails(FileSearchResult preliminaryResult, String detailsHtml) {
        if (detailsPattern == null) {
            return null;
        }

        try {
            Matcher matcher = detailsPattern.matcher(detailsHtml);
            if (matcher.find()) {
                String magnetLink = matcher.group("magnet");
                String sizeStr = matcher.group("size");
                String seedsStr = matcher.group("seeds");

                if (StringUtils.isNullOrEmpty(magnetLink)) {
                    return null;
                }

                // Parse size (format: "1.23 GB" or "456 MB" etc)
                long size = parseSize(sizeStr);

                // Parse seeders
                int seeds = 0;
                try {
                    seeds = Integer.parseInt(seedsStr);
                } catch (Throwable e) {
                    // ignore
                }

                // Create complete result with metadata from detail page
                return CompositeFileSearchResult.builder()
                        .displayName(preliminaryResult.getDisplayName())
                        .filename(preliminaryResult.getDisplayName() + ".torrent")
                        .size(size)
                        .detailsUrl(preliminaryResult.getDetailsUrl())
                        .source(preliminaryResult.getSource())
                        .creationTime(preliminaryResult.getCreationTime())
                        .torrent(magnetLink, "", seeds, magnetLink)  // magnet link as both torrent URL and hash URL
                        .preliminary(false)  // Now complete after crawling
                        .build();
            }
        } catch (Exception e) {
            LOG.warn("Error parsing 1337X torrent details: " + e.getMessage());
        }

        return null;
    }

    /**
     * Parse size string like "1.23 GB", "456 MB", "789 KB" into bytes.
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
                default:
                    return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }
}
