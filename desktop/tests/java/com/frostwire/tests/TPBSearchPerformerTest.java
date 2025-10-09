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
import com.frostwire.search.SearchResult;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.tpb.TPBSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TPBSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(TPBSearchPerformerTest.class);

    @Test
    public void testTPBSearch() {
        final List<TPBSearchResult> tpbResults = new ArrayList<>();
        TPBSearchPerformer tpbSearchPerformer = initializeSearchPerformer();
        assert (tpbSearchPerformer != null);
        CountDownLatch latch = new CountDownLatch(1);

        tpbSearchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LOG.info("[TPBSearchPerformerTest] onResults() " + results.size());
                try {
                    for (SearchResult r : results) {
                        TPBSearchResult sr = (TPBSearchResult) r;
                        LOG.info("[TPBSearchPerformerTest] onResults() size = " + sr.getSize());
                        LOG.info("[TPBSearchPerformerTest] onResults() hash = " + sr.getHash());
                        LOG.info("[TPBSearchPerformerTest] ==== ");
                        tpbResults.add(sr);
                    }
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onError(long token, SearchError error) {
                latch.countDown();
            }

            @Override
            public void onStopped(long token) {
                latch.countDown();
            }
        });

        tpbSearchPerformer.perform();
        boolean completed = false;
        try {
            LOG.info("[TPBSearchPerformerTest] Waiting for results...");
            completed = latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("[TPBSearchPerformerTest] Error waiting for results: " + e.getMessage());
            fail("[TPBSearchPerformerTest] Interrupted while waiting for results");
        }
        assertTrue(completed, "[TPBSearchPerformerTest] Timed out waiting for results from TPB performer");
        LOG.info("[TPBSearchPerformerTest] Results found: " + tpbResults.size() + " using domain: " + tpbSearchPerformer.getDomainName());
        assertFalse(tpbResults.isEmpty(), "[TPBSearchPerformerTest] No results found using domain: " + tpbSearchPerformer.getDomainName());
    }

    private TPBSearchPerformer initializeSearchPerformer() {
        SearchEngine tpbEngine = SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.TPB_ID);
        assertNotNull(tpbEngine, "[TPBSearchPerformerTest] TPB engine is null");
        int wait = 250;
        int maxWait = 15000;
        int currentWait = 0;
        while (!tpbEngine.isReady() && currentWait < maxWait) {
            try {
                Thread.sleep(wait);
                currentWait += wait;
            } catch (InterruptedException e) {
                LOG.error("[TPBSearchPerformerTest] Error waiting for TPB engine to be ready: " + e.getMessage());
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
