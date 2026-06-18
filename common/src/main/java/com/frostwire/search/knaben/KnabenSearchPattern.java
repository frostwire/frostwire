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

package com.frostwire.search.knaben;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.DateParser;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * V2 search pattern for Knaben API that uses POST with JSON body.
 * Declares HTTP method as POST_JSON and constructs the JSON request body.
 *
 * @author gubatron
 */
public class KnabenSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(KnabenSearchPattern.class);
    private static final int MAX_RESULTS = 50;

    @Override
    public String getSearchUrl(String encodedKeywords) {
        // Return the API endpoint (HTTP method is in getHttpMethod())
        return "https://api.knaben.org/v1";
    }

    @Override
    public HttpMethod getHttpMethod() {
        return HttpMethod.POST_JSON;
    }

    @Override
    public String getRequestBody(String keywords) {
        // Build JSON via Gson for proper escaping of backslashes, newlines,
        // control characters, and Unicode.
        com.google.gson.Gson gson = new com.google.gson.Gson();
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("query", keywords);
        body.put("search_type", "100%");
        body.put("search_field", "title");
        body.put("order_by", "peers");
        body.put("order_direction", "desc");
        body.put("from", 0);
        body.put("size", MAX_RESULTS);
        body.put("hide_unsafe", true);
        body.put("hide_xxx", true);
        body.put("seconds_since_last_seen", 86400);
        return gson.toJson(body);
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            LOG.warn("Knaben: Received empty response body");
            return results;
        }

        try {
            // Parse JSON response
            Response response = JsonUtils.toObject(responseBody, Response.class);

            if (response == null || response.hits == null) {
                LOG.warn("Knaben: Response is null or missing hits");
                return results;
            }

            int resultCount = 0;
            for (TorrentData torrentData : response.hits) {
                if (resultCount >= MAX_RESULTS) {
                    break;
                }

                try {
                    // Validate required fields
                    if (StringUtils.isNullOrEmpty(torrentData.title) ||
                        StringUtils.isNullOrEmpty(torrentData.hash) ||
                        StringUtils.isNullOrEmpty(torrentData.magnetUrl)) {
                        LOG.debug("Knaben: Skipping result with missing required fields");
                        continue;
                    }

                    String displayName = torrentData.title;
                    String filename = torrentData.title + ".torrent";
                    long size = torrentData.bytes > 0 ? torrentData.bytes : -1;
                    long creationTime = parseCreationTime(torrentData.date);
                    int seeds = torrentData.seeders > 0 ? torrentData.seeders : 0;

                    // Create complete result - Knaben provides all metadata in API response
                    CompositeFileSearchResult result = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(filename)
                            .size(size)
                            .detailsUrl(torrentData.details)
                            .source("Knaben")
                            .creationTime(creationTime)
                            .torrent(torrentData.magnetUrl, torrentData.hash, seeds, torrentData.magnetUrl)
                            .preliminary(false)
                            .build();

                    LOG.debug("Knaben: Created result - displayName: '" + displayName + "', hash: " + torrentData.hash +
                             ", seeds: " + seeds + ", size: " + size);
                    results.add(result);
                    resultCount++;
                } catch (Exception e) {
                    LOG.warn("Knaben: Error parsing torrent result: " + e.getMessage(), e);
                }
            }

            LOG.debug("Knaben: Finished parsing - found " + resultCount + " results, returning " + results.size());
        } catch (Exception e) {
            LOG.error("Knaben: Error parsing API response: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Parse creation time from date string using legacy DateParser.
     */
    private long parseCreationTime(String dateString) {
        if (StringUtils.isNullOrEmpty(dateString)) {
            return System.currentTimeMillis();
        }

        try {
            return DateParser.parseTorrentDate(dateString);
        } catch (Exception e) {
            LOG.debug("Knaben: Failed to parse date: " + dateString, e);
            return System.currentTimeMillis();
        }
    }

    /**
     * JSON response wrapper
     */
    private static class Response {
        public TorrentData[] hits;
    }

    /**
     * Individual torrent data from Knaben API
     */
    private static class TorrentData {
        public String title;        // Torrent name
        public String hash;         // Info hash
        public String magnetUrl;    // Magnet URL
        public String details;      // Details URL
        public long bytes;          // Size in bytes
        public String date;         // Date
        public int seeders;         // Number of seeders
    }
}
