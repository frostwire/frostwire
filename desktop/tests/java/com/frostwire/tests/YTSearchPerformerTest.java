/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.

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
