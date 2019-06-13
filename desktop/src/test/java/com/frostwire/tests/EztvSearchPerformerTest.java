/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.tests;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.eztv.EztvSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;

import static com.frostwire.search.eztv.EztvSearchPerformer.SEARCH_RESULTS_REGEX;
import static com.frostwire.search.eztv.EztvSearchPerformer.TORRENT_DETAILS_PAGE_REGEX;

public final class EztvSearchPerformerTest {
    public static void main(String[] args) throws Throwable {
        String TEST_SEARCH_TERM = "foo";
        HttpClient httpClient = HttpClientFactory.newInstance();
        String fileStr;
        try {
            fileStr = httpClient.get("https://eztv.re/search/" + TEST_SEARCH_TERM);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("Aborting test.");
            return;
        }
        Pattern searchResultsDetailURLPattern = Pattern.compile(SEARCH_RESULTS_REGEX);
        Pattern detailPagePattern = Pattern.compile(TORRENT_DETAILS_PAGE_REGEX);
        Matcher searchResultsMatcher = searchResultsDetailURLPattern.matcher(fileStr);
        int found = 0;
        while (searchResultsMatcher.find()) {
            found++;
            System.out.println("\nfound " + found);
            System.out.println("result_url: [" + searchResultsMatcher.group(1) + "]");
            String detailUrl = "https://eztv.re" + searchResultsMatcher.group(1);
            System.out.println("Fetching details from " + detailUrl + " ....");
            long start = System.currentTimeMillis();
            String detailPage;
            try {
                detailPage = httpClient.get(detailUrl, 5000);
            } catch (Throwable t) {
                detailPage = null;
            }
            if (detailPage == null) {
                System.out.println("Error fetching from " + detailUrl);
                continue;
            }
            long downloadTime = System.currentTimeMillis() - start;
            System.out.println("Downloaded " + detailPage.length() + " bytes in " + downloadTime + "ms");
            SearchMatcher sm = new SearchMatcher(detailPagePattern.matcher(detailPage));
            if (sm.find()) {
                System.out.println("magneturl: [" + sm.group("magneturl") + "]");
                System.out.println("torrenturl: [" + sm.group("torrenturl") + "]");
                System.out.println("seeds: [" + sm.group("seeds") + "]");
                System.out.println("displayname: [" + sm.group("displayname") + "]");
                System.out.println("displayname2: [" + sm.group("displayname2") + "]");
                System.out.println("displaynamefallback: [" + sm.group("displaynamefallback") + "]");
                System.out.println("infohash: [" + sm.group("infohash") + "]");
                System.out.println("filesize: [" + sm.group("filesize") + "]");
                System.out.println("creationtime: [" + sm.group("creationtime") + "]");
                EztvSearchResult sr = new EztvSearchResult(detailUrl, sm);
                System.out.println(sr);
            } else {
                System.out.println("Detail page search matcher failed, check TORRENT_DETAILS_PAGE_REGEX");
            }
            System.out.println("===");
            System.out.println("Sleeping 5 seconds...");
            Thread.sleep(5000);
        }
        System.out.println("-done-");
    }
}
