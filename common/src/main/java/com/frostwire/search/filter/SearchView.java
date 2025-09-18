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

import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchView {
    private final SearchTable table;
    private final SearchFilter filter;
    private final SortedMap<FilterKey, SearchGroup> groups;
    private SearchViewListener listener;

    public SearchView(SearchTable table, SearchFilter filter) {
        this.table = table;
        this.filter = filter;
        this.groups = new TreeMap<>();
        add(table.data());
    }

    public SearchFilter filter() {
        return filter;
    }

    public SortedMap<FilterKey, SearchGroup> groups() {
        return Collections.unmodifiableSortedMap(groups);
    }

    public void add(List<? extends SearchResult> results) {
        LinkedList<SearchResult> added = new LinkedList<>();
        for (SearchResult sr : results) {
            if (filter.accept(sr)) {
                FilterKey key = filter.key(sr);
                SearchGroup group = groups.get(key);
                if (group == null) {
                    group = new SearchGroup(filter);
                    groups.put(key, group);
                }
                group.add(sr);
                added.add(sr);
            }
        }
        if (listener != null && added.size() > 0) {
            listener.viewAdded(this, added);
        }
    }

    public void refresh() {
        clear();
        add(table.data());
    }

    public SearchViewListener getListener() {
        return listener;
    }

    public void setListener(SearchViewListener listener) {
        this.listener = listener;
    }

    public void clear() {
        for (SearchGroup g : groups.values()) {
            g.clear();
        }
        groups.clear();
        if (listener != null) {
            listener.viewChanged(this);
        }
    }
}
