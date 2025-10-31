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

package com.frostwire.search.soundcloud;

import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudItem;
import com.frostwire.search.soundcloud.SoundcloudResponse;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * V2 pattern-based search for Soundcloud audio streaming service.
 * Searches for tracks via Soundcloud's JSON API and returns HTTP download URLs.
 *
 * @author gubatron
 */
public class SoundcloudSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(SoundcloudSearchPattern.class);
    private static final String DOMAIN = "api-v2.soundcloud.com";

    private final String clientId;
    private final String appVersion;

    public SoundcloudSearchPattern(String clientId, String appVersion) {
        this.clientId = clientId != null ? clientId : "rUGz4MgnGsIwaLTaWXvGkjJMk4pViiPA";
        this.appVersion = appVersion != null ? appVersion : "1713906596";
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/search/tracks?q=" + encodedKeywords + "&limit=50&offset=0&client_id=" + clientId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FileSearchResult> parseResults(String responseBody) {
        List<SearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            LOG.warn("Soundcloud: Received empty response body");
            return (List<FileSearchResult>) (List<?>) results;
        }

        try {
            // Parse JSON response
            SoundcloudResponse response = JsonUtils.toObject(responseBody, SoundcloudResponse.class);

            if (response == null || response.collection == null || response.collection.isEmpty()) {
                LOG.debug("Soundcloud: No tracks in response");
                return (List<FileSearchResult>) (List<?>) results;
            }

            // Parse each track
            for (SoundcloudItem item : response.collection) {
                if (!isValidSearchResult(item)) {
                    continue;
                }

                try {
                    // Create legacy SoundcloudSearchResult for compatibility with download/UI layer
                    SoundcloudSearchResult result = new SoundcloudSearchResult(item, clientId, appVersion);
                    results.add(result);

                    LOG.debug("Soundcloud: Created result - displayName: '" + result.getDisplayName() +
                             "', hash: " + result.getHash() + ", size: " + result.getSize());
                } catch (Exception e) {
                    LOG.warn("Soundcloud: Error parsing track: " + e.getMessage());
                }
            }

            LOG.debug("Soundcloud: Parsed " + results.size() + " results from response");
        } catch (Exception e) {
            LOG.error("Soundcloud: Error parsing response: " + e.getMessage(), e);
        }

        return (List<FileSearchResult>) (List<?>) results;
    }

    private boolean isValidSearchResult(SoundcloudItem item) {
        if (item == null) {
            return false;
        }
        // Only include downloadable tracks that have progressive format (streamable)
        boolean hasDownload = item.downloadable && hasProgressiveFormat(item);
        boolean hasUrl = !StringUtils.isNullOrEmpty(item.permalink_url);
        return hasDownload && hasUrl;
    }

    private boolean hasProgressiveFormat(SoundcloudItem item) {
        if (item == null || item.media == null || item.media.transcodings == null) {
            return false;
        }
        return item.media.transcodings.length > 0;
    }

}
