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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManagerImpl;
import com.frostwire.search.SearchPerformer;
import com.frostwire.search.SearchResult;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public class SearchTest2 extends TestCase {

    private List<SearchResult> results;

    @Override
    protected void setUp() throws Exception {
        results = new ArrayList<SearchResult>();
        results.add(newIntegerSearchResult(1));
        results.add(newIntegerSearchResult(2));
        results.add(newIntegerSearchResult(3));
        results.add(newIntegerSearchResult(4));
        results.add(newIntegerSearchResult(5));
    }

    public void testFixedNumberResults() {
        MockSearchResultListener l = new MockSearchResultListener();

        SearchManagerImpl manager = new SearchManagerImpl();
        manager.registerListener(l);
        manager.perform(new SearchPerformer() {

            private SearchListener listener;

            @Override
            public long getToken() {
                return 0;
            }

            @Override
            public void registerListener(SearchListener listener) {
                this.listener = listener;
            }

            @Override
            public void perform() {
                listener.onResults(this, results);
            }

            @Override
            public void crawl(CrawlableSearchResult sr) {
            }

            @Override
            public void stop() {
            }

            @Override
            public boolean isStopped() {
                return false;
            }
        });

        assertTrue("Did not finish or took too much time", manager.shutdown(5, TimeUnit.SECONDS));

        assertEquals(results.size(), l.getNumResults());
    }

    public void testPerformFinished() {
        MockSearchResultListener l = new MockSearchResultListener();

        SearchManagerImpl manager = new SearchManagerImpl();
        manager.registerListener(l);
        manager.perform(new SearchPerformer() {

            @Override
            public long getToken() {
                return 0;
            }

            @Override
            public void registerListener(SearchListener listener) {
            }

            @Override
            public void perform() {
                // not calling finished here
            }

            @Override
            public void crawl(CrawlableSearchResult sr) {
            }

            @Override
            public void stop() {
            }

            @Override
            public boolean isStopped() {
                return false;
            }
        });

        assertTrue("Did not finish or took too much time", manager.shutdown(5, TimeUnit.SECONDS));
    }

    @MediumTest
    public void testStopWithFastPerformers() {
        runStopWithPerformers(10, 0);
    }

    @MediumTest
    public void testStopWithMediumFastPerformers() {
        runStopWithPerformers(10, 2000);
    }

    @MediumTest
    public void testStopWithSlowPerformers() {
        runStopWithPerformers(10, 20000);
    }

    private void runStopWithPerformers(int n, long timeMillis) {
        List<SearchPerformer> performers = createTimedPerformers(10, 2000);

        MockSearchResultListener l = new MockSearchResultListener();

        SearchManagerImpl manager = new SearchManagerImpl();
        manager.registerListener(l);

        for (SearchPerformer performer : performers) {
            manager.perform(performer);
        }

        assertTrue("Did not finish or took too much time", manager.shutdown(5, TimeUnit.SECONDS));
    }

    private List<SearchPerformer> createTimedPerformers(int n, final long timeMillis) {
        List<SearchPerformer> performers = new LinkedList<SearchPerformer>();

        for (int i = 0; i < n; i++) {
            performers.add(new SearchPerformer() {

                private Object sync = new Object();

                @Override
                public long getToken() {
                    return 0;
                }

                @Override
                public void registerListener(SearchListener listener) {
                }

                @Override
                public void perform() {
                    try {
                        sync.wait(timeMillis);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }

                @Override
                public void crawl(CrawlableSearchResult sr) {
                }

                @Override
                public void stop() {
                    synchronized (sync) {
                        sync.notifyAll();
                    }
                }

                @Override
                public boolean isStopped() {
                    return false;
                }
            });
        }

        return performers;
    }

    private SearchResult newIntegerSearchResult(final int n) {
        return new MockSearchResult() {
            @Override
            public String toString() {
                return String.valueOf(n);
            }
        };
    }
}
