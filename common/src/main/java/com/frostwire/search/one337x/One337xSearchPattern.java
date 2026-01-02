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

package com.frostwire.search.one337x;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.search.TorrentMetadata;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * V2 migration of One337xSearchPerformer.
 * Searches 1337X torrent site using regex patterns to parse HTML.
 *
 * Note: This is a simplified non-crawler version. The full crawler implementation
 * would require async HTTP fetches for each result's detail page, which is beyond
 * the scope of this pattern-based search.
 *
 * @author gubatron
 */
public class One337xSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(One337xSearchPattern.class);
    private static final String DOMAIN = "www.1377x.to";
    private static Pattern searchResultsPattern;
    private static Pattern detailsPattern;

    public One337xSearchPattern() {
        if (searchResultsPattern == null) {
            try {
                searchResultsPattern = Pattern.compile("(?is)<a href=\"/torrent/(?<itemId>[0-9]*)/(?<htmlFileName>.*?)\">(?<displayName>.*?)</a>");
            } catch (PatternSyntaxException e) {
                LOG.error("Error compiling search results pattern", e);
            }
        }
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
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/search/" + encodedKeywords + "/1/";
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty() || searchResultsPattern == null) {
            return results;
        }

        try {
            Matcher matcher = searchResultsPattern.matcher(responseBody);
            int resultCount = 0;
            int maxResults = 20;

            while (matcher.find() && resultCount < maxResults) {
                try {
                    String itemId = matcher.group("itemId");
                    String htmlFileName = matcher.group("htmlFileName");
                    String displayName = matcher.group("displayName");

                    if (StringUtils.isNullOrEmpty(displayName)) {
                        continue;
                    }

                    // Clean up display name
                    displayName = displayName.replaceAll("<.*?>", "").trim();
                    if (displayName.length() > 150) {
                        displayName = displayName.substring(0, 150);
                    }

                    // Build details URL
                    String detailsUrl = "https://" + DOMAIN + "/torrent/" + itemId + "/" + htmlFileName;

                    // Create preliminary torrent result for crawling strategy to resolve.
                    // 1337X search page only has title and link - details page has magnet link,
                    // size, seeders, etc. The search performer's crawling strategy will fetch
                    // detail pages and return complete results to the UI.
                    CompositeFileSearchResult searchResult = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(displayName + ".torrent")
                            .size(-1)  // Size not available until detail page is crawled
                            .detailsUrl(detailsUrl)
                            .source("1337x")
                            .creationTime(System.currentTimeMillis())
                            .preliminary(false)  // Crawling happens inside performer, not in UI
                            .crawlable()  // Mark as crawlable so performer knows to crawl it
                            .build();

                    results.add(searchResult);
                    resultCount++;
                } catch (Exception e) {
                    LOG.warn("Error parsing 1337X result: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Error parsing 1337X response: " + e.getMessage(), e);
        }

        return results;
    }
}
