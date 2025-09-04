/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.search.knaben;

import com.frostwire.search.torrent.SimpleTorrentSearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import com.frostwire.util.StringUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Search performer for Knaben Database (knaben.org)
 * @author gubatron
 */
public class KnabenSearchPerformer extends SimpleTorrentSearchPerformer {
    private static final Logger LOG = Logger.getLogger(KnabenSearchPerformer.class);

    public KnabenSearchPerformer(long token, String keywords, int timeout) {
        super("knaben.org", token, keywords, timeout, 1, 0);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        // Knaben API v1 search endpoint
        return "https://" + getDomainName() + "/api/v1/search?q=" + encodedKeywords + "&limit=50";
    }

    @Override
    protected List<? extends KnabenSearchResult> searchPage(String page) {
        if (StringUtils.isNullOrEmpty(page)) {
            return Collections.emptyList();
        }

        try {
            return parseJsonResponse(page);
        } catch (Exception e) {
            LOG.error("Failed to parse Knaben API response", e);
            return Collections.emptyList();
        }
    }

    private List<KnabenSearchResult> parseJsonResponse(String jsonPage) throws Exception {
        List<KnabenSearchResult> results = new ArrayList<>();
        
        JsonElement root = JsonParser.parseString(jsonPage);
        
        JsonArray torrents;
        if (root.isJsonObject()) {
            JsonObject rootObj = root.getAsJsonObject();
            // Try common field names for torrent results
            if (rootObj.has("torrents")) {
                torrents = rootObj.getAsJsonArray("torrents");
            } else if (rootObj.has("results")) {
                torrents = rootObj.getAsJsonArray("results");
            } else if (rootObj.has("data")) {
                torrents = rootObj.getAsJsonArray("data");
            } else if (rootObj.has("items")) {
                torrents = rootObj.getAsJsonArray("items");
            } else {
                throw new Exception("No recognized torrents array found in response");
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
                KnabenSearchResult result = fromJsonObject(torrent);
                if (result != null) {
                    results.add(result);
                    count++;
                }
            } catch (Exception e) {
                LOG.warn("Failed to parse torrent JSON object: " + e.getMessage());
            }
        }
        
        if (results.isEmpty()) {
            LOG.warn("KnabenSearchPerformer: No results found - API may have changed or be unavailable");
        }
        
        return results;
    }

    private KnabenSearchResult fromJsonObject(JsonObject torrent) {
        try {
            // Common field names for torrent APIs - flexible mapping
            String name = getJsonString(torrent, "name", "title", "filename");
            String infoHash = getJsonString(torrent, "infohash", "info_hash", "hash");
            String magnetUrl = getJsonString(torrent, "magnet", "magnet_uri", "magnetUri");
            String detailsUrl = getJsonString(torrent, "details_url", "detailsUrl", "url");
            long size = getJsonLong(torrent, "size", "length", "bytes");
            String creationTime = getJsonString(torrent, "created", "createdAt", "date", "uploaded", "upload_date");
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

            // Generate details URL if not provided (fallback to base domain)
            if (StringUtils.isNullOrEmpty(detailsUrl)) {
                detailsUrl = "https://" + getDomainName() + "/torrent/" + infoHash;
            }

            return new KnabenSearchResult(
                infoHash,
                name + ".torrent",
                name,
                magnetUrl,
                detailsUrl,
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
        
        // Valid hex info hash should be exactly 40 characters and contain only hex characters
        if (infoHash.length() == 40 && infoHash.matches("^[a-f0-9]{40}$")) {
            return infoHash;
        }
        
        // Check if it's base32 encoded (32 characters, uppercase)
        String upperInfoHash = infoHash.toUpperCase();
        if (upperInfoHash.length() == 32 && upperInfoHash.matches("^[A-Z2-7]{32}$")) {
            // BitTorrent magnet links often use base32 encoding for info hashes
            return upperInfoHash;
        }
        
        // Try to handle other formats that might need cleanup
        // Remove any non-hex characters and try again
        String cleanedHash = infoHash.replaceAll("[^a-f0-9]", "");
        if (cleanedHash.length() == 40 && cleanedHash.matches("^[a-f0-9]{40}$")) {
            return cleanedHash;
        }
        
        LOG.warn("Invalid info hash format: " + infoHash + " (length: " + infoHash.length() + ")");
        return null;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}