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
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.SearchPerformerFactory;
import com.frostwire.search.frostclick.FrostClickSearchPattern;
import com.frostwire.util.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FrostclickSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(FrostclickSearchPerformerTest.class);

    @Test
    public void testFrostClickSearchPerformer() {
        String youtubeUrl = "https://www.youtube.com/watch?v=OtMYSeRrF8M";
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)";
        String sessionId = "test_session_123";
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");

        // V2: Use SearchPerformerFactory with FrostClickSearchPattern
        ISearchPerformer performer = SearchPerformerFactory.createSearchPerformer(
                1,
                youtubeUrl,  // FrostClick uses URL as search term
                new FrostClickSearchPattern(userAgent, sessionId, headers),
                null,  // No crawling for FrostClick
                5000
        );

        List<String> errors = new ArrayList<>();
        List<Integer> resultCounts = new ArrayList<>();

        performer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("onResults: " + results.size());
                resultCounts.add(results.size());
                for (SearchResult result : results) {
                    CompositeFileSearchResult sr = (CompositeFileSearchResult) result;
                    LOG.info(" - " + sr.getDisplayName() + " (" + sr.getSource() + ")");
                }
            }

            @Override
            public void onError(long token, SearchError error) {
                errors.add(error.toString());
                LOG.error("Search error: " + error.message());
            }

            @Override
            public void onStopped(long token) {
                LOG.info("onStopped");
            }
        });

        performer.perform();

        // Verify no errors occurred
        Assertions.assertTrue(errors.isEmpty(), "FrostClick search encountered errors: " + errors);
        // Either got results or it's OK if FrostClick doesn't return results for that URL
        LOG.info("FrostClick test completed successfully");
    }
}
