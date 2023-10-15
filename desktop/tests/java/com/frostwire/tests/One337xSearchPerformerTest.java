/*
 * Created by Angel Leon (@gubatron)
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

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.one337x.One337xSearchPerformer;
import com.frostwire.search.one337x.One337xSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.frostwire.util.ThreadPool;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.OkHttpClientWrapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

public final class One337xSearchPerformerTest {
    private static final Logger LOG = Logger.getLogger(One337xSearchPerformerTest.class);
    @Test
    public void one377xSearchTest() {
        String TEST_SEARCH_TERM = "creative commons";
        HttpClient httpClient = new OkHttpClientWrapper(new ThreadPool("testPool", 4, new LinkedBlockingQueue<>(), false));
        String fileStr = null;
        try {
            fileStr = httpClient.get("https://www.1377x.to/search/" + TEST_SEARCH_TERM + "/1/");
        } catch (IOException e) {
            fail(e.getMessage());
        }
        Pattern searchResultsDetailURLPattern = Pattern.compile(One337xSearchPerformer.SEARCH_RESULTS_REGEX);
        Pattern detailPagePattern = Pattern.compile(One337xSearchPerformer.TORRENT_DETAILS_PAGE_REGEX);
        Matcher searchResultsMatcher = searchResultsDetailURLPattern.matcher(fileStr);
        int found = 0;
        while (searchResultsMatcher.find() && found < 5) {
            found++;
            LOG.info("\nfound " + found);
            LOG.info("result_url: [" + searchResultsMatcher.group(1) + "]");
            String detailUrl = "https://www.1377x.to/torrent/" + searchResultsMatcher.group("itemId") + "/" + searchResultsMatcher.group("htmlFileName");
            String displayName = searchResultsMatcher.group("displayName");
            //LOG.info("Fetching details from " + detailUrl + " ....");
            long start = System.currentTimeMillis();
            String detailPage = null;
            try {
                detailPage = httpClient.get(detailUrl, 5000);
            } catch (IOException e) {
                fail(e.getMessage());
            }
            if (detailPage == null) {
                fail("Error fetching from " + detailUrl);
                continue;
            }
            long downloadTime = System.currentTimeMillis() - start;
            LOG.info("Downloaded " + detailPage.length() + " bytes in " + downloadTime + "ms");
            SearchMatcher sm = new SearchMatcher(detailPagePattern.matcher(detailPage));
            if (sm.find()) {
                assertFalse(StringUtils.isNullOrEmpty(displayName), "displayName is null or empty");
                LOG.info("size: [" + sm.group("size") + "]");
                assertFalse(StringUtils.isNullOrEmpty(sm.group("size")), "size is null or empty");
                LOG.info("creationDate: [" + sm.group("creationDate") + "]");
                assertFalse(StringUtils.isNullOrEmpty(sm.group("creationDate")), "creationDate is null or empty");
                assertFalse(StringUtils.isNullOrEmpty(sm.group("seeds")), "seeds is null or empty");
                assertFalse(StringUtils.isNullOrEmpty(sm.group("magnet")), "magnet is null or empty");
                One337xSearchResult sr = new One337xSearchResult(detailUrl, displayName, sm);
                // can't make this assertion, site isn't really giving magnet links
                //assertFalse(StringUtils.isNullOrEmpty(sr.getHash()));
            } else {
                LOG.error("ERROR: Detail page search matcher failed, check TORRENT_DETAILS_PAGE_REGEX");
                LOG.error("HTML @ " + detailUrl + ":");
                LOG.error(detailPage);
                fail("ERROR: Detail page search matcher failed, check TORRENT_DETAILS_PAGE_REGEX (" + detailUrl + ")");
                return;
            }
            LOG.info("===");
            LOG.info("Sleeping 5 seconds...");
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        LOG.info("-done-");
        if (found == 0) {
            LOG.info(fileStr);
            fail("No search results");
        }
    }
}
