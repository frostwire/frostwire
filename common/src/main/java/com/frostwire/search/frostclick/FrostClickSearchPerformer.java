/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.frostwire.search.frostclick;

import com.frostwire.search.PagedWebSearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.util.Logger;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class FrostClickSearchPerformer extends PagedWebSearchPerformer {
    private static final Logger LOG = Logger.getLogger(FrostClickSearchPerformer.class);
    private static final int MAX_RESULTS = 1;
    private final Map<String, String> customHeaders;

    private boolean responseAsExpected;

    public FrostClickSearchPerformer(String domainName, long token, String keywords, int timeout, UserAgent userAgent) {
        super(domainName, token, keywords, timeout, MAX_RESULTS);
        this.customHeaders = buildCustomHeaders(userAgent);
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://api.frostclick.com/q?page=" + page + "&q=" + encodedKeywords;
    }

    @Override
    protected List<? extends SearchResult> searchPage(int page) {
        String url = getSearchUrl(page, getEncodedKeywords());
        String text = null;
        try {
            text = fetch(url, null, customHeaders);
            responseAsExpected = text.contains("errors:[]");
            //LOG.info("FrostClickSearchPerformer::searchPage() text: " + text);
        } catch (IOException e) {
            return Collections.emptyList();
        }
        if (text != null) {
            return searchPage(text);
        } else {
            LOG.warn("Page content empty for url: " + url);
            return Collections.emptyList();
        }
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        // unused for this implementation since we still don't have search responses ready.
        return Collections.emptyList();
    }

    private Map<String, String> buildCustomHeaders(UserAgent userAgent) {
        Map<String, String> map = new HashMap<>(userAgent.getHeadersMap());
        map.put("User-Agent", userAgent.toString());
        map.put("sessionId", userAgent.getUUID());
        return map;
    }

    public boolean wasResponseOk() {
        return responseAsExpected;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }
}
