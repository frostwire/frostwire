/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(R). All rights reserved.
 
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

package com.frostwire.android.tests.search;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.frostwire.logging.Logger;
import com.frostwire.search.SearchManagerListener;
import com.frostwire.search.SearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.archiveorg.ArchiveorgCrawledSearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class MockSearchResultListener implements SearchManagerListener {

    private static final Logger LOG = Logger.getLogger(MockSearchResultListener.class);

    private final List<SearchResult> results;

    private int numResults;

    public MockSearchResultListener() {
        this.results = Collections.synchronizedList(new LinkedList<SearchResult>());
    }

    public int getNumResults() {
        return numResults;
    }

    @Override
    public void onResults(SearchPerformer performer, List<? extends SearchResult> results) {
        this.results.addAll(results);
        this.numResults += results.size();
    }

    @Override
    public void onFinished(long token) {
    }

    public void logResults() {
        synchronized (results) {
            for (SearchResult sr : results) {
                if (sr instanceof TorrentCrawledSearchResult) {
                    TorrentCrawledSearchResult tsr = (TorrentCrawledSearchResult) sr;
                    System.out.println("Files name: " +  tsr.getFilename());
                } else {
                    LOG.info(sr.toString());
                }
            }
        }
    }

    public boolean containsDeepSearchResult() {
        synchronized (results) {
            for (SearchResult sr : results) {
                if (sr instanceof TorrentCrawledSearchResult || sr instanceof ArchiveorgCrawledSearchResult) {
                    if (sr instanceof TorrentCrawledSearchResult) {
                        TorrentCrawledSearchResult tsr = (TorrentCrawledSearchResult) sr;
                        System.out.println("Display name: " +  tsr.getFilename());
                    }
                    return true;
                }
            }
        }

        return false;
    }
}
