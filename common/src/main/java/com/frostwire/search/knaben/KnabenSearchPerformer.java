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

import com.frostwire.search.PerformersHelper;
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
        JsonArray torrents = PerformersHelper.findJsonArrayInResponse(root);
        
        if (torrents == null) {
            throw new Exception("No recognized torrents array found in response");
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
            // Common field names for torrent APIs - flexible mapping using helper functions
            String name = PerformersHelper.getJsonString(torrent, "name", "title", "filename");
            String infoHash = PerformersHelper.getJsonString(torrent, "infohash", "info_hash", "hash");
            String magnetUrl = PerformersHelper.getJsonString(torrent, "magnet", "magnet_uri", "magnetUri");
            String detailsUrl = PerformersHelper.getJsonString(torrent, "details_url", "detailsUrl", "url");
            long size = PerformersHelper.getJsonLong(torrent, "size", "length", "bytes");
            String creationTime = PerformersHelper.getJsonString(torrent, "created", "createdAt", "date", "uploaded", "upload_date");
            int seeds = PerformersHelper.getJsonInt(torrent, "seeds", "seeders", "seeder");
            
            if (StringUtils.isNullOrEmpty(name) || StringUtils.isNullOrEmpty(infoHash)) {
                return null;
            }

            // Validate and normalize info hash using helper function
            infoHash = PerformersHelper.validateAndNormalizeInfoHash(infoHash);
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

    @Override
    public boolean isCrawler() {
        return false;
    }
}