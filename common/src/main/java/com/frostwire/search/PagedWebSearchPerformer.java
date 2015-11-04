/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.search;

import com.frostwire.logging.Logger;
import com.frostwire.util.StringUtils;

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
            onResults(searchPage(i));
        }
    }

    protected List<? extends SearchResult> searchPage(int page) {
        List<? extends SearchResult> result = Collections.emptyList();
        try {
            String url = getUrl(page, getEncodedKeywords());
            String text = fetchSearchPage(url);
            if (!StringUtils.isNullOrEmpty(text)) {
                result = searchPage(text);
            }
        } catch (Throwable e) {
            LOG.error("Error searching page: " + e.getMessage(), e);
        }
        return result;
    }

    protected String fetchSearchPage(String url) throws IOException {
        return fetch(url);
    }

    protected abstract String getUrl(int page, String encodedKeywords);

    protected abstract List<? extends SearchResult> searchPage(String page);
}
