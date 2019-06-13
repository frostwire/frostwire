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

package com.frostwire.search;

import com.frostwire.search.filter.SearchTable;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.ThreadPool;

import java.lang.ref.WeakReference;
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
public final class SearchManager {
    private static final Logger LOG = Logger.getLogger(SearchManager.class);
    private final ExecutorService executor;
    private final List<SearchTask> tasks;
    private final List<WeakReference<SearchTable>> tables;
    private SearchListener listener;

    private SearchManager(int nThreads) {
        this.executor = new ThreadPool("SearchManager", 2, nThreads, 5000L, new PriorityBlockingQueue<>(), true);
        this.tasks = Collections.synchronizedList(new LinkedList<>());
        this.tables = Collections.synchronizedList(new LinkedList<>());
    }

    public static SearchManager getInstance() {
        return Loader.INSTANCE;
    }

    public void perform(final SearchPerformer performer) {
        if (performer != null) {
            if (performer.getToken() < 0) {
                throw new IllegalArgumentException("Search token id must be >= 0");
            }
            performer.setListener(new SearchListener() {
                @Override
                public void onResults(long token, List<? extends SearchResult> results) {
                    if (performer.getToken() == token) {
                        SearchManager.this.onResults(performer, results);
                    } else {
                        LOG.warn("Performer token does not match listener onResults token, review your logic");
                    }
                }

                @Override
                public void onError(long token, SearchError error) {
                    SearchManager.this.onError(token, error);
                }

                @Override
                public void onStopped(long token) {
                    // nothing since this is calculated in aggregation
                }
            });
            SearchTask task = new PerformTask(this, performer, nextOrdinal(performer.getToken()));
            submit(task);
        } else {
            LOG.warn("Search performer is null, review your logic");
        }
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
        List<SearchResult> list = new LinkedList<>();
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
            synchronized (tables) {
                Iterator<WeakReference<SearchTable>> it = tables.iterator();
                while (it.hasNext()) {
                    WeakReference<SearchTable> t = it.next();
                    if (Ref.alive(t)) {
                        //noinspection ConstantConditions
                        t.get().add(results);
                    } else {
                        it.remove();
                    }
                }
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
            for (SearchTask task : tasks) {
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
            for (SearchTask task : tasks) {
                if (task.token() == token) {
                    ordinal = ordinal + 1;
                }
            }
        }
        return ordinal;
    }

    private static class Loader {
        static final SearchManager INSTANCE = new SearchManager(8);
    }

    private static abstract class SearchTask extends Thread implements Comparable<SearchTask> {
        protected final SearchManager manager;
        final SearchPerformer performer;
        private final int ordinal;

        SearchTask(SearchManager manager, SearchPerformer performer, int ordinal) {
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

        void stopSearch() {
            performer.stop();
        }

        @Override
        public int compareTo(SearchTask o) {
            return Integer.compare(ordinal, o.ordinal);
        }
    }

    private static final class PerformTask extends SearchTask {
        PerformTask(SearchManager manager, SearchPerformer performer, int order) {
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

        CrawlTask(SearchManager manager, SearchPerformer performer, CrawlableSearchResult sr, int order) {
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
