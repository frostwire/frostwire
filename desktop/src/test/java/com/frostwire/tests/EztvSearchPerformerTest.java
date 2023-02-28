/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.eztv.EztvSearchPerformer;
import com.frostwire.search.eztv.EztvSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public final class EztvSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(EztvSearchPerformerTest.class);

    @Test
    public void eztvSearchTest() {
        ArrayList<EztvSearchResult> testResults = new ArrayList<>();
        EztvSearchPerformer searchPerformer = (EztvSearchPerformer) SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.EZTV_ID).getPerformer(1, "cspan");
        searchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                for (SearchResult sr : results) {
                    EztvSearchResult esr = (EztvSearchResult) sr;
                    LOG.info("[EztvSearchPerformerTest] hash: " + esr.getHash());
                    LOG.info("[EztvSearchPerformerTest] fileSize: " + esr.getSize());
                    testResults.add(esr);
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
        // don't fail if DDOS protection is active
        if (!searchPerformer.isDDOSProtectionActive()) {
            Assertions.assertFalse(testResults.isEmpty(), "[EztvSearchPerformerTest] results should not be empty");
        }
    }
}
