/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

    private static final String SOUNDCLOUD_CLIENTID = "02gUJC0hH2ct1EGOcYXQIzRFU91c72Ea";
    private static final String SOUNDCLOUD_APP_VERSION = "3833d63";

    public SoundcloudSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://api-v2.soundcloud.com/search?q=" + encodedKeywords + "&limit=50&offset=0&client_id=" + SOUNDCLOUD_CLIENTID;
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        List<SearchResult> result = new LinkedList<SearchResult>();

        SoundcloudResponse obj = JsonUtils.toObject(page, SoundcloudResponse.class);

        // can't use fromJson here due to the isStopped call
        if (obj != null && obj.collection != null) {
            for (SoundcloudItem item : obj.collection) {
                if (!isStopped() && item != null && item.downloadable) {
                    SoundcloudSearchResult sr = new SoundcloudSearchResult(item, SOUNDCLOUD_CLIENTID, SOUNDCLOUD_APP_VERSION);
                    result.add(sr);
                }
            }
        }

        return result;
    }

    public static String resolveUrl(String url) {
        return "http://api.soundcloud.com/resolve.json?url=" + url + "&client_id=" + SOUNDCLOUD_CLIENTID + "&app_version=" + SOUNDCLOUD_APP_VERSION;
    }

    public static LinkedList<SoundcloudSearchResult> fromJson(String json) {
        LinkedList<SoundcloudSearchResult> r = new LinkedList<>();
        if (json.indexOf("\"collection\":") != -1) {
            SoundcloudResponse obj = JsonUtils.toObject(json, SoundcloudResponse.class);

            if (obj != null && obj.collection != null) {
                for (SoundcloudItem item : obj.collection) {
                    if (item != null && item.downloadable) {
                        SoundcloudSearchResult sr = new SoundcloudSearchResult(item, SOUNDCLOUD_CLIENTID, SOUNDCLOUD_APP_VERSION);
                        r.add(sr);
                    }
                }
            }
        } else if (json.indexOf("\"tracks\":") != -1) {
            SoundcloudPlaylist obj = JsonUtils.toObject(json, SoundcloudPlaylist.class);

            if (obj != null && obj.tracks != null) {
                for (SoundcloudItem item : obj.tracks) {
                    if (item != null && item.downloadable) {
                        SoundcloudSearchResult sr = new SoundcloudSearchResult(item, SOUNDCLOUD_CLIENTID, SOUNDCLOUD_APP_VERSION);
                        r.add(sr);
                    }
                }
            }
        } else { // assume it's a single item
            SoundcloudItem item = JsonUtils.toObject(json, SoundcloudItem.class);
            if (item != null) {
                SoundcloudSearchResult sr = new SoundcloudSearchResult(item, SOUNDCLOUD_CLIENTID, SOUNDCLOUD_APP_VERSION);
                r.add(sr);
            }
        }

        return r;
    }
}
