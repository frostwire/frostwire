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
