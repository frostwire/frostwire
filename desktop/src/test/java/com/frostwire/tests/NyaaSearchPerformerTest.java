/*
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.tests;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.nyaa.NyaaSearchPerformer;
import com.frostwire.search.nyaa.NyaaSearchResult;
import com.frostwire.util.UrlUtils;

import java.util.List;

public class NyaaSearchPerformerTest {
    public static void main(String[] args) {
        String TEST_SEARCH_TERM = UrlUtils.encode("foo");
        NyaaSearchPerformer nyaa = new NyaaSearchPerformer("nyaa.si", 1, TEST_SEARCH_TERM, 5000);
        nyaa.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                for (SearchResult result : results) {
                    NyaaSearchResult sr = (NyaaSearchResult) result;
                    System.out.println("NyaaSearchPerformer.SearchListener.onResults:");
                    System.out.println("\t DisplayName: " + sr.getDisplayName());
                    System.out.println("\t Source: " + sr.getSource());
                    System.out.println("\t DetailsUrl: " + sr.getDetailsUrl());
                    System.out.println("\t Filename: " + sr.getFilename());
                    System.out.println("\t Hash: " + sr.getHash());
                    System.out.println("\t TorrentUrl: " + sr.getTorrentUrl());
                    System.out.println("\t Seeds: " + sr.getSeeds());
                    System.out.println("\t Size: " + sr.getSize());
                }
            }

            @Override
            public void onError(long token, SearchError error) {
            }

            @Override
            public void onStopped(long token) {
            }
        });
        try {
            nyaa.perform();
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("Aborting test.");
            return;
        }
    }
}
