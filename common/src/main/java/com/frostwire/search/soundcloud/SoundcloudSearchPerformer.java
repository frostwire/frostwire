/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import com.frostwire.search.PagedWebSearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.util.JsonUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SoundcloudSearchPerformer extends PagedWebSearchPerformer {
    // Default fallback values (use getters to get current values from cache)
    public static final String DEFAULT_SOUNDCLOUD_CLIENTID = "rUGz4MgnGsIwaLTaWXvGkjJMk4pViiPA";
    public static final String DEFAULT_SOUNDCLOUD_APP_VERSION = "1713906596";

    // Fields to store instance-specific credentials (can be overridden via setters)
    private String instanceClientId;
    private String instanceAppVersion;

    public SoundcloudSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1);
        this.instanceClientId = DEFAULT_SOUNDCLOUD_CLIENTID;
        this.instanceAppVersion = DEFAULT_SOUNDCLOUD_APP_VERSION;
    }

    /**
     * Sets the client ID and app version for this performer instance.
     * Used on desktop to inject dynamically fetched credentials from the remote update server.
     */
    public void setCredentials(String clientId, String appVersion) {
        if (clientId != null && !clientId.trim().isEmpty()) {
            this.instanceClientId = clientId;
        }
        if (appVersion != null && !appVersion.trim().isEmpty()) {
            this.instanceAppVersion = appVersion;
        }
    }

    /**
     * Gets the client ID to use for this performer instance.
     */
    private String getClientId() {
        return this.instanceClientId != null ? this.instanceClientId : DEFAULT_SOUNDCLOUD_CLIENTID;
    }

    /**
     * Gets the app version to use for this performer instance.
     */
    private String getAppVersion() {
        return this.instanceAppVersion != null ? this.instanceAppVersion : DEFAULT_SOUNDCLOUD_APP_VERSION;
    }

    public static String resolveUrl(String url) {
        return "https://api-v2.soundcloud.com/resolve?url=" + url + "&client_id=" + DEFAULT_SOUNDCLOUD_CLIENTID + "&app_version=" + DEFAULT_SOUNDCLOUD_APP_VERSION;
    }

    public LinkedList<SoundcloudSearchResult> fromJsonWithCredentials(String json, boolean fromPastedUrl) {
        LinkedList<SoundcloudSearchResult> r = new LinkedList<>();
        if (json.contains("\"collection\":")) {
            SoundcloudResponse obj = JsonUtils.toObject(json, SoundcloudResponse.class);
            if (obj != null && obj.collection != null) {
                obj.collection.stream().
                        filter(SoundcloudItem::isValidSearchResult).
                        forEach(item -> r.add(new SoundcloudSearchResult(item, getClientId(), getAppVersion())));
            }
        } else if (json.contains("\"tracks\":")) {
            SoundcloudPlaylist obj = JsonUtils.toObject(json, SoundcloudPlaylist.class);
            if (obj != null && obj.tracks != null) {
                obj.tracks.stream().
                        filter(SoundcloudItem::isValidSearchResult).
                        forEach(item -> r.add(new SoundcloudSearchResult(item, getClientId(), getAppVersion())));
            }
        } else { // assume it's a single item
            SoundcloudItem item = JsonUtils.toObject(json, SoundcloudItem.class);
            if (item != null && item.isValidSearchResult(fromPastedUrl)) {
                SoundcloudSearchResult sr = new SoundcloudSearchResult(item, getClientId(), getAppVersion());
                r.add(sr);
            }
        }
        return r;
    }

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

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://api-v2.soundcloud.com/search/tracks?q=" + encodedKeywords + "&limit=50&offset=0&client_id=" + getClientId();
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        List<SearchResult> result = new LinkedList<>();
        SoundcloudResponse obj = JsonUtils.toObject(page, SoundcloudResponse.class);
        // can't use fromJson here due to the isStopped call
        if (obj != null && obj.collection != null) {
            obj.collection.stream().
                    filter(item -> !isStopped() && item.isValidSearchResult()).
                    forEach(item -> result.add(new SoundcloudSearchResult(item, getClientId(), getAppVersion())));
        }
        return result;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}
