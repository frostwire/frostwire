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
