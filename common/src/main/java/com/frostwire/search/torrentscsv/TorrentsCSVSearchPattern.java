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

package com.frostwire.search.torrentscsv;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.DateParser;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.UrlUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * V2 pattern-based search for torrents-csv.com
 * Torrents-csv provides complete torrent metadata via JSON API - no crawling needed.
 *
 * @author gubatron
 */
public class TorrentsCSVSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(TorrentsCSVSearchPattern.class);
    private static final String DOMAIN = "torrents-csv.com";
    private static final int MAX_RESULTS = 50;

    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/service/search?q=" + encodedKeywords + "&size=" + MAX_RESULTS;
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (StringUtils.isNullOrEmpty(responseBody)) {
            LOG.warn("TorrentsCSV: Received empty response body");
            return results;
        }

        try {
            JsonElement root = JsonParser.parseString(responseBody);
            JsonArray torrents = extractTorrentsArray(root);

            if (torrents == null) {
                LOG.warn("TorrentsCSV: Failed to extract torrents array from response");
                return results;
            }

            int resultCount = 0;
            for (JsonElement element : torrents) {
                if (resultCount >= MAX_RESULTS) {
                    break;
                }

                try {
                    JsonObject torrent = element.getAsJsonObject();
                    FileSearchResult result = parseTorrentResult(torrent);
                    if (result != null) {
                        results.add(result);
                        resultCount++;
                    }
                } catch (Exception e) {
                    LOG.debug("TorrentsCSV: Failed to parse torrent JSON object: " + e.getMessage());
                }
            }

            LOG.debug("TorrentsCSV: Parsed " + resultCount + " results from response");

        } catch (Exception e) {
            LOG.error("TorrentsCSV: Error parsing response: " + e.getMessage(), e);
        }

        return results;
    }

    private JsonArray extractTorrentsArray(JsonElement root) {
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }

        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            // Try multiple possible field names for flexibility
            for (String fieldName : new String[]{"torrents", "results", "data"}) {
                if (obj.has(fieldName) && obj.get(fieldName).isJsonArray()) {
                    return obj.getAsJsonArray(fieldName);
                }
            }
        }

        return null;
    }

    private FileSearchResult parseTorrentResult(JsonObject torrent) {
        try {
            // Extract with field name fallbacks for API flexibility
            String name = getJsonString(torrent, "name", "title", "filename");
            String infoHash = getJsonString(torrent, "infohash", "info_hash", "hash");
            String magnetUrl = getJsonString(torrent, "magnet", "magnet_uri", "magnetUri");
            long size = getJsonLong(torrent, "size", "length", "bytes");
            String creationTimeStr = getJsonString(torrent, "created", "createdAt", "date", "uploaded");
            int seeds = getJsonInt(torrent, "seeds", "seeders", "seeder");

            if (StringUtils.isNullOrEmpty(name) || StringUtils.isNullOrEmpty(infoHash)) {
                LOG.debug("TorrentsCSV: Skipping result with missing name or hash");
                return null;
            }

            // Validate and normalize info hash
            infoHash = validateAndNormalizeInfoHash(infoHash);
            if (StringUtils.isNullOrEmpty(infoHash)) {
                LOG.debug("TorrentsCSV: Invalid info hash format for torrent: " + name);
                return null;
            }

            // Generate magnet URL if not provided
            if (StringUtils.isNullOrEmpty(magnetUrl)) {
                magnetUrl = UrlUtils.buildMagnetUrl(infoHash, name,
                    UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
            }

            // Parse creation time
            long creationTime = parseCreationTime(creationTimeStr);

            // Build V2 result
            return CompositeFileSearchResult.builder()
                    .displayName(name)
                    .filename(name + ".torrent")
                    .size(size)
                    .detailsUrl(null)  // No detail pages for TorrentsCSV
                    .source("TorrentsCSV")  // Must match SearchEngine name for SearchMediator matching
                    .creationTime(creationTime)
                    .torrent(magnetUrl, infoHash, seeds, magnetUrl)
                    .preliminary(false)  // Complete data from search page
                    .build();

        } catch (Exception e) {
            LOG.warn("TorrentsCSV: Error parsing torrent: " + e.getMessage());
            return null;
        }
    }

    private String getJsonString(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                try {
                    return obj.get(key).getAsString();
                } catch (Exception e) {
                    LOG.debug("TorrentsCSV: Failed to extract string from key: " + key);
                }
            }
        }
        return "";
    }

    private long getJsonLong(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                try {
                    return obj.get(key).getAsLong();
                } catch (Exception e) {
                    try {
                        return Long.parseLong(obj.get(key).getAsString());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return 0;
    }

    private int getJsonInt(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                try {
                    return obj.get(key).getAsInt();
                } catch (Exception e) {
                    try {
                        return Integer.parseInt(obj.get(key).getAsString());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return 0;
    }

    private String validateAndNormalizeInfoHash(String infoHash) {
        if (StringUtils.isNullOrEmpty(infoHash)) {
            return null;
        }

        infoHash = infoHash.trim().toLowerCase();

        // Valid hex hash (40 chars)
        if (infoHash.length() == 40 && infoHash.matches("^[a-f0-9]{40}$")) {
            LOG.debug("TorrentsCSV: Valid hex hash: " + infoHash);
            return infoHash;
        }

        // Valid base32 hash (32 chars)
        String upperHash = infoHash.toUpperCase();
        if (upperHash.length() == 32 && upperHash.matches("^[A-Z2-7]{32}$")) {
            LOG.debug("TorrentsCSV: Valid base32 hash: " + upperHash);
            return upperHash;
        }

        // Try cleaning up invalid characters
        String cleaned = infoHash.replaceAll("[^a-f0-9]", "");
        if (cleaned.length() == 40 && cleaned.matches("^[a-f0-9]{40}$")) {
            LOG.debug("TorrentsCSV: Cleaned hex hash: " + cleaned);
            return cleaned;
        }

        LOG.debug("TorrentsCSV: Invalid hash format: " + infoHash + " (length: " + infoHash.length() + ")");
        return null;
    }

    private long parseCreationTime(String dateString) {
        try {
            return DateParser.parseTorrentDate(dateString);
        } catch (Exception e) {
            LOG.debug("TorrentsCSV: Failed to parse date: " + dateString);
            return System.currentTimeMillis();
        }
    }
}
