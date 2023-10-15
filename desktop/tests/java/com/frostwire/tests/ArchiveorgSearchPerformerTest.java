/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.tests;

import com.frostwire.search.*;
import com.frostwire.search.archiveorg.ArchiveorgCrawledSearchResult;
import com.frostwire.search.archiveorg.ArchiveorgSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ArchiveorgSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(ArchiveorgSearchPerformerTest.class);

    @Test
    public void testSearch() {
        List<ArchiveorgCrawledSearchResult> searchResults = new ArrayList<>();
        SearchPerformer searchPerformer = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.ARCHIVEORG_ID).getPerformer(1, "free book");
        searchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("Results: " + results.size());
                for (SearchResult r : results) {
                    if (r instanceof ArchiveorgSearchResult) {
                        searchPerformer.crawl((CrawlableSearchResult) r);
                        continue;
                    }
                    if (!(r instanceof ArchiveorgCrawledSearchResult)) {
                        continue;
                    }
                    ArchiveorgCrawledSearchResult sr = (ArchiveorgCrawledSearchResult) r;
                    searchResults.add(sr);
                    LOG.info("[ArchiveorgSearchPerformerTest] source: " + sr.getSource());
                    LOG.info("[ArchiveorgSearchPerformerTest] size: " + sr.getSize());
                    LOG.info("[ArchiveorgSearchPerformerTest] ---------------------");
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
        Assertions.assertTrue(searchResults.size() > 0, "[ArchiveorgSearchPerformerTest] No results found");
    }
}
