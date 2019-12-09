/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private static final String SOUNDCLOUD_CLIENTID = Math.random() % 2 == 0 ? "UW9ajvMgVdMMW3cdeBi8lPfN6dvOVGji" : "pToAEVYicMm3OkPBnOlGCHfEBFrYx1fz";
    private static final String SOUNDCLOUD_APP_VERSION = "1575380558";

    public SoundcloudSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1);
    }

    public static String resolveUrl(String url) {
        return "https://api-v2.soundcloud.com/resolve?url=" + url + "&client_id=" + SOUNDCLOUD_CLIENTID + "&app_version=" + SOUNDCLOUD_APP_VERSION;
    }

    public static LinkedList<SoundcloudSearchResult> fromJson(String json, boolean fromPastedUrl) {
        LinkedList<SoundcloudSearchResult> r = new LinkedList<>();
        if (json.contains("\"collection\":")) {
            SoundcloudResponse obj = JsonUtils.toObject(json, SoundcloudResponse.class);
            if (obj != null && obj.collection != null) {
                for (SoundcloudItem item : obj.collection) {
                    if (item != null && item.isValidSearchResult(fromPastedUrl)) {
                        try {
                            SoundcloudSearchResult sr = new SoundcloudSearchResult(item, SOUNDCLOUD_CLIENTID, SOUNDCLOUD_APP_VERSION);
                            r.add(sr);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } else if (json.contains("\"tracks\":")) {
            SoundcloudPlaylist obj = JsonUtils.toObject(json, SoundcloudPlaylist.class);
            if (obj != null && obj.tracks != null) {
                for (SoundcloudItem item : obj.tracks) {
                    if (item != null && item.isValidSearchResult(fromPastedUrl)) {
                        try {
                            SoundcloudSearchResult sr = new SoundcloudSearchResult(item, SOUNDCLOUD_CLIENTID, SOUNDCLOUD_APP_VERSION);
                            r.add(sr);
                        } catch (Throwable ignored) {
                            ignored.printStackTrace();
                        }
                    }
                }
            }
        } else { // assume it's a single item
            SoundcloudItem item = JsonUtils.toObject(json, SoundcloudItem.class);
            if (item != null && item.isValidSearchResult(fromPastedUrl)) {
                SoundcloudSearchResult sr = new SoundcloudSearchResult(item, SOUNDCLOUD_CLIENTID, SOUNDCLOUD_APP_VERSION);
                r.add(sr);
            }
        }
        return r;
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "https://api-v2.soundcloud.com/search/tracks?q=" + encodedKeywords + "&limit=50&offset=0&client_id=" + SOUNDCLOUD_CLIENTID;
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        List<SearchResult> result = new LinkedList<>();
        SoundcloudResponse obj = JsonUtils.toObject(page, SoundcloudResponse.class);
        // can't use fromJson here due to the isStopped call
        if (obj != null && obj.collection != null) {
            for (SoundcloudItem item : obj.collection) {
                if (!isStopped() && item != null && item.isValidSearchResult()) {
                    SoundcloudSearchResult sr = new SoundcloudSearchResult(item, SOUNDCLOUD_CLIENTID, SOUNDCLOUD_APP_VERSION);
                    result.add(sr);
                }
            }
        }
        return result;
    }
}
