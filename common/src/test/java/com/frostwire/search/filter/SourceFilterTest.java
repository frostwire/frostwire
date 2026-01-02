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

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gubatron
 * @author aldenml
 */
public class SourceFilterTest {

    @Test
    public void testAddTwoSameSource() {
        SearchTable t = new SearchTable(0);

        TestSearchResult sr1 = new TestSearchResult("a");
        sr1 = sr1.clone().source("test");
        TestSearchResult sr2 = new TestSearchResult("b");
        sr2 = sr2.clone().source("test");

        SourceKey k = new SourceKey("test", 0);
        SourceFilter f = new SourceFilter(k);

        SearchView view = t.view(f);

        t.add(Arrays.asList(sr1, sr2));

        assertEquals(t.data().size(), 2);
        assertEquals(view.groups().size(), 1);
    }

    @Test
    public void testAddTwoSources() {
        SearchTable t = new SearchTable(0);

        TestSearchResult sr1 = new TestSearchResult("a");
        sr1 = sr1.clone().source("test1");
        TestSearchResult sr2 = new TestSearchResult("b");
        sr2 = sr2.clone().source("test2");

        SourceKey k1 = new SourceKey("test1", 0);
        SourceKey k2 = new SourceKey("test2", 1);
        SourceFilter f = new SourceFilter(k1, k2);

        SearchView view = t.view(f);

        t.add(Arrays.asList(sr1, sr2));

        assertEquals(t.data().size(), 2);
        assertEquals(view.groups().size(), 2);
    }
}
