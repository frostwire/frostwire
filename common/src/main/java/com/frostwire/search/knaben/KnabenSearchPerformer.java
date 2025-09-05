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
        // Try different possible JSON structures that the API might return
        TorrentData torrentData = null;
        
        // Try the primary structure first
        try {
            torrentData = gson.fromJson(element, TorrentData.class);
        } catch (Exception e) {
            LOG.debug("Failed to parse with primary structure: " + e.getMessage());
        }
        
        // Try alternative structure if the first one fails
        if (torrentData == null || (!torrentData.isValid())) {
            try {
                TorrentDataAlt altData = gson.fromJson(element, TorrentDataAlt.class);
                if (altData.isValid()) {
                    torrentData = altData.toTorrentData();
                }
            } catch (Exception e) {
                LOG.debug("Failed to parse with alternative structure: " + e.getMessage());
            }
        }
        
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
     * Primary JSON structure for torrent data
     */
    public static class TorrentData {
        public String name;
        public String infohash;
        public String magnet;
        public String details_url;
        public long size;
        public String created;
        public int seeds;
        
        public String getName() {
            return name;
        }
        
        public String getInfoHash() {
            return infohash;
        }
        
        public String getMagnetUrl() {
            return magnet;
        }
        
        public String getDetailsUrl() {
            return details_url;
        }
        
        public long getSize() {
            return size;
        }
        
        public String getCreationTime() {
            return created;
        }
        
        public int getSeeds() {
            return seeds;
        }
        
        public boolean isValid() {
            return !StringUtils.isNullOrEmpty(name) && !StringUtils.isNullOrEmpty(infohash);
        }
    }
    
    /**
     * Alternative JSON structure with different field names
     */
    public static class TorrentDataAlt {
        public String title;
        public String filename;
        public String hash;
        public String info_hash;
        public String magnet_uri;
        public String magnetUri;
        public String detailsUrl;
        public String url;
        public long length;
        public long bytes;
        public String createdAt;
        public String date;
        public String uploaded;
        public String upload_date;
        public int seeders;
        public int seeder;
        
        public boolean isValid() {
            String name = getName();
            String infoHash = getInfoHash();
            return !StringUtils.isNullOrEmpty(name) && !StringUtils.isNullOrEmpty(infoHash);
        }
        
        public String getName() {
            if (!StringUtils.isNullOrEmpty(title)) return title;
            if (!StringUtils.isNullOrEmpty(filename)) return filename;
            return "";
        }
        
        public String getInfoHash() {
            if (!StringUtils.isNullOrEmpty(hash)) return hash;
            if (!StringUtils.isNullOrEmpty(info_hash)) return info_hash;
            return "";
        }
        
        public String getMagnetUrl() {
            if (!StringUtils.isNullOrEmpty(magnet_uri)) return magnet_uri;
            if (!StringUtils.isNullOrEmpty(magnetUri)) return magnetUri;
            return "";
        }
        
        public String getDetailsUrl() {
            if (!StringUtils.isNullOrEmpty(detailsUrl)) return detailsUrl;
            if (!StringUtils.isNullOrEmpty(url)) return url;
            return "";
        }
        
        public long getSize() {
            if (length > 0) return length;
            if (bytes > 0) return bytes;
            return 0;
        }
        
        public String getCreationTime() {
            if (!StringUtils.isNullOrEmpty(createdAt)) return createdAt;
            if (!StringUtils.isNullOrEmpty(date)) return date;
            if (!StringUtils.isNullOrEmpty(uploaded)) return uploaded;
            if (!StringUtils.isNullOrEmpty(upload_date)) return upload_date;
            return "";
        }
        
        public int getSeeds() {
            if (seeders > 0) return seeders;
            if (seeder > 0) return seeder;
            return 0;
        }
        
        public TorrentData toTorrentData() {
            TorrentData data = new TorrentData();
            data.name = getName();
            data.infohash = getInfoHash();
            data.magnet = getMagnetUrl();
            data.details_url = getDetailsUrl();
            data.size = getSize();
            data.created = getCreationTime();
            data.seeds = getSeeds();
            return data;
        }
    }
}