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

package com.frostwire.search.magnetdl;
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
 * V2 pattern-based search for MagnetDL torrent search.
 * MagnetDL returns complete torrent metadata via JSON API.
 * No regex needed - just JSON deserialization.
 *
 * @author gubatron
 */
public class MagnetDLSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(MagnetDLSearchPattern.class);
    private static final String DOMAIN = "magnetdl.homes";

    /**
     * MagnetDL JSON API result structure
     * Matches exactly what the API returns
     */
    private static class Result {
        public int id;           // Used for the information page URL
        public String name;      // The name of the torrent
        public String info_hash; // Hash of the torrent (used for magnet link)
        public int seeders;      // How many seeders are currently seeding
        public long size;        // The torrent's size (in bytes)
        public long added;       // When the torrent was added (unix timestamp)
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        // IMPORTANT: MagnetDL's API is quirky - it expects:
        // 1. Original (non-URL-encoded) keywords with spaces converted to hyphens
        // 2. The SearchPerformerFactory passes encoded keywords (%20 for space)
        // 3. We need to reverse the encoding and convert spaces to hyphens
        String decodedKeywords = encodedKeywords.replace("%20", " ");
        String transformedKeywords = decodedKeywords.replace(" ", "-");
        return "https://" + DOMAIN + "/api.php?url=/q.php?q=" + transformedKeywords;
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        try {
            LOG.debug("MagnetDL: Parsing JSON response, length: " + responseBody.length());

            // Parse JSON array of results directly (MagnetDL returns a JSON array)
            Result[] searchResults = JsonUtils.toObject(responseBody, Result[].class);

            if (searchResults == null || searchResults.length == 0) {
                LOG.debug("MagnetDL: No results in JSON response");
                return results;
            }

            LOG.info("MagnetDL: Found " + searchResults.length + " results in JSON");

            for (Result result : searchResults) {
                try {
                    if (result == null || StringUtils.isNullOrEmpty(result.name) ||
                        StringUtils.isNullOrEmpty(result.info_hash)) {
                        continue;
                    }

                    String displayName = result.name;
                    if (displayName.length() > 150) {
                        displayName = displayName.substring(0, 150);
                    }

                    // MagnetDL hardcodes specific trackers in magnet URLs
                    // These must match exactly what was in the legacy MagnetDLSearchResult
                    String magnetLink = UrlUtils.buildMagnetUrl(
                        result.info_hash,
                        result.name,
                        "&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce" +
                        "&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337" +
                        "&tr=udp%3A%2F%2Fmovies.zsw.ca%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.org%3A6969%2Fannounce" +
                        "&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce" +
                        "&tr=udp%3A%2F%2Ftracker.0x.tf%3A6969%2Fannounce"
                    );

                    // Create details URL using the API ID
                    String detailsUrl = "https://" + DOMAIN + "/tortpb?id=" + result.id;

                    // Create complete result - MagnetDL provides everything via API
                    CompositeFileSearchResult fsr = CompositeFileSearchResult.builder()
                            .displayName(displayName)
                            .filename(displayName + ".torrent")
                            .size(result.size)
                            .detailsUrl(detailsUrl)
                            .source("magnetdl")
                            .creationTime(result.added)
                            .torrent(magnetLink, result.info_hash, result.seeders, magnetLink)
                            .preliminary(false)  // Complete result from API
                            .build();

                    LOG.info("MagnetDL: Created result - displayName: '" + displayName +
                             "', size: " + result.size + " bytes, seeds: " + result.seeders);
                    results.add(fsr);
                } catch (Exception e) {
                    LOG.warn("Error parsing MagnetDL result: " + e.getMessage(), e);
                }
            }

            LOG.debug("MagnetDL: Finished parsing - found " + results.size() + " results");
        } catch (Exception e) {
            LOG.error("Error parsing MagnetDL response: " + e.getMessage(), e);
        }

        return results;
    }
}
