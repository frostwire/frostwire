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

package com.frostwire.search.torrent;

import com.frostwire.search.SearchResult;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SimpleTorrentJsonSearchPerformer<T extends ComparableTorrentJsonItem, R extends TorrentSearchResult> extends SimpleTorrentSearchPerformer {
    private static final int DEFAULT_NUM_CRAWLS = 10;
    private final Comparator<T> itemComparator;

    private SimpleTorrentJsonSearchPerformer(String domainName, long token, String keywords, int timeout, int pages, int numCrawls) {
        super(domainName, token, keywords, timeout, pages, numCrawls);
        this.itemComparator = (a, b) -> b.getSeeds() - a.getSeeds();
    }

    public SimpleTorrentJsonSearchPerformer(String domainName, long token, String keywords, int timeout, int pages) {
        this(domainName, token, keywords, timeout, pages, DEFAULT_NUM_CRAWLS);
    }

    @Override
    protected final List<? extends SearchResult> searchPage(String page) {
        List<SearchResult> result = new LinkedList<>();
        List<T> items = parseJson(page);
        if (items != null) {
            items.sort(itemComparator);
            for (T item : items) {
                if (!isStopped()) {
                    SearchResult sr = fromItem(item);
                    result.add(sr);
                }
            }
        }
        return result;
    }

    protected abstract List<T> parseJson(String json);

    abstract R fromItem(T item);
}