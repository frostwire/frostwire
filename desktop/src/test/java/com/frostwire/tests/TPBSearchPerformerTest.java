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

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.tpb.TPBSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TPBSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(TPBSearchPerformerTest.class);

    @Test
    public void testTPBSearch() {
        final List<TPBSearchResult> tpbResults = new ArrayList<>();
        TPBSearchPerformer tpbSearchPerformer = initializeSearchPerformer();
        assert (tpbSearchPerformer != null);

        tpbSearchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("[TPBSearchPerformerTest] onResults() " + results.size());
                for (SearchResult r : results) {
                    TPBSearchResult sr = (TPBSearchResult) r;
                    LOG.info("[TPBSearchPerformerTest] onResults() displayName = " + sr.getDisplayName());
                    LOG.info("[TPBSearchPerformerTest] onResults() detailsUrl = " + sr.getDetailsUrl());
                    LOG.info("[TPBSearchPerformerTest] onResults() torrentUrl = " + sr.getTorrentUrl());
                    LOG.info("[TPBSearchPerformerTest] onResults() seeds = " + sr.getSeeds());
                    LOG.info("[TPBSearchPerformerTest] onResults() size = " + sr.getSize());
                    LOG.info("[TPBSearchPerformerTest] onResults() source = " + sr.getSource());
                    LOG.info("[TPBSearchPerformerTest] onResults() thumbnailUrl = " + sr.getThumbnailUrl());
                    LOG.info("[TPBSearchPerformerTest] onResults() hash = " + sr.getHash());
                    LOG.info("[TPBSearchPerformerTest] onResults() filename = " + sr.getFilename());
                    LOG.info("[TPBSearchPerformerTest] ==== ");
                    tpbResults.add(sr);
                }
            }

            @Override
            public void onError(long token, SearchError error) {

            }

            @Override
            public void onStopped(long token) {

            }
        });

        tpbSearchPerformer.perform();

        assertTrue(tpbResults.size() > 0, "[TPBSearchPerformerTest] No results found using domain: " + tpbSearchPerformer.getDomainName());
    }

    private TPBSearchPerformer initializeSearchPerformer() {
        SearchEngine tpbEngine = SearchEngine.getTPBEngine();
        int wait = 250;
        int maxWait = 15000;
        int currentWait = 0;
        while (!tpbEngine.isReady() && currentWait < maxWait) {
            try {
                Thread.sleep(wait);
                currentWait += wait;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            LOG.info("[TPBSearchPerformerTest] Waiting " + currentWait + "ms for TPB engine to be ready...");
        }
        if (currentWait > maxWait) {
            LOG.error("TPB engine is not ready after " + maxWait + "ms");
            fail("[TPBSearchPerformerTest] TPB engine is not ready after " + maxWait + "ms");
            return null;
        }

        if (tpbEngine.isReady()) {
            TPBSearchPerformer searchPerformer = (TPBSearchPerformer) tpbEngine.getPerformer(1337, "free book");
            LOG.info("[TPBSearchPerformerTest] TPB engine is ready with domain: " + searchPerformer.getDomainName() + ", after " + currentWait + "ms");
            return searchPerformer;
        }
        return null;
    }
}
