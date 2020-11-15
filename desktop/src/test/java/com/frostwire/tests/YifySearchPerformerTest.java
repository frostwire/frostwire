/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.

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
import com.frostwire.search.yify.YifySearchPerformer;
import com.frostwire.search.yify.YifySearchResult;
import com.frostwire.util.StringUtils;
import com.frostwire.util.ThreadPool;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.OKHTTPClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public final class YifySearchPerformerTest {
    @Test
    public void yifiSearchTest() {
    //public static void main(String[] args) {
        String TEST_SEARCH_TERM = "one";
        HttpClient httpClient = new OKHTTPClient(new ThreadPool("testPool", 4, new LinkedBlockingQueue<>(), false));
        String fileStr = null;
        try {
            fileStr = httpClient.get("https://www.yify-torrent.org/search/" + TEST_SEARCH_TERM);
        } catch (IOException e) {
            e.printStackTrace();
            fail(e.getMessage());
            return;
        }
        Pattern searchResultsDetailURLPattern = Pattern.compile(YifySearchPerformer.SEARCH_RESULTS_REGEX);
        Pattern detailPagePattern = Pattern.compile(YifySearchPerformer.TORRENT_DETAILS_PAGE_REGEX);
        Matcher searchResultsMatcher = searchResultsDetailURLPattern.matcher(fileStr);
        int found = 0;
        while (searchResultsMatcher.find() && found < 5) {
            found++;
            System.out.println("\nfound " + found);
            System.out.println("result_url: [" + searchResultsMatcher.group(1) + "]");
            String detailUrl = "https://www.yify-torrent.org/torrent/" + searchResultsMatcher.group("itemId") + "/" + searchResultsMatcher.group("htmlFileName");
            System.out.println("Fetching details from " + detailUrl + " ....");
            long start = System.currentTimeMillis();
            String detailPage = null;
            try {
                detailPage = httpClient.get(detailUrl, 5000);
            } catch (IOException e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
            if (detailPage == null) {
                System.out.println("Error fetching from " + detailUrl);
                continue;
            }
            long downloadTime = System.currentTimeMillis() - start;
            System.out.println("Downloaded " + detailPage.length() + " bytes in " + downloadTime + "ms");
            SearchMatcher sm = new SearchMatcher(detailPagePattern.matcher(detailPage));
            if (sm.find()) {
                System.out.println("displayname: [" + sm.group("displayName") + "]");
                System.out.println("size: [" + sm.group("size") + "]");
                System.out.println("creationDate: [" + sm.group("creationDate") + "]");
                System.out.println("seeds: [" + sm.group("seeds") + "]");
                System.out.println("magnet: [" + sm.group("magnet") + "]");

                assertTrue(!StringUtils.isNullOrEmpty(sm.group("displayName")),"displayName null or empty");
                assertTrue(!StringUtils.isNullOrEmpty(sm.group("creationDate")),"creationDate null or empty");
                assertTrue(!StringUtils.isNullOrEmpty(sm.group("seeds")),"seeds null or empty");
                assertTrue(!StringUtils.isNullOrEmpty(sm.group("magnet")),"magnet null or empty");

                YifySearchResult sr = new YifySearchResult(detailUrl, sm);
                System.out.println(sr);
            } else {
                System.out.println("Detail page search matcher failed, check TORRENT_DETAILS_PAGE_REGEX");
                fail("TORRENT_DETAILS_PAGE_REGEX broken");
            }
            System.out.println("===");
            System.out.println("Sleeping 4 seconds...");
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("-done-");
        if (found == 0) {
            System.out.println(fileStr);
            fail("No search results");
        }
    }
}
