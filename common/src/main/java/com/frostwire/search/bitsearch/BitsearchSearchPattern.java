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

package com.frostwire.search.bitsearch;

import com.frostwire.bittorrent.DefaultTrackers;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * V2 pattern-based search for Bitsearch (https://bitsearch.eu).
 *
 * <p>Bitsearch is the successor of Solid Torrents and exposes a clean
 * REST/JSON API:
 * <pre>
 *   GET https://bitsearch.eu/api/v1/search?q={keywords}&limit=20
 *   GET https://bitsearch.eu/api/v1/torrent/{id}
 * </pre>
 *
 * <p>The free tier is 200 requests/day per IP without auth, 1000/day
 * with a free account API key (rate-limit info returned in
 * {@code X-RateLimit-*} headers). The search response already includes
 * infohash, title, size, seeders, leechers and a {@code verified} flag,
 * which is enough to build a complete result — we don't have to hit
 * the detail endpoint per result. The magnet link is constructed
 * client-side from the infohash and {@link DefaultTrackers}.
 *
 * <p>No crawling is needed: the search response is the entire result.
 *
 * @author gubatron
 */
public class BitsearchSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(BitsearchSearchPattern.class);
    private static final String DOMAIN = "bitsearch.eu";
    private static final int MAX_RESULTS = 20;
    private static final int MAX_DISPLAY_NAME_LENGTH = 150;

    /**
     * Subset of the Bitsearch search-result schema we consume.
     * Mirrors https://bitsearch.eu/api/v1/search — extra fields are
     * ignored by gson.
     */
    private static class Result {
        public String id;
        public String infohash;
        public String title;
        public long size;
        public int seeders;
        public int leechers;
        public boolean verified;
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/api/v1/search?q=" + encodedKeywords + "&limit=" + MAX_RESULTS;
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (StringUtils.isNullOrEmpty(responseBody)) {
            return results;
        }

        try {
            String trimmed = responseBody.trim();
            Response envelope = JsonUtils.toObject(trimmed, Response.class);
            if (envelope == null || envelope.results == null || envelope.results.length == 0) {
                LOG.debug("Bitsearch: empty or no-results response");
                return results;
            }

            LOG.info("Bitsearch: parsing " + envelope.results.length + " result(s)");

            for (Result r : envelope.results) {
                try {
                    if (r == null || StringUtils.isNullOrEmpty(r.infohash) || StringUtils.isNullOrEmpty(r.title)) {
                        continue;
                    }

                    String displayName = r.title.length() > MAX_DISPLAY_NAME_LENGTH
                            ? r.title.substring(0, MAX_DISPLAY_NAME_LENGTH)
                            : r.title;

                    String magnet = "magnet:?xt=urn:btih:" + r.infohash
                            + "&dn=" + UrlUtils.encode(displayName)
                            + DefaultTrackers.MAGNET_URL_PARAMETERS;

                    String detailsUrl = "https://" + DOMAIN + "/torrent/" + r.id;

                    CompositeFileSearchResult csr = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(displayName + ".torrent")
                            .size(r.size)
                            .detailsUrl(detailsUrl)
                            .source("Bitsearch")
                            .torrent(magnet, r.infohash, r.seeders, magnet)
                            .preliminary(false)
                            .build();

                    results.add(csr);
                } catch (Exception e) {
                    LOG.warn("Bitsearch: error parsing result: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            LOG.error("Bitsearch: error parsing response: " + e.getMessage(), e);
        }

        return results;
    }

    private static class Response {
        public boolean success;
        public String query;
        public Result[] results;
    }
}
