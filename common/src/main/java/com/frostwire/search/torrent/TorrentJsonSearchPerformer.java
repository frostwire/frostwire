/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.search.torrent;

import com.frostwire.search.SearchResult;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class TorrentJsonSearchPerformer<T extends ComparableTorrentJsonItem, R extends TorrentSearchResult> extends TorrentSearchPerformer {
    private static final int DEFAULT_NUM_CRAWLS = 10;
    private final Comparator<T> itemComparator;

    private TorrentJsonSearchPerformer(String domainName, long token, String keywords, int timeout, int pages, int numCrawls) {
        super(domainName, token, keywords, timeout, pages, numCrawls);
        this.itemComparator = (a, b) -> b.getSeeds() - a.getSeeds();
    }

    public TorrentJsonSearchPerformer(String domainName, long token, String keywords, int timeout, int pages) {
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

    abstract List<T> parseJson(String json);

    abstract R fromItem(T item);
}