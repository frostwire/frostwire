/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchTable {
    private final long token;
    private final LinkedList<SearchResult> data;
    private final LinkedList<WeakReference<SearchView>> views;
    private final Object lock;

    public SearchTable(long token) {
        this.token = token;
        this.data = new LinkedList<>();
        this.views = new LinkedList<>();
        this.lock = new Object();
    }

    public long token() {
        return token;
    }

    public List<SearchResult> data() {
        return Collections.unmodifiableList(data);
    }

    public SearchView view(SearchFilter filter) {
        SearchView v = new SearchView(this, filter);
        synchronized (lock) {
            views.add(Ref.weak(v));
        }
        return v;
    }

    public void add(List<? extends SearchResult> results) {
        data.addAll(results);
        synchronized (lock) {
            Iterator<WeakReference<SearchView>> it = views.iterator();
            while (it.hasNext()) {
                WeakReference<SearchView> r = it.next();
                if (Ref.alive(r)) {
                    r.get().add(results);
                } else {
                    it.remove();
                }
            }
        }
    }

    public void clear() {
        data.clear();
        synchronized (lock) {
            Iterator<WeakReference<SearchView>> it = views.iterator();
            while (it.hasNext()) {
                WeakReference<SearchView> r = it.next();
                if (Ref.alive(r)) {
                    r.get().clear();
                } else {
                    it.remove();
                }
            }
        }
    }

    /**
     * This method is implemented calling {@link #add(List)} with a list of
     * a single element.
     *
     * @param sr
     */
    public void add(SearchResult sr) {
        add(Arrays.asList(sr));
    }
}
