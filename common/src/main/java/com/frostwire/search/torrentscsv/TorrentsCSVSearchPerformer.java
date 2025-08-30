/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2024, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.torrentscsv;

import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.StringUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Search performer for torrents-csv.com
 * @author gubatron
 */
public class TorrentsCSVSearchPerformer extends SimpleTorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(TorrentsCSVSearchPerformer.class);

    public TorrentsCSVSearchPerformer(long token, String keywords, int timeout) {
        super("torrents-csv.com", token, keywords, timeout, 1, 0);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        // Try the search API endpoint first
        return "https://" + getDomainName() + "/service/search?q=" + encodedKeywords + "&size=50";
    }

    @Override
    protected List<? extends TorrentsCSVSearchResult> searchPage(String page) {
        if (StringUtils.isNullOrEmpty(page)) {
            return Collections.emptyList();
        }

        // Try to parse as JSON first
        try {
            return parseJsonResponse(page);
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON response, trying HTML parsing: " + e.getMessage());
            try {
                return parseHtmlResponse(page);
            } catch (Exception e2) {
                LOG.error("Failed to parse both JSON and HTML responses", e2);
                return Collections.emptyList();
            }
        }
    }

    private List<TorrentsCSVSearchResult> parseJsonResponse(String jsonPage) throws Exception {
        List<TorrentsCSVSearchResult> results = new ArrayList<>();
        
        JsonElement root = JsonParser.parseString(jsonPage);
        
        JsonArray torrents;
        if (root.isJsonObject()) {
            JsonObject rootObj = root.getAsJsonObject();
            if (rootObj.has("torrents")) {
                torrents = rootObj.getAsJsonArray("torrents");
            } else if (rootObj.has("results")) {
                torrents = rootObj.getAsJsonArray("results");
            } else if (rootObj.has("data")) {
                torrents = rootObj.getAsJsonArray("data");
            } else {
                throw new Exception("No recognized torrents array found");
            }
        } else if (root.isJsonArray()) {
            torrents = root.getAsJsonArray();
        } else {
            throw new Exception("Unexpected JSON structure");
        }

        int maxResults = 50;
        int count = 0;
        
        for (JsonElement element : torrents) {
            if (count >= maxResults || isStopped()) {
                break;
            }
            
            try {
                JsonObject torrent = element.getAsJsonObject();
                TorrentsCSVSearchResult result = fromJsonObject(torrent);
                if (result != null) {
                    results.add(result);
                    count++;
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse torrent JSON object: " + e.getMessage());
            }
        }
        
        if (results.isEmpty()) {
            LOG.warn("TorrentsCSVSearchPerformer: No results found - API may have changed or be unavailable");
        }
        
        return results;
    }

    private TorrentsCSVSearchResult fromJsonObject(JsonObject torrent) {
        try {
            // Common field names for torrent APIs
            String name = getJsonString(torrent, "name", "title", "filename");
            String infoHash = getJsonString(torrent, "infohash", "info_hash", "hash");
            String magnetUrl = getJsonString(torrent, "magnet", "magnet_uri", "magnetUri");
            long size = getJsonLong(torrent, "size", "length", "bytes");
            String creationTime = getJsonString(torrent, "created", "createdAt", "date", "uploaded");
            int seeds = getJsonInt(torrent, "seeds", "seeders", "seeder");
            
            if (StringUtils.isNullOrEmpty(name) || StringUtils.isNullOrEmpty(infoHash)) {
                return null;
            }

            // Validate and normalize info hash
            infoHash = validateAndNormalizeInfoHash(infoHash);
            if (StringUtils.isNullOrEmpty(infoHash)) {
                LOG.warn("Invalid info hash format for torrent: " + name);
                return null;
            }

            // Generate magnet URL if not provided
            if (StringUtils.isNullOrEmpty(magnetUrl) && !StringUtils.isNullOrEmpty(infoHash)) {
                magnetUrl = UrlUtils.buildMagnetUrl(infoHash, name, UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
            }

            return new TorrentsCSVSearchResult(
                infoHash,
                name + ".torrent",
                name,
                magnetUrl,
                size,
                creationTime,
                seeds
            );
            
        } catch (Exception e) {
            LOG.warn("Error parsing JSON torrent object: " + e.getMessage());
            return null;
        }
    }

    private String getJsonString(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                return obj.get(key).getAsString();
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
                        // Try parsing as string in case it's a string number
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
                        // Try parsing as string in case it's a string number  
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
        
        // Remove any whitespace and convert to lowercase
        infoHash = infoHash.trim().toLowerCase();
        
        // Log the original hash for debugging
        LOG.info("Processing info hash: '" + infoHash + "' (length: " + infoHash.length() + ")");
        
        // Valid hex info hash should be exactly 40 characters and contain only hex characters
        if (infoHash.length() == 40 && infoHash.matches("^[a-f0-9]{40}$")) {
            LOG.info("Valid hex info hash: " + infoHash);
            return infoHash;
        }
        
        // Check if it's base32 encoded (32 characters, uppercase)
        String upperInfoHash = infoHash.toUpperCase();
        if (upperInfoHash.length() == 32 && upperInfoHash.matches("^[A-Z2-7]{32}$")) {
            LOG.info("Valid base32 info hash, returning as-is: " + upperInfoHash);
            // BitTorrent magnet links often use base32 encoding for info hashes
            return upperInfoHash;
        }
        
        // Try to handle other formats that might need cleanup
        // Remove any non-hex characters and try again
        String cleanedHash = infoHash.replaceAll("[^a-f0-9]", "");
        if (cleanedHash.length() == 40 && cleanedHash.matches("^[a-f0-9]{40}$")) {
            LOG.info("Cleaned and validated hex info hash: " + cleanedHash);
            return cleanedHash;
        }
        
        // Log detailed failure information
        LOG.warn("Invalid info hash format rejected:");
        LOG.warn("  Original: '" + infoHash + "'");
        LOG.warn("  Length: " + infoHash.length());
        LOG.warn("  Hex pattern (40 chars): " + infoHash.matches("^[a-f0-9]{40}$"));
        LOG.warn("  Base32 pattern (32 chars): " + upperInfoHash.matches("^[A-Z2-7]{32}$"));
        LOG.warn("  Contains only hex chars: " + infoHash.matches("^[a-f0-9]+$"));
        LOG.warn("  Contains only base32 chars: " + upperInfoHash.matches("^[A-Z2-7]+$"));
        
        return null;
    }

    private List<TorrentsCSVSearchResult> parseHtmlResponse(String htmlPage) throws Exception {
        // Fallback HTML parsing in case JSON API is not available
        List<TorrentsCSVSearchResult> results = new ArrayList<>();
        
        // This is a basic implementation - would need to be refined based on actual HTML structure
        // For now, just throw an exception to indicate JSON parsing should be used
        throw new Exception("HTML parsing not implemented - JSON API expected");
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}