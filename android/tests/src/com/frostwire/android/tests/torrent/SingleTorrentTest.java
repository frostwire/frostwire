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

import junit.framework.TestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.frostwire.torrent.TOTorrent;
import com.frostwire.torrent.TOTorrentException;
import com.frostwire.torrent.TorrentUtils;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.UserAgentGenerator;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class SingleTorrentTest extends TestCase {

    @MediumTest
    public void testFromUrl1() {
        testDownloadFrom("http://www.mininova.org/tor/3191902/0"); // good torrent
    }

    public void testDownloadFrom(String url) {
        HttpClient c = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
        byte[] data = c.getBytes(url, 10000, UserAgentGenerator.getUserAgent(), null);
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
