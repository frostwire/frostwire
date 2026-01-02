/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.tests;

import com.frostwire.search.FileSearchResult;
import com.frostwire.search.one337x.One337xSearchPattern;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.ThreadPool;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.OkHttpClientWrapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

public final class One337xSearchPatternTest {
    private static final Logger LOG = Logger.getLogger(One337xSearchPatternTest.class);

    @Test
    public void one337xSearchTest() {
        String TEST_SEARCH_TERM = "creative commons";
        HttpClient httpClient = new OkHttpClientWrapper(new ThreadPool("testPool", 4, new LinkedBlockingQueue<>(), false));
        String responseBody = null;
        try {
            responseBody = httpClient.get("https://www.1377x.to/search/" + TEST_SEARCH_TERM + "/1/");
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertNotNull(responseBody, "Response body should not be null");

        // Test V2 pattern-based search
        One337xSearchPattern pattern = new One337xSearchPattern();
        List<FileSearchResult> results = pattern.parseResults(responseBody);

        assertNotNull(results, "Results should not be null");
        assertFalse(results.isEmpty(), "Should find at least one result");
        assertTrue(results.size() <= 20, "Should have at most 20 results");

        LOG.info("Found " + results.size() + " results");

        // Verify first few results have expected properties
        int checked = 0;
        for (FileSearchResult result : results) {
            if (checked >= 3) break;
            checked++;

            CompositeFileSearchResult sr = (CompositeFileSearchResult) result;

            assertFalse(sr.getDisplayName() == null || sr.getDisplayName().isEmpty(),
                    "Result " + checked + ": displayName is null or empty");
            LOG.info("Result " + checked + " - displayName: " + sr.getDisplayName());

            assertFalse(sr.getDetailsUrl() == null || sr.getDetailsUrl().isEmpty(),
                    "Result " + checked + ": detailsUrl is null or empty");
            LOG.info("Result " + checked + " - detailsUrl: " + sr.getDetailsUrl());

            assertEquals("1337x", sr.getSource(),
                    "Result " + checked + ": source should be 1337x");

            assertFalse(sr.isPreliminary(),
                    "Result " + checked + ": should NOT be preliminary (crawling is done inside performer, not in UI)");
            LOG.info("Result " + checked + " - isPreliminary: false ✓");

            assertTrue(sr.isCrawlable(),
                    "Result " + checked + ": should be crawlable (performer crawls details page internally)");
            LOG.info("Result " + checked + " - isCrawlable: true ✓");

            LOG.info("===");
        }

        LOG.info("-done-");
    }
}
