/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 
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

package com.frostwire.search;

import com.frostwire.logging.Logger;
import com.frostwire.util.ThreadPool;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchManager2 {

    private static final Logger LOG = Logger.getLogger(SearchManager2.class);

    private final ExecutorService executor;
    private final List<SearchTask> tasks;

    private SearchListener listener;

    private SearchManager2(int nThreads) {
        this.executor = new ThreadPool("SearchManager", nThreads, nThreads, 1L, new PriorityBlockingQueue<Runnable>(), true);
        this.tasks = Collections.synchronizedList(new LinkedList<SearchTask>());
    }

    private static class Loader {
        static final SearchManager2 INSTANCE = new SearchManager2(6);
    }

    public static SearchManager2 getInstance() {
        return Loader.INSTANCE;
    }

    public void perform(final SearchPerformer performer) {
        if (performer == null) {
            throw new IllegalArgumentException("Search performer argument can't be null");
        }
        if (performer.getToken() < 0) {
            throw new IllegalArgumentException("Search token id must be >= 0");
        }

        performer.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                if (performer.getToken() == token) {
                    SearchManager2.this.onResults(performer, results);
                } else {
                    LOG.warn("Performer token does not match listener onResults token, review your logic");
                }
            }

            @Override
            public void onError(long token, SearchError error) {
                SearchManager2.this.onError(token, error);
            }

            @Override
            public void onStopped(long token) {
                // nothing since this is calculated in aggregation
            }
        });

        SearchTask task = new PerformTask(this, performer, nextOrdinal(performer.getToken()));
        submit(task);
    }

    public void stop() {
        stopTasks(-1L);
    }

    public void stop(long token) {
        stopTasks(token);
    }

    public SearchListener getListener() {
        return listener;
    }

    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    private void submit(SearchTask task) {
        tasks.add(task);
        executor.execute(task);
    }

    private void onResults(SearchPerformer performer, List<? extends SearchResult> results) {
        List<SearchResult> list = new LinkedList<SearchResult>();

        for (SearchResult sr : results) {
            if (sr instanceof CrawlableSearchResult) {
                CrawlableSearchResult csr = (CrawlableSearchResult) sr;

                if (csr.isComplete()) {
                    list.add(sr);
                }

                crawl(performer, csr);
            } else {
                list.add(sr);
            }
        }

        if (!list.isEmpty()) {
            onResults(performer.getToken(), list);
        }
    }

    private void onResults(long token, List<? extends SearchResult> results) {
        try {
            if (results != null && listener != null) {
                listener.onResults(token, results);
            }
        } catch (Throwable e) {
            LOG.warn("Error sending results to listener: " + e.getMessage(), e);
        }
    }

    private void onError(long token, SearchError error) {
        try {
            if (error != null && listener != null) {
                listener.onError(token, error);
            }
        } catch (Throwable e) {
            LOG.warn("Error sending search error to listener: " + e.getMessage(), e);
        }
    }

    private void onStopped(long token) {
        try {
            if (listener != null) {
                listener.onStopped(token);
            }
        } catch (Throwable e) {
            LOG.warn("Error sending stopped signal to listener: " + e.getMessage(), e);
        }
    }

    private void crawl(SearchPerformer performer, CrawlableSearchResult sr) {
        if (performer != null && !performer.isStopped()) {
            try {
                SearchTask task = new CrawlTask(this, performer, sr, nextOrdinal(performer.getToken()));
                submit(task);
            } catch (Throwable e) {
                LOG.warn("Error scheduling crawling of search result: " + sr);
            }
        } else {
            LOG.warn("Search performer is null or stopped, review your logic");
        }
    }

    private void stopTasks(long token) {
        synchronized (tasks) {
            Iterator<SearchTask> it = tasks.iterator();
            while (it.hasNext()) {
                SearchTask task = it.next();
                if (token == -1L || task.token() == token) {
                    task.stopSearch();
                }
            }
        }
    }

    private void checkIfFinished(long token) {
        SearchTask pendingTask = null;

        synchronized (tasks) {
            Iterator<SearchTask> it = tasks.iterator();
            while (it.hasNext() && pendingTask == null) {
                SearchTask task = it.next();
                if (task.token() == token && !task.stopped()) {
                    pendingTask = task;
                }

                if (task.stopped()) {
                    it.remove();
                }
            }
        }

        if (pendingTask == null) {
            onStopped(token);
        }
    }

    private int nextOrdinal(long token) {
        int ordinal = 0;
        synchronized (tasks) {
            Iterator<SearchTask> it = tasks.iterator();
            while (it.hasNext()) {
                SearchTask task = it.next();
                if (task.token() == token) {
                    ordinal = ordinal + 1;
                }
            }
        }
        return ordinal;
    }

    private static abstract class SearchTask extends Thread implements Comparable<SearchTask> {

        protected final SearchManager2 manager;
        protected final SearchPerformer performer;
        private final int ordinal;

        public SearchTask(SearchManager2 manager, SearchPerformer performer, int ordinal) {
            this.manager = manager;
            this.performer = performer;
            this.ordinal = ordinal;
            this.setName(performer.getClass().getName() + "-SearchTask");
        }

        public long token() {
            return performer.getToken();
        }

        public boolean stopped() {
            return performer.isStopped();
        }

        public void stopSearch() {
            performer.stop();
        }

        @Override
        public int compareTo(SearchTask o) {
            int x = ordinal;
            int y = o.ordinal;
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }
    }

    private static final class PerformTask extends SearchTask {

        public PerformTask(SearchManager2 manager, SearchPerformer performer, int order) {
            super(manager, performer, order);
        }

        @Override
        public void run() {
            try {
                if (!stopped()) {
                    performer.perform();
                }
            } catch (Throwable e) {
                LOG.warn("Error performing search: " + performer + ", e=" + e.getMessage());
            } finally {
                if (manager.tasks.remove(this)) {
                    manager.checkIfFinished(performer.getToken());
                }
            }
        }
    }

    private static final class CrawlTask extends SearchTask {

        private final CrawlableSearchResult sr;

        public CrawlTask(SearchManager2 manager, SearchPerformer performer, CrawlableSearchResult sr, int order) {
            super(manager, performer, order);
            this.sr = sr;
        }

        @Override
        public void run() {
            try {
                if (!stopped()) {
                    performer.crawl(sr);
                }
            } catch (Throwable e) {
                LOG.warn("Error performing crawling of: " + sr + ", e=" + e.getMessage());
            } finally {
                if (manager.tasks.remove(this)) {
                    manager.checkIfFinished(performer.getToken());
                }
            }
        }
    }
}
