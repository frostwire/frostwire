/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.search;

import com.frostwire.search.filter.SearchTable;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.ThreadPool;
import com.frostwire.util.http.OkHttpClientWrapper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchManager {
    private static final Logger LOG = Logger.getLogger(SearchManager.class);
    /**
     * Executor for one-off searches that don't need any crawling of results
     */
    private final ExecutorService singlePageRequestExecutor;
    /**
     * Executor for first search and crawls by Performers that need crawling
     */
    private final ExecutorService crawlingExecutor;
    private final List<SearchTask> tasks;
    private final List<WeakReference<SearchTable>> tables;
    private SearchListener listener;

    private SearchManager(int instantResultsThreads, int crawlResultsThreads) {
        LOG.info("SearchManager: instantResultsThreads: " + instantResultsThreads + " crawlExecutorThreads: " + crawlResultsThreads);
        this.singlePageRequestExecutor = new ThreadPool("SearchManager-executor", instantResultsThreads, instantResultsThreads, 2L, new LinkedBlockingQueue<>(), true);
        this.crawlingExecutor = new ThreadPool("SearchManager-crawlExecutor", crawlResultsThreads, crawlResultsThreads, 2L, new LinkedBlockingQueue<>(), true);
        // Pre-size: typical workload has ~100 concurrent tasks and ~10 tables
        this.tasks = Collections.synchronizedList(new ArrayList<>(128));
        this.tables = Collections.synchronizedList(new ArrayList<>(16));
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
            submitSimpleSearchTask(task, performer.isCrawler() ? crawlingExecutor : singlePageRequestExecutor);
        } else {
            LOG.warn("Search performer is null, review your logic");
        }
    }

    public void stop() {
        stopTasks();
        OkHttpClientWrapper.cancelAllRequests();
    }

    public SearchListener getListener() {
        return listener;
    }

    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    private void submitSimpleSearchTask(SearchTask task, ExecutorService executor) {
        tasks.add(task);
        executor.execute(task);
    }

    private void onResults(SearchPerformer performer, List<? extends SearchResult> results) {
        var list = new LinkedList<SearchResult>();
        results.forEach(sr -> {
            if (sr instanceof CrawlableSearchResult) {
                CrawlableSearchResult csr = (CrawlableSearchResult) sr;
                if (csr.isComplete()) {
                    list.add(sr);
                }
                crawl(performer, csr);
            } else {
                list.add(sr);
            }
        });
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
                CrawlTask task = new CrawlTask(this, performer, sr, nextOrdinal(performer.getToken()));
                tasks.add(task);
                crawlingExecutor.execute(task);
            } catch (Throwable e) {
                LOG.warn("Error scheduling crawling of search result: " + sr);
            }
        } else {
            LOG.warn("Search performer is null or stopped, review your logic");
        }
    }

    private void stopTasks() {
        synchronized (tasks) {
            for (SearchTask task : tasks) {
                task.stopSearch();
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
        static final SearchManager INSTANCE = new SearchManager(3, 6);
    }

    private static abstract class SearchTask implements Runnable, Comparable<SearchTask> {
        protected final SearchManager manager;
        final SearchPerformer performer;
        private final int ordinal;

        SearchTask(SearchManager manager, SearchPerformer performer, int ordinal) {
            this.manager = manager;
            this.performer = performer;
            this.ordinal = ordinal;
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
            Thread.currentThread().setName(performer.getClass().getName() + "-SearchTask");
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
            Thread.currentThread().setName(performer.getClass().getName() + "-CrawlTask");
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
