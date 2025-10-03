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

import com.frostwire.search.*;
import com.frostwire.search.internetarchive.InternetArchiveCrawledSearchResult;
import com.frostwire.search.internetarchive.InternetArchiveSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class InternetArchiveSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(InternetArchiveSearchPerformerTest.class);

    @Test
    public void testSearch() {
        List<InternetArchiveCrawledSearchResult> searchResults = new ArrayList<>();
        SearchPerformer searchPerformer = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.INTERNET_ARCHIVE_ID).getPerformer(1, "free book");
        searchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("Results: " + results.size());
                for (SearchResult r : results) {
                    if (r instanceof InternetArchiveSearchResult) {
                        searchPerformer.crawl((CrawlableSearchResult) r);
                        continue;
                    }
                    if (!(r instanceof InternetArchiveCrawledSearchResult)) {
                        continue;
                    }
                    InternetArchiveCrawledSearchResult sr = (InternetArchiveCrawledSearchResult) r;
                    searchResults.add(sr);
                    LOG.info("[InternetArchiveSearchPerformerTest] source: " + sr.getSource());
                    LOG.info("[InternetArchiveSearchPerformerTest] size: " + sr.getSize());
                    LOG.info("[InternetArchiveSearchPerformerTest] ---------------------");
                }
            }

            @Override
            public void onError(long token, SearchError error) {

            }

            @Override
            public void onStopped(long token) {

            }
        });
        searchPerformer.perform();
        Assertions.assertTrue(searchResults.size() > 0, "[InternetArchiveSearchPerformerTest] No results found");
    }
}
