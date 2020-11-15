/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.limetorrents.LimeTorrentsSearchPerformer;
import com.frostwire.search.limetorrents.LimeTorrentsSearchResult;
import com.frostwire.util.UrlUtils;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public class LimeTorrentsTest {

    public static void main(String[] args) {
        String TEST_SEARCH_TERM = UrlUtils.encode("foobar");
        LimeTorrentsSearchPerformer limeTorrentsSearchPerformer = new LimeTorrentsSearchPerformer("www.limetorrents.info", 1, TEST_SEARCH_TERM, 5000);
        limeTorrentsSearchPerformer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                for (SearchResult result : results) {
                    LimeTorrentsSearchResult sr = (LimeTorrentsSearchResult) result;
                    System.out.println("LimeTorrentsSearchPerformer.SearchListener.onResults:");
                    System.out.println("\t DisplayName: " + sr.getDisplayName());
                    System.out.println("\t Source: " + sr.getSource());
                    System.out.println("\t DetailsUrl: " + sr.getDetailsUrl());
                    System.out.println("\t Filename: " + sr.getFilename());
                    System.out.println("\t Hash: " + sr.getHash());
                    System.out.println("\t TorrentUrl: " + sr.getTorrentUrl());
                    System.out.println("\t Seeds: " + sr.getSeeds());
                    System.out.println("\t Size: " + sr.getSize());
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
            System.out.println("Aborting test.");
            return;
        }
        System.out.println("-done-");
    }
}
