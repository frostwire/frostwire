/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.search.knaben;

import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Search performer for Knaben Database (knaben.org)
 * <p>
 * This implementation uses POST requests to the Knaben API as required by their specification.
 * The API endpoint is: https://knaben.org/api/v1/search (POST only)
 *
 * @author gubatron
 */
public class KnabenSearchPerformer extends SimpleTorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(KnabenSearchPerformer.class);

    public KnabenSearchPerformer(long token, String keywords, int timeout) {
        super("knaben.org", token, keywords, timeout, 1, 0);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        // Knaben API v1 search endpoint (requires POST request)
        // Note: This domain may be unreachable or the API may not exist
        // The search performer is disabled by default in SearchEnginesSettings
        return "https://api.knaben.org/v1";
    }

    @Override
    protected String fetchSearchPage(String url) throws IOException {
        // The Knaben API requires POST requests with JSON data
        // Prepare the JSON data with correct parameters according to API documentation
        String escapedQuery = getKeywordsString().replace("\"", "\\\""); // Escape quotes in search query
        String jsonData = String.format(
            "{" +
            "\"query\":\"%s\"," +
            "\"search_type\":\"100%%\"," +
            "\"search_field\":\"title\"," +
            "\"order_by\":\"peers\"," +
            "\"order_direction\":\"desc\"," +
            "\"from\":0," +
            "\"size\":50," +
            "\"hide_unsafe\":true," +
            "\"hide_xxx\":true," +
            "\"seconds_since_last_seen\":86400" +
            "}",
            escapedQuery
        );
        
        LOG.info("Making POST request to Knaben API: " + url);
        LOG.info("POST JSON data: " + jsonData);
        
        // Use the postJson method from WebSearchPerformer
        String response = postJson(url, jsonData);
        
        if (response == null) {
            LOG.warn("POST request to Knaben API returned null response");
            return null;
        }
        
        LOG.info("Knaben API POST response length: " + response.length() + " characters");
        return response;
    }

    @Override
    protected List<? extends KnabenSearchResult> searchPage(String page) {
        if (page == null || page.isEmpty()) {
            LOG.warn("Received empty page response from Knaben API - this may indicate the domain is unreachable or the API is down");
            return Collections.emptyList();
        }

        List<KnabenSearchResult> results = new ArrayList<>();

        Response response = JsonUtils.toObject(page, Response.class);
        for (TorrentData result : response.hits) {
            results.add(
                new KnabenSearchResult(
                    result.hash,
                    result.title + ".torrent",
                    result.title,
                    result.magnetUrl,
                    result.details,
                    result.bytes,
                    result.date,
                    result.seeders
                )
            );
        }

        if (results.isEmpty()) {
            LOG.warn("KnabenSearchPerformer: No results found - API may have changed or be unavailable");
        }
        
        return results;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }

    /**
     * Class used to help directly parse JSON into an object
     */
    private static class Response {
        public TorrentData[] hits;
    }

    /**
     * Class to allow direct parsing of Knaben's "hits" response field into an object.
     */
    private static class TorrentData {
        public String title;        // Name of the file
        public String hash;         // Hash of the torrent as used in the magnet URL
        public String magnetUrl;    // The magnet URL of the torrent
        public String details;      // URL for details about the torrent
        public long bytes;          // Size of the URL in bytes
        public String date;         // Age of the torrent
        public int seeders;         // Number of people current seeding the torrent
    }
}