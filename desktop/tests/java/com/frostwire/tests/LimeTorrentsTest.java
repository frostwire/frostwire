/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
import com.frostwire.search.limetorrents.LimeTorrentsSearchPerformer;
import com.frostwire.search.limetorrents.LimeTorrentsSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class LimeTorrentsTest {
    private static final Logger LOG = Logger.getLogger(LimeTorrentsTest.class);

    public static void main(String[] args) {
        String TEST_SEARCH_TERM = UrlUtils.encode("foobar");
        LimeTorrentsSearchPerformer limeTorrentsSearchPerformer = new LimeTorrentsSearchPerformer("www.limetorrents.info", 1, TEST_SEARCH_TERM, 5000);
        limeTorrentsSearchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                for (SearchResult result : results) {
                    LimeTorrentsSearchResult sr = (LimeTorrentsSearchResult) result;
                    LOG.info("\t Hash: " + sr.getHash());
                    LOG.info("\t Seeds: " + sr.getSeeds());
                }
                limeTorrentsSearchPerformer.stop();
            }

            @Override
            public void onError(long token, SearchError error) {
                System.err.println(error.message());
            }

            @Override
            public void onStopped(long token) {
            }
        });
        try {
            limeTorrentsSearchPerformer.perform();
        } catch (Throwable t) {
            t.printStackTrace();
            LOG.info("Aborting test.");
            return;
        }
        LOG.info("-done-");
    }
}
