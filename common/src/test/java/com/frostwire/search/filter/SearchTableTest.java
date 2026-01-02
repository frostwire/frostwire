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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
