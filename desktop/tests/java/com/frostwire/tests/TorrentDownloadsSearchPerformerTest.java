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

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrentdownloads.TorrentDownloadsSearchPerformer;
import com.frostwire.search.torrentdownloads.TorrentDownloadsSearchResult;
import com.frostwire.search.torrentdownloads.TorrentDownloadsTempSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TorrentDownloadsSearchPerformerTest {

    private static final Logger LOG = Logger.getLogger(TorrentDownloadsSearchPerformerTest.class);

    @Test
    public void testTorrentDownloadsSearch() {
        final List<TorrentDownloadsSearchResult> searchResults = new ArrayList<>();
        TorrentDownloadsSearchPerformer searchPerformer = (TorrentDownloadsSearchPerformer) SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.TORRENTDOWNLOADS_ID).getPerformer(1, "free");
        assert (searchPerformer != null);

        searchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("[TorrentDownloadsSearchPerformerTest] onResults() " + results.size());
                for (SearchResult r : results) {
                    if (r instanceof TorrentDownloadsTempSearchResult) {
                        LOG.info("[TorrentDownloadsSearchPerformerTest] onResults() skipping temp result");
                        searchPerformer.crawl((CrawlableSearchResult) r);
                        continue;
                    }
                    TorrentDownloadsSearchResult sr = (TorrentDownloadsSearchResult) r;
                    LOG.info("[TorrentDownloadsSearchPerformerTest] onResults() hash = " + sr.getHash());
                    LOG.info("[TorrentDownloadsSearchPerformerTest] onResults() size = " + sr.getSize());
                    LOG.info("[TorrentDownloadsSearchPerformerTest] ==== ");
                    searchResults.add(sr);
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

        assertTrue(searchResults.size() > 0, "[TorrentDownloadsSearchPerformerTest] No results found using domain: " + searchPerformer.getDomainName());
    }
}

