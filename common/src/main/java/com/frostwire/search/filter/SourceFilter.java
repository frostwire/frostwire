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

    private static final Comparator<SearchResult> CMP = (o1, o2) -> {
        int x = o1 instanceof TorrentSearchResult ? ((TorrentSearchResult) o1).getSeeds() : 0;
        int y = o2 instanceof TorrentSearchResult ? ((TorrentSearchResult) o2).getSeeds() : 0;
        return Integer.compare(x, y);
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
