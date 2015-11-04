/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 
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

package com.frostwire.android.tests.vuze;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import android.test.suitebuilder.annotation.LargeTest;

import com.frostwire.android.gui.util.SystemUtils;
import com.frostwire.android.tests.TestUtils;
import com.frostwire.android.tests.TorrentUrls;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.vuze.VuzeDownloadFactory;
import com.frostwire.vuze.VuzeDownloadListener;
import com.frostwire.vuze.VuzeDownloadManager;
import com.frostwire.vuze.VuzeUtils;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class CreateDownloadTest extends TestCase {

    @LargeTest
    public void testDownload1() throws IOException {

        HttpClient c = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);

        File torrentFile = new File(SystemUtils.getTorrentsDirectory(), "create_download_test1.torrent");
        File saveDir = SystemUtils.getTorrentDataDirectory();
        c.save(TorrentUrls.FROSTCLICK_BRANDON_HINES_2010, torrentFile);

        final CountDownLatch signal = new CountDownLatch(1);

        VuzeDownloadManager dm = VuzeDownloadFactory.create(torrentFile.getAbsolutePath(), null, saveDir.getAbsolutePath(), new VuzeDownloadListener() {

            @Override
            public void stateChanged(VuzeDownloadManager dm, int state) {
                if (state == VuzeDownloadManager.STATE_STOPPED) {
                    signal.countDown();
                }
            }

            @Override
            public void downloadComplete(VuzeDownloadManager dm) {
            }
        });

        assertNotNull(dm);

        VuzeUtils.remove(dm, true);

        assertTrue("Unable to remove the download", TestUtils.await(signal, 10, TimeUnit.SECONDS));
    }
}
