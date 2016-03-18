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

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

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
