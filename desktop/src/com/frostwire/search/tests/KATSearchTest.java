/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.search.tests;

import com.frostwire.search.SearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.kat.KATSearchPerformer;
import com.frostwire.search.kat.KATSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.search.SearchEngine;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import org.apache.commons.io.FileUtils;
import rx.Observable;
import rx.functions.Action1;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @author gubatron
 * @author aldenml
 */
public class KATSearchTest {
    public static void main(String[] args) throws InterruptedException {
        SearchEngine KAT = new SearchEngine(1, "KAT", SearchEnginesSettings.KAT_SEARCH_ENABLED, "kat.cr") {
            @Override
            public SearchPerformer getPerformer(long token, String keywords) {
                return new KATSearchPerformer(KAT.getDomainName(), token, keywords, 10000);
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        final SearchPerformer performer;
        performer = KAT.getPerformer(1, "public domain");

        Action1 onNextAction = new Action1<List<? extends SearchResult>>() {
            @Override
            public void call(List<? extends SearchResult> searchResults) {
                System.out.println("doOnNext!");
                if (searchResults instanceof List) {
                    try {
                        if (!testOnSearchResults((List<KATSearchResult>) searchResults)) {
                            System.out.println("Test failed.");
                        } else {
                            System.out.println("Test passed.");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    latch.countDown();
                }
            }
        };

        final Observable<List<? extends SearchResult>> observable = performer.observable();
        observable.forEach(onNextAction);
        performer.perform();
        System.out.println("performer.perform()\nWaiting...");
        latch.await();

        //System.out.println("Bye bye");

        /**
         byte[] readAllBytes = Files.readAllBytes(Paths.get("/Users/gubatron/tmp/eztv4.html"));
         String fileStr = new String(readAllBytes,"utf-8");

         //Pattern pattern = Pattern.compile(REGEX);
         Pattern pattern = Pattern.compile(HTML_REGEX);

         Matcher matcher = pattern.matcher(fileStr);

         int found = 0;
         while (matcher.find()) {
         found++;
         System.out.println("\nfound " + found);
         System.out.println("displayname: " + matcher.group("displayname"));
         System.out.println("infohash: " + matcher.group("infohash"));
         System.out.println("torrenturl: " + matcher.group("torrenturl"));
         System.out.println("creationtime: " + matcher.group("creationtime"));
         System.out.println("filesize: " + matcher.group("filesize"));
         System.out.println("===");
         }
         //System.out.println("-done-");
         */
    }

    private static boolean testOnSearchResults(List<KATSearchResult> results) throws IOException {
        final boolean failed = true;
        for (KATSearchResult result : results) {
            System.out.println(result.getDetailsUrl());
            System.out.println(result.getTorrentUrl());
            System.out.println(result.getDisplayName());
            System.out.println(result.getFilename());
            System.out.println(result.getSource());
            System.out.println();
            String torrentFileName = result.getFilename().replace("\\s", "_") + ".txt";
            //torcache.net's landing page can be removed by passing an Http Referrer equal to "https://torcache.net/"
            System.out.println("Connecting to " + result.getTorrentUrl());
            getHttpClient().save(result.getTorrentUrl(), new File(torrentFileName), false, 10000, "", "https://torcache.net/");
            File savedTorrent = new File(torrentFileName);
            if (savedTorrent.exists() && savedTorrent.length() > 0 && FileUtils.readFileToString(savedTorrent).contains("<html")) {
                return failed;
            }

            System.out.println("Saved .torrent -> " + savedTorrent.getAbsolutePath());
            System.out.println("============================\n");
        }

        return !failed;
    }

    private static HttpClient getHttpClient() {
        return HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
    }
}