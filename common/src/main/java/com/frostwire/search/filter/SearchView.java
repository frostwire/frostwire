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
