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
import org.junit.Test;

import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gubatron
 * @author aldenml
 */
public class SearchTableTest {

    @Test
    public void testAddSingle() {
        SearchTable t = new SearchTable(0);

        t.add(new TestSearchResult("a"));

        List<SearchResult> data = t.data();
        assertEquals(data.size(), 1);

        SearchView view = t.view(SearchFilter.NONE);
        SortedMap<FilterKey, SearchGroup> groups = view.groups();
        assertEquals(groups.size(), 1);

        SearchGroup group = groups.get(groups.firstKey());
        assertEquals(group.data().size(), 1);

        t.clear();
        data = t.data();
        assertEquals(data.size(), 0);
        assertEquals(view.groups().size(), 0);
    }

    @Test
    public void testAddSingleListener() {
        SearchTable t = new SearchTable(0);

        final AtomicBoolean b1 = new AtomicBoolean(false);
        final AtomicBoolean b2 = new AtomicBoolean(false);

        SearchView view = t.view(SearchFilter.NONE);
        view.setListener(new SearchViewListener() {
            @Override
            public void viewChanged(SearchView view) {
                b2.set(true);
            }

            @Override
            public void viewAdded(SearchView view, List<SearchResult> results) {
                b1.set(true);
            }
        });

        t.add(new TestSearchResult("a"));
        assertTrue(b1.get());

        t.clear();
        assertTrue(b2.get());
    }
}
