/*
 *     Created by Angel Leon (@gubatron)
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
import com.frostwire.search.frostclick.FrostClickSearchPerformer;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class FrostclickSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(FrostclickSearchPerformerTest.class);

    @Test
    public void testFrostClickSearchPerformer() {
        SearchPerformer searchPerformer = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.FROSTCLICK_ID).getPerformer(1, "https://www.youtube.com/watch?v=OtMYSeRrF8M");
        List<String> errors = new ArrayList<>();
        searchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("onResults: " + results.size());
            }

            @Override
            public void onError(long token, SearchError error) {
                errors.add(error.toString());
            }

            @Override
            public void onStopped(long token) {
                LOG.info("onStopped");
            }
        });
        searchPerformer.perform();
        Assertions.assertTrue(((FrostClickSearchPerformer) searchPerformer).wasResponseOk(), "FrostClickSearchPerformer response was not as expected");
    }
}
