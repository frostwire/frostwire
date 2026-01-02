/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.search.soundcloud;

import com.frostwire.util.JsonUtils;

import java.util.LinkedList;

/**
 * Utility methods for Soundcloud search result parsing.
 * Extracted from legacy SoundcloudSearchPerformer for use by V2 architecture and other code.
 */
public final class SoundcloudUtils {
    private static final String DEFAULT_SOUNDCLOUD_CLIENTID = "rUGz4MgnGsIwaLTaWXvGkjJMk4pViiPA";
    private static final String DEFAULT_SOUNDCLOUD_APP_VERSION = "1713906596";

    private SoundcloudUtils() {
        // utility class
    }

    /**
     * Resolves a Soundcloud URL to the API endpoint for fetching metadata.
     */
    public static String resolveUrl(String url) {
        return "https://api-v2.soundcloud.com/resolve?url=" + url + "&client_id=" + DEFAULT_SOUNDCLOUD_CLIENTID + "&app_version=" + DEFAULT_SOUNDCLOUD_APP_VERSION;
    }

    /**
     * Parses Soundcloud JSON response and creates SoundcloudSearchResult objects.
     * Uses default client ID and app version.
     */
    public static LinkedList<SoundcloudSearchResult> fromJson(String json, boolean fromPastedUrl) {
        LinkedList<SoundcloudSearchResult> r = new LinkedList<>();
        if (json.contains("\"collection\":")) {
            SoundcloudResponse obj = JsonUtils.toObject(json, SoundcloudResponse.class);
            if (obj != null && obj.collection != null) {
                obj.collection.stream().
                        filter(SoundcloudItem::isValidSearchResult).
                        forEach(item -> r.add(new SoundcloudSearchResult(item, DEFAULT_SOUNDCLOUD_CLIENTID, DEFAULT_SOUNDCLOUD_APP_VERSION)));
            }
        } else if (json.contains("\"tracks\":")) {
            SoundcloudPlaylist obj = JsonUtils.toObject(json, SoundcloudPlaylist.class);
            if (obj != null && obj.tracks != null) {
                obj.tracks.stream().
                        filter(SoundcloudItem::isValidSearchResult).
                        forEach(item -> r.add(new SoundcloudSearchResult(item, DEFAULT_SOUNDCLOUD_CLIENTID, DEFAULT_SOUNDCLOUD_APP_VERSION)));
            }
        } else { // assume it's a single item
            SoundcloudItem item = JsonUtils.toObject(json, SoundcloudItem.class);
            if (item != null && item.isValidSearchResult(fromPastedUrl)) {
                SoundcloudSearchResult sr = new SoundcloudSearchResult(item, DEFAULT_SOUNDCLOUD_CLIENTID, DEFAULT_SOUNDCLOUD_APP_VERSION);
                r.add(sr);
            }
        }
        return r;
    }
}
