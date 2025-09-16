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

package com.frostwire.search;

import com.frostwire.util.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class PagedWebSearchPerformer extends WebSearchPerformer {
    private static final Logger LOG = Logger.getLogger(PagedWebSearchPerformer.class);
    private final int pages;

    public PagedWebSearchPerformer(String domainName, long token, String keywords, int timeout, int pages) {
        super(domainName, token, keywords, timeout);
        this.pages = pages;
    }

    @Override
    public void perform() {
        for (int i = 1; !isStopped() && i <= pages; i++) {
            List<? extends SearchResult> searchResults = searchPage(i);
            if (searchResults != null && !searchResults.isEmpty()) {
                onResults(searchResults);
            }
        }
    }

    protected List<? extends SearchResult> searchPage(int page) {
        List<? extends SearchResult> result = Collections.emptyList();
        String url = null;
        try {
            url = getSearchUrl(page, getEncodedKeywords());
            String text = fetchSearchPage(url);
            if (text != null) {
                result = searchPage(text);
            }
        } catch (Throwable e) {
            if (url == null) {
                url = "n.a";
            }
            if (e instanceof SSLPeerUnverifiedException) {
                LOG.error("Make sure to add " + getDomainName() + " to Ssl.FWHostnameVerifier valid host name list");
            }
            LOG.error("Error searching page [" + url + "]: " + e.getMessage(), e);
        }
        return result;
    }

    protected String fetchSearchPage(String url) throws IOException {
        return fetch(url);
    }

    /**
     * The Search URL
     */
    protected abstract String getSearchUrl(int page, String encodedKeywords);

    protected abstract List<? extends SearchResult> searchPage(String page);
}
