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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

import android.test.ApplicationTestCase;
import android.test.mock.MockApplication;
import android.test.suitebuilder.annotation.LargeTest;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.util.SystemUtils;
import com.frostwire.android.tests.TestUtils;
import com.frostwire.android.tests.TorrentUrls;
import com.frostwire.logging.Logger;
import com.frostwire.torrent.TOTorrent;
import com.frostwire.torrent.TOTorrentException;
import com.frostwire.torrent.TorrentUtils;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.vuze.VuzeConfiguration;
import com.frostwire.vuze.VuzeDownloadFactory;
import com.frostwire.vuze.VuzeDownloadListener;
import com.frostwire.vuze.VuzeDownloadManager;
import com.frostwire.vuze.VuzeManager;
import com.frostwire.vuze.VuzeUtils;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class VuzeDownloadTest extends ApplicationTestCase<MockApplication> {

    private static final Logger LOG = Logger.getLogger(VuzeDownloadTest.class);

    public VuzeDownloadTest() {
        this(MockApplication.class);
    }

    public VuzeDownloadTest(Class<MockApplication> applicationClass) {
        super(applicationClass);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        ConfigurationManager.create(getApplication());

        String azureusPath = SystemUtils.getAzureusDirectory().getAbsolutePath();
        String torrentsPath = SystemUtils.getTorrentsDirectory().getAbsolutePath();
        VuzeConfiguration conf = new VuzeConfiguration(azureusPath, torrentsPath, null);
        VuzeManager.setConfiguration(conf);
    }

    @LargeTest
    public void testDownload1() throws IOException, TOTorrentException {

        HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);

        File torrentFile = new File(SystemUtils.getTorrentsDirectory(), "download_test1.torrent");
        File saveDir = SystemUtils.getTorrentDataDirectory();
        c.save(TorrentUrls.FROSTCLICK_BRANDON_HINES_2010, torrentFile);

        TOTorrent t = readTorrent(torrentFile.getAbsolutePath());

        VuzeUtils.remove(t.getHash(), true);
        TestUtils.sleep(10000);

        final CountDownLatch signal = new CountDownLatch(1);

        VuzeDownloadManager dm = VuzeDownloadFactory.create(torrentFile.getAbsolutePath(), null, saveDir.getAbsolutePath(), new VuzeDownloadListener() {

            @Override
            public void stateChanged(VuzeDownloadManager dm, int state) {
                LOG.info("testDownload1-stateChanged:" + formatDownloadState(state));
            }

            @Override
            public void downloadComplete(VuzeDownloadManager dm) {
                signal.countDown();
            }
        });

        assertNotNull(dm);

        assertTrue("Download not finished", TestUtils.await(signal, 1, TimeUnit.HOURS));
    }

    private static TOTorrent readTorrent(String torrent) throws IOException {
        InputStream is = null;

        try {
            is = new FileInputStream(torrent);

            return TorrentUtils.readFromBEncodedInputStream(is);

        } catch (TOTorrentException e) {
            throw new IOException("Unable to read the torrent", e);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    private static String formatDownloadState(int state) {
        switch (state) {
        case VuzeDownloadManager.STATE_WAITING:
            return "Waiting";
        case VuzeDownloadManager.STATE_INITIALIZING:
            return "Initializing";
        case VuzeDownloadManager.STATE_INITIALIZED:
            return "Initialized";
        case VuzeDownloadManager.STATE_ALLOCATING:
            return "Allocating";
        case VuzeDownloadManager.STATE_CHECKING:
            return "Checking";
        case VuzeDownloadManager.STATE_READY:
            return "Ready";
        case VuzeDownloadManager.STATE_DOWNLOADING:
            return "Downloading";
        case VuzeDownloadManager.STATE_FINISHING:
            return "Finishing";
        case VuzeDownloadManager.STATE_SEEDING:
            return "Seeding";
        case VuzeDownloadManager.STATE_STOPPING:
            return "Stopping";
        case VuzeDownloadManager.STATE_STOPPED:
            return "Stopped";
        case VuzeDownloadManager.STATE_CLOSED:
            return "Closed";
        case VuzeDownloadManager.STATE_QUEUED:
            return "Queued";
        case VuzeDownloadManager.STATE_ERROR:
            return "Error";
        default:
            return "Unknown";
        }
    }
}
