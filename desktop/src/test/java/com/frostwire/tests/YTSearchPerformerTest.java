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
import com.frostwire.search.SearchResult;
import com.frostwire.search.yt.YTSearchPerformer;
import com.frostwire.util.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class YTSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(YTSearchPerformerTest.class);

    @Test
    public void test() {
        LOG.info("test");
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
                LOG.error("YTSearchPerformerTest error: " + error.message());
            }
        };
        YTSearchPerformer searchPerformer = new YTSearchPerformer(1, "frostwire", 5000, 1);
        searchPerformer.setListener(listener);
        searchPerformer.perform();
        LOG.info("YTSearchPerformerTest results: " + results.size());
        if (results.isEmpty()) {
            LOG.info("YTSearchPerformerTest no results found, htmlOutput:\n\n" + searchPerformer.getHtmlOutput() + "\n\n");
        }
        Assertions.assertEquals(true, results.size() > 0, "No results found");
    }

}
