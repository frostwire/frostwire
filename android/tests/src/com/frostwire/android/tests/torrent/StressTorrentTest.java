/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(R). All rights reserved.
 
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

package com.frostwire.android.tests.torrent;

import java.io.ByteArrayInputStream;
import java.util.List;

import junit.framework.TestCase;
import android.test.suitebuilder.annotation.LargeTest;

import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.extratorrent.ExtratorrentSearchPerformer;
import com.frostwire.search.kat.KATSearchPerformer;
import com.frostwire.search.mininova.MininovaSearchPerformer;
import com.frostwire.search.torrent.TorrentSearchPerformer;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.torrent.TOTorrent;
import com.frostwire.torrent.TOTorrentException;
import com.frostwire.torrent.TorrentUtils;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class StressTorrentTest extends TestCase {

    @LargeTest
    public void testDownloadFromExtratorrent() {
        ExtratorrentSearchPerformer p = new ExtratorrentSearchPerformer(null, System.currentTimeMillis(), "public domain", 10000);
        testDownloadFrom(p);
    }

    @LargeTest
    public void testDownloadFromKAT() {
        KATSearchPerformer p = new KATSearchPerformer(null, System.currentTimeMillis(), "public domain", 10000);
        testDownloadFrom(p);
    }

    @LargeTest
    public void testDownloadFromMininova() {
        MininovaSearchPerformer p = new MininovaSearchPerformer(null, System.currentTimeMillis(), "mp3", 10000);
        testDownloadFrom(p);
    }

    private void testDownloadFrom(final TorrentSearchPerformer p) {
        p.registerListener(new SearchListener() {
            @Override
            public void onResults(SearchPerformer performer, List<? extends SearchResult> results) {
                for (SearchResult sr : results) {
                    if (sr instanceof TorrentSearchResult) {
                        TorrentSearchResult tsr = (TorrentSearchResult) sr;
                        String url = tsr.getTorrentUrl();
                        byte[] data = p.fetchBytes(url);
                        ByteArrayInputStream is = new ByteArrayInputStream(data);
                        try {
                            TOTorrent t = TorrentUtils.readFromBEncodedInputStream(is);

                            assertNotNull(t.getName());
                            assertNotNull(t.getPieces());
                            assertTrue(t.getPieceLength() > 0);

                            System.out.println("Parsed: " + url);

                        } catch (TOTorrentException e) {
                            assertTrue("Exception for torrent: " + url, false);
                        }
                    }
                }
            }
        });

        p.perform();
    }
}
