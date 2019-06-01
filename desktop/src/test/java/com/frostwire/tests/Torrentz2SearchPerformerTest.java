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

import com.frostwire.regex.Pattern;
import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrentz2.Torrentz2SearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;

import static com.frostwire.search.torrentz2.Torrentz2SearchPerformer.REGEX;


public final class Torrentz2SearchPerformerTest {
    public static void main(String[] args) throws Throwable {
        String TEST_SEARCH_TERM = "barack+obama";
        String trackers = "tr=udp://tracker.leechers-paradise.org:6969/announce&" +
                "tr=udp://tracker.coppersurfer.tk:6969/announce&" +
                "tr=udp://tracker.internetwarriors.net:1337/announce&" +
                "tr=udp://retracker.akado-ural.ru:80/announce&" +
                "tr=udp://tracker.moeking.me:6969/announce&" +
                "tr=udp://carapax.net:6969/announce&" +
                "tr=udp://retracker.baikal-telecom.net:2710/announce&" +
                "tr=udp://bt.dy20188.com:80/announce&" +
                "tr=udp://tracker.nyaa.uk:6969/announce&" +
                "tr=udp://carapax.net:6969/announce&" +
                "tr=udp://amigacity.xyz:6969/announce&" +
                "tr=udp://tracker.supertracker.net:1337/announce&" +
                "tr=udp://tracker.cyberia.is:6969/announce&" +
                "tr=udp://tracker.openbittorrent.com:80/announce&" +
                "tr=udp://tracker.msm8916.com:6969/announce&" +
                "tr=udp://tracker.sktorrent.net:6969/announce&";
        HttpClient httpClient = HttpClientFactory.newInstance();
        String fileStr;
        try {
            fileStr = httpClient.get("https://torrentz2.eu/verified?f=" + TEST_SEARCH_TERM);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("Aborting test.");
            return;
        }

        Pattern searchResultsDetailURLPattern = Pattern.compile(REGEX);

        SearchMatcher matcher = new SearchMatcher(searchResultsDetailURLPattern.matcher(fileStr));

        int found = 0;
        while (matcher.find()) {
            found++;
            System.out.println("\nfound " + found);

            String infoHash = matcher.group("infohash");
            String detailsURL = "https://torrentz2.eu/" + infoHash;
            String filename = matcher.group("filename");
            String fileSizeMagnitude = matcher.group("filesize");
            String fileSizeUnit = matcher.group("unit");
            String ageString = matcher.group("age");
            int seeds = 20;
            try {
                seeds = Integer.parseInt(matcher.group("seeds"));
            } catch (Throwable ignored) {
            }


            System.out.println("infohash: [" + matcher.group("infohash") + "]");
            System.out.println("filename: [" + matcher.group("filename") + "]");
            System.out.println("filesize: [" + matcher.group("filesize") + "]");
            System.out.println("unit: [" + matcher.group("unit") + "]");
            System.out.println("filesize: [" + matcher.group("filesize") + "]");
            System.out.println("unit: [" + matcher.group("unit") + "]");
            System.out.println("age: [" + matcher.group("age") + "]");
            System.out.println("seeds: [" + matcher.group("seeds") + "]");
            Torrentz2SearchResult sr = new Torrentz2SearchResult(detailsURL, infoHash, filename, fileSizeMagnitude, fileSizeUnit, ageString, seeds, trackers);
            System.out.println(sr);
            System.out.println("===");
        }
        System.out.println("-done-");
    }
}
