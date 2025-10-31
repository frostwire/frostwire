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

package com.frostwire.search.idope;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.search.TorrentMetadata;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * V2 migration of IdopeSearchPerformer.
 * Uses idope's JSON API to search for torrents.
 *
 * @author gubatron
 */
public class IdopeSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(IdopeSearchPattern.class);
    private static final String DOMAIN = "idope.hair";

    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/api.php?url=/q.php?cat=0&q=" + encodedKeywords;
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        try {
            IdopeResult[] apiResults = JsonUtils.toObject(responseBody, IdopeResult[].class);
            if (apiResults == null) {
                return results;
            }

            for (IdopeResult result : apiResults) {
                try {
                    String magnetUri = "magnet:?xt=urn:btih:" + result.info_hash;
                    CompositeFileSearchResult searchResult = CompositeFileSearchResult.builder()
                            .displayName(result.name)
                            .filename(result.name + ".torrent")
                            .size(result.size)
                            .detailsUrl("https://" + DOMAIN + "/torrent/" + result.id + "-" + result.info_hash)
                            .source("idope")
                            .creationTime(result.added * 1000)  // Convert seconds to milliseconds
                            .torrent(new TorrentMetadata(
                                    magnetUri,
                                    result.info_hash,
                                    result.seeders,
                                    "https://" + DOMAIN
                            ))
                            .build();

                    results.add(searchResult);
                } catch (Exception e) {
                    LOG.warn("Error parsing idope result: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Error parsing idope response: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Internal class representing idope API response format.
     */
    private static class IdopeResult {
        public int id;
        public String name;
        public String info_hash;
        public int seeders;
        public long size;
        public long added;  // seconds since epoch
    }
}
