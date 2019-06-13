/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

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

package com.frostwire.search.filter;

import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;

import java.util.Comparator;
import java.util.HashMap;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SourceFilter implements SearchFilter {
    private static final Comparator<SearchResult> CMP = new Comparator<SearchResult>() {
        @Override
        public int compare(SearchResult o1, SearchResult o2) {
            int x = o1 instanceof TorrentSearchResult ? ((TorrentSearchResult) o1).getSeeds() : 0;
            int y = o2 instanceof TorrentSearchResult ? ((TorrentSearchResult) o2).getSeeds() : 0;
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }
    };
    private final HashMap<String, SourceKey> keys;

    public SourceFilter(SourceKey... keys) {
        this.keys = new HashMap<>();
        for (SourceKey k : keys) {
            this.keys.put(k.source(), k);
        }
    }

    @Override
    public FilterKey key(SearchResult sr) {
        if (keys.containsKey(sr.getSource())) {
            return keys.get(sr.getSource());
        } else {
            return FilterKey.NULL;
        }
    }

    @Override
    public boolean accept(SearchResult sr) {
        return true;
    }

    @Override
    public Comparator<SearchResult> comparator() {
        return CMP;
    }
}
