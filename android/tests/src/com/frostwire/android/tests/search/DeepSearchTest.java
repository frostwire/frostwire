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

package com.frostwire.android.tests.search;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.test.ApplicationTestCase;
import android.test.mock.MockApplication;
import android.test.suitebuilder.annotation.LargeTest;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.util.SystemUtils;
import com.frostwire.android.tests.TestUtils;
import com.frostwire.search.SearchManagerImpl;
import com.frostwire.search.SearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.archiveorg.ArchiveorgSearchPerformer;
import com.frostwire.search.extratorrent.ExtratorrentSearchPerformer;
import com.frostwire.vuze.VuzeConfiguration;
import com.frostwire.vuze.VuzeManager;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class DeepSearchTest extends ApplicationTestCase<MockApplication> {

    public DeepSearchTest() {
        this(MockApplication.class);
    }

    public DeepSearchTest(Class<MockApplication> applicationClass) {
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
    public void testDeepSearchExtratorrent() {
        deepSearch(new ExtratorrentSearchPerformer(null, 0, "frostclick", 5000));
    }

    @LargeTest
    public void testDeepSearchArchiveorg() {
        deepSearch(new ArchiveorgSearchPerformer(null, 0, "Big Buck Bunny", 5000));
    }

    private void deepSearch(SearchPerformer performer) {
        final CountDownLatch signal = new CountDownLatch(1);

        MockSearchResultListener l = new MockSearchResultListener() {
            @Override
            public void onResults(SearchPerformer performer, List<? extends SearchResult> results) {
                super.onResults(performer, results);
                if (containsDeepSearchResult()) {
                    signal.countDown();
                }
            }
        };

        SearchManagerImpl manager = new SearchManagerImpl();
        manager.registerListener(l);
        manager.perform(performer);

        assertTrue("Unable to get crawled results in less than 10 seconds", TestUtils.await(signal, 10, TimeUnit.SECONDS));

        assertTrue("Did not finish or took too much time", manager.shutdown(1, TimeUnit.MINUTES));

        assertTrue("Didn't get a crawled search result", l.containsDeepSearchResult());

        l.logResults();
    }
}
