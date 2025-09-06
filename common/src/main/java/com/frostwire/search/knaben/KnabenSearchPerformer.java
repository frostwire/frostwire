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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Search performer for Knaben Database (knaben.org)
 * 
 * Note: The knaben.org domain and API may not be available or accessible.
 * This search performer is disabled by default and should only be enabled
 * if you have confirmed that the API is working and accessible.
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
        // Knaben API v1 search endpoint
        // Note: This domain may be unreachable or the API may not exist
        // The search performer is disabled by default in SearchEnginesSettings
        return "https://" + getDomainName() + "/api/v1/search?q=" + encodedKeywords + "&limit=50";
    }

    @Override
    protected List<? extends KnabenSearchResult> searchPage(String page) {
        if (StringUtils.isNullOrEmpty(page)) {
            LOG.warn("Received empty page response from Knaben API - this may indicate the domain is unreachable or the API is down");
            return Collections.emptyList();
        }

        // Debug logging to understand what we're receiving
        LOG.info("Knaben API response length: " + page.length() + " characters");
        String preview = page.substring(0, Math.min(100, page.length())).trim();
        LOG.info("Response starts with: " + preview);
        
        // Check if this looks like an error page or HTML instead of JSON
        if (preview.toLowerCase().startsWith("<!doctype") || 
            preview.toLowerCase().startsWith("<html") ||
            preview.toLowerCase().contains("<title>") ||
            preview.toLowerCase().contains("404 not found") ||
            preview.toLowerCase().contains("500 internal server") ||
            preview.toLowerCase().contains("service unavailable")) {
            LOG.error("Knaben API returned an HTML error page instead of JSON. The API endpoint may not exist or the service may be down.");
            LOG.error("This search performer is disabled by default. Enable it only if you know the API is working.");
            return Collections.emptyList();
        }

        try {
            return parseJsonResponse(page);
        } catch (Exception e) {
            LOG.error("Failed to parse Knaben API response: " + e.getMessage(), e);
            LOG.error("If you're seeing JSON parsing errors, the knaben.org API may not be available or may have changed.");
            return Collections.emptyList();
        }
    }

    private List<KnabenSearchResult> parseJsonResponse(String jsonPage) throws Exception {
        List<KnabenSearchResult> results = new ArrayList<>();
        
        // Debug logging to see what we're actually receiving
        System.out.println("KnabenSearchPerformer.parseJsonResponse(jsonPage=" + 
            (jsonPage != null ? jsonPage.substring(0, Math.min(200, jsonPage.length())) : "null") + "...)");
        
        // Check if the response looks like HTML instead of JSON
        if (jsonPage != null && jsonPage.trim().toLowerCase().startsWith("<!doctype") || 
            (jsonPage != null && jsonPage.trim().toLowerCase().startsWith("<html"))) {
            LOG.error("Received HTML response instead of JSON from Knaben API. This suggests the API endpoint is incorrect or the service is down.");
            LOG.error("Response preview: " + jsonPage.substring(0, Math.min(500, jsonPage.length())));
            throw new Exception("API returned HTML instead of JSON - service may be unavailable");
        }
        
        // Check for empty or invalid response
        if (StringUtils.isNullOrEmpty(jsonPage) || jsonPage.trim().isEmpty()) {
            throw new Exception("Empty response from Knaben API");
        }
        
        JsonElement root;
        try {
            root = JsonParser.parseString(jsonPage);
        } catch (Exception e) {
            LOG.error("Failed to parse JSON response from Knaben API", e);
            LOG.error("Response content: " + (jsonPage.length() > 1000 ? jsonPage.substring(0, 1000) + "..." : jsonPage));
            throw new Exception("Invalid JSON response from API: " + e.getMessage());
        }
        
        JsonArray torrents = findJsonArrayInResponse(root);
        
        if (torrents == null) {
            throw new Exception("No recognized torrents array found in response");
        }

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        
        int maxResults = 50;
        int count = 0;
        
        for (JsonElement element : torrents) {
            if (count >= maxResults || isStopped()) {
                break;
            }
            
            try {
                // Try different torrent data structures that the API might return
                KnabenSearchResult result = createSearchResultFromJson(gson, element);
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

    private KnabenSearchResult createSearchResultFromJson(Gson gson, JsonElement element) {
        try {
            TorrentData torrentData = gson.fromJson(element, TorrentData.class);
            
            if (torrentData == null || !torrentData.isValid()) {
                return null;
            }
            
            // Validate and normalize info hash
            String infoHash = validateAndNormalizeInfoHash(torrentData.getInfoHash());
            if (StringUtils.isNullOrEmpty(infoHash)) {
                LOG.warn("Invalid info hash format for torrent: " + torrentData.getName());
                return null;
            }

            // Generate magnet URL if not provided
            String magnetUrl = torrentData.getMagnetUrl();
            if (StringUtils.isNullOrEmpty(magnetUrl) && !StringUtils.isNullOrEmpty(infoHash)) {
                magnetUrl = UrlUtils.buildMagnetUrl(infoHash, torrentData.getName(), UrlUtils.USUAL_TORRENT_TRACKERS_MAGNET_URL_PARAMETERS);
            }

            // Generate details URL if not provided (fallback to base domain)
            String detailsUrl = torrentData.getDetailsUrl();
            if (StringUtils.isNullOrEmpty(detailsUrl)) {
                detailsUrl = "https://" + getDomainName() + "/torrent/" + infoHash;
            }

            return new KnabenSearchResult(
                infoHash,
                torrentData.getName() + ".torrent",
                torrentData.getName(),
                magnetUrl,
                detailsUrl,
                torrentData.getSize(),
                torrentData.getCreationTime(),
                torrentData.getSeeds()
            );
        } catch (Exception e) {
            LOG.warn("Failed to parse torrent JSON object: " + e.getMessage());
            return null;
        }
    }

    private JsonArray findJsonArrayInResponse(JsonElement root) {
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        
        if (root.isJsonObject()) {
            // Try common field names for torrent results
            String[] arrayFieldNames = {"torrents", "results", "data", "items", "response"};
            for (String fieldName : arrayFieldNames) {
                if (root.getAsJsonObject().has(fieldName) && root.getAsJsonObject().get(fieldName).isJsonArray()) {
                    return root.getAsJsonObject().getAsJsonArray(fieldName);
                }
            }
        }
        
        return null;
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
        
        return null;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }

    // Inner static classes to map JSON response structures
    
    /**
     * Flexible JSON structure for torrent data that handles multiple field naming conventions
     */
    public static class TorrentData {
        // Name field variations
        public String name;
        public String title;
        public String filename;
        
        // Info hash field variations
        public String infohash;
        public String hash;
        public String info_hash;
        
        // Magnet URL field variations
        public String magnet;
        public String magnet_uri;
        public String magnetUri;
        
        // Details URL field variations
        public String details_url;
        public String detailsUrl;
        public String url;
        
        // Size field variations
        public long size;
        public long length;
        public long bytes;
        
        // Creation time field variations
        public String created;
        public String createdAt;
        public String date;
        public String uploaded;
        public String upload_date;
        
        // Seeds field variations
        public int seeds;
        public int seeders;
        public int seeder;
        
        public String getName() {
            if (!StringUtils.isNullOrEmpty(name)) return name;
            if (!StringUtils.isNullOrEmpty(title)) return title;
            if (!StringUtils.isNullOrEmpty(filename)) return filename;
            return "";
        }
        
        public String getInfoHash() {
            if (!StringUtils.isNullOrEmpty(infohash)) return infohash;
            if (!StringUtils.isNullOrEmpty(hash)) return hash;
            if (!StringUtils.isNullOrEmpty(info_hash)) return info_hash;
            return "";
        }
        
        public String getMagnetUrl() {
            if (!StringUtils.isNullOrEmpty(magnet)) return magnet;
            if (!StringUtils.isNullOrEmpty(magnet_uri)) return magnet_uri;
            if (!StringUtils.isNullOrEmpty(magnetUri)) return magnetUri;
            return "";
        }
        
        public String getDetailsUrl() {
            if (!StringUtils.isNullOrEmpty(details_url)) return details_url;
            if (!StringUtils.isNullOrEmpty(detailsUrl)) return detailsUrl;
            if (!StringUtils.isNullOrEmpty(url)) return url;
            return "";
        }
        
        public long getSize() {
            if (size > 0) return size;
            if (length > 0) return length;
            if (bytes > 0) return bytes;
            return 0;
        }
        
        public String getCreationTime() {
            if (!StringUtils.isNullOrEmpty(created)) return created;
            if (!StringUtils.isNullOrEmpty(createdAt)) return createdAt;
            if (!StringUtils.isNullOrEmpty(date)) return date;
            if (!StringUtils.isNullOrEmpty(uploaded)) return uploaded;
            if (!StringUtils.isNullOrEmpty(upload_date)) return upload_date;
            return "";
        }
        
        public int getSeeds() {
            if (seeds > 0) return seeds;
            if (seeders > 0) return seeders;
            if (seeder > 0) return seeder;
            return 0;
        }
        
        public boolean isValid() {
            return !StringUtils.isNullOrEmpty(getName()) && !StringUtils.isNullOrEmpty(getInfoHash());
        }
    }
}