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

package com.frostwire.tests;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.yt.YTSearchPattern;
import com.frostwire.util.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class YTSearchPatternTest {
    private static final Logger LOG = Logger.getLogger(YTSearchPatternTest.class);

    @Test
    public void test() {
        LOG.info("test - YouTube V2 Search Pattern");
        List<SearchResult> results = new ArrayList<>();
        SearchListener listener = new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> searchResults) {
                results.addAll(searchResults);
            }

            @Override
            public void onStopped(long token) {

            }

            @Override
            public void onError(long token, SearchError error) {
                LOG.error("YTSearchPatternTest error: " + error.message());
            }
        };

        // V2: Using new flat architecture via SearchPerformerFactory
        ISearchPerformer performer = SearchPerformerFactory.createSearchPerformer(
                1,
                "frostwire",
                new YTSearchPattern(),
                null,  // No crawling for YouTube
                5000
        );
        performer.setListener(listener);
        performer.perform();

        LOG.info("YTSearchPatternTest results: " + results.size());
        Assertions.assertTrue(results.size() > 0, "No results found");
    }

}
