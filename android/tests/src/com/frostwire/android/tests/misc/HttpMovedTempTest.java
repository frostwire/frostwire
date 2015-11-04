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

package com.frostwire.android.tests.misc;

import java.io.ByteArrayInputStream;

import com.frostwire.torrent.TOTorrent;
import com.frostwire.torrent.TOTorrentException;
import com.frostwire.torrent.TorrentUtils;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.HttpClientFactory;

import junit.framework.TestCase;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class HttpMovedTempTest extends TestCase{
    

    public void testFWClientOnMovedTempTest() {
        HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
        byte[] result = client.getBytes("http://extratorrent.com/download/-------/",5000,"Internet Xploder",null);
        assertNotNull(result);
        assertTrue(result.length > 0);
        System.out.println(result);
        
        TOTorrent torrent = null;
        try {
            torrent = TorrentUtils.readFromBEncodedInputStream(new ByteArrayInputStream(result));
        } catch (TOTorrentException e) {
        }
        assertNotNull(torrent);
    }

}