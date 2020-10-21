/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.eztv.EztvSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.StringUtils;
import com.frostwire.util.http.HttpClient;
import org.junit.jupiter.api.Test;

import static com.frostwire.search.eztv.EztvSearchPerformer.SEARCH_RESULTS_REGEX;
import static com.frostwire.search.eztv.EztvSearchPerformer.TORRENT_DETAILS_PAGE_REGEX;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public final class EztvSearchPerformerTest {
    @Test
    public void eztvSearchTest() {
        String TEST_SEARCH_TERM = "foo";
        HttpClient httpClient = HttpClientFactory.newInstance();
        String fileStr;
        try {
            fileStr = httpClient.get("https://eztv.re/search/" + TEST_SEARCH_TERM);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("Aborting test.");
            fail(t.getMessage());
            return;
        }
        Pattern searchResultsDetailURLPattern = Pattern.compile(SEARCH_RESULTS_REGEX);
        Pattern detailPagePattern = Pattern.compile(TORRENT_DETAILS_PAGE_REGEX);
        Matcher searchResultsMatcher = searchResultsDetailURLPattern.matcher(fileStr);
        int found = 0;
        while (searchResultsMatcher.find() && found < 5) {
            found++;
            System.out.println("\nfound " + found);
            String result_url = searchResultsMatcher.group(1);
            assertTrue(!StringUtils.isNullOrEmpty(result_url), "result_url was null or empty");
            System.out.println("result_url: [" + result_url + "]");
            String detailUrl = "https://eztv.re" + result_url;
            System.out.println("Fetching details from " + detailUrl + " ....");
            long start = System.currentTimeMillis();
            String detailPage;
            try {
                detailPage = httpClient.get(detailUrl, 5000);
            } catch (Throwable t) {
                detailPage = null;
                fail(t.getMessage());
            }
            if (detailPage == null) {
                System.err.println("Error fetching from " + detailUrl);
                continue;
            }
            long downloadTime = System.currentTimeMillis() - start;
            System.out.println("Downloaded " + detailPage.length() + " bytes in " + downloadTime + "ms");
            SearchMatcher sm = new SearchMatcher(detailPagePattern.matcher(detailPage));
            if (sm.find()) {
                System.out.println("magneturl: [" + sm.group("magneturl") + "]");
                assertTrue(!StringUtils.isNullOrEmpty(sm.group("magneturl")), "magneturl was null or empty");
                System.out.println("torrenturl: [" + sm.group("torrenturl") + "]");
                assertTrue(!StringUtils.isNullOrEmpty(sm.group("torrenturl")), "torrenturl was null or empty");
                System.out.println("seeds: [" + sm.group("seeds") + "]");
                assertTrue(!StringUtils.isNullOrEmpty(sm.group("seeds")), "seeds was null or empty");
                System.out.println("displayname: [" + sm.group("displayname") + "]");
                System.out.println("displayname2: [" + sm.group("displayname2") + "]");
                System.out.println("displaynamefallback: [" + sm.group("displaynamefallback") + "]");

                assertTrue(!StringUtils.isNullOrEmpty(sm.group("displayname")) ||
                                !StringUtils.isNullOrEmpty(sm.group("displayname2")) ||
                                !StringUtils.isNullOrEmpty(sm.group("displaynamefallback"))
                        , "displayname && displaynam2 && displaynamefallback were all null or empty");

                System.out.println("infohash: [" + sm.group("infohash") + "]");
                assertTrue(!StringUtils.isNullOrEmpty(sm.group("infohash")), "infohash was null or empty");
                System.out.println("filesize: [" + sm.group("filesize") + "]");
                assertTrue(!StringUtils.isNullOrEmpty(sm.group("filesize")), "filesize was null or empty");
                System.out.println("creationtime: [" + sm.group("creationtime") + "]");
                assertTrue(!StringUtils.isNullOrEmpty(sm.group("creationtime")), "creationtime was null or empty");
                EztvSearchResult sr = new EztvSearchResult(detailUrl, sm);
                System.out.println(sr);
            } else {
                fail("Detail page search matcher failed, check TORRENT_DETAILS_PAGE_REGEX (" + detailUrl + ")");
            }
            System.out.println("===");
            System.out.println("Sleeping 5 seconds...");
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (found == 0) {
            fail("No search results");
        }
        System.out.println("-done-");
    }
}
