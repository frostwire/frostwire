/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.search.pixabay;

import com.frostwire.search.CrawlPagedWebSearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.util.JsonUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PixabaySearchPerformer extends CrawlPagedWebSearchPerformer<PixabaySearchResult> {

    static final String API_KEY = "1489683-69e83a64e7ef86185e67a5b6f";

    public PixabaySearchPerformer(long token, String keywords, int timeout) {
        super("pixabay.com", token, keywords, timeout, 1, 2);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        // start with image search, but inject the video later
        return String.format(Locale.US,
                "https://pixabay.com/api/?key=%s&q=%s&image_type=photo",
                API_KEY, encodedKeywords);
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        return searchPage(page, true);
    }

    @Override
    protected String getCrawlUrl(PixabaySearchResult sr) {
        return sr.getDetailsUrl();
    }

    @Override
    protected List<? extends SearchResult> crawlResult(PixabaySearchResult sr, byte[] data) throws Exception {
        String json = new String(data, "UTF-8");
        return searchPage(json, false);
    }

    private List<? extends SearchResult> searchPage(String page, boolean firstPass) {
        List<SearchResult> result = new LinkedList<>();

        PixabayResponse response = JsonUtils.toObject(page, PixabayResponse.class);

        if (firstPass) {
            // inject the video search
            result.add(new PixabaySearchResult(String.format(Locale.US,
                    "https://pixabay.com/api/videos/?key=%s&q=%s&video_type=film",
                    API_KEY, getEncodedKeywords())));
        }

        for (PixabayItem item : response.hits) {
            if (!isStopped()) {
                if (item.type.equals("photo")) {
                    PixabayImageSearchResult sr = new PixabayImageSearchResult(item);
                    result.add(sr);
                } else if (item.type.equals("film")) {
                    // check if it has the video
                    if (item.videos != null && item.videos.tiny != null) {
                        PixabayVideoSearchResult sr = new PixabayVideoSearchResult(item);
                        result.add(sr);
                    }
                }
            }
        }

        return result;
    }
}
