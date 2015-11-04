/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 
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
import rx.Observable;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author gubatron
 * @author aldenml
 */
public class SearchManagerImpl implements SearchManager {

    private static final Logger LOG = Logger.getLogger(SearchManagerImpl.class);

    private static final int DEFAULT_NTHREADS = 6;

    private final ExecutorService executor;
    private final List<SearchTask> tasks;
    private final PublishSubject<SearchManagerSignal> subject;

    public SearchManagerImpl(int nThreads) {
        this.executor = new ThreadPool("SearchManager", nThreads, nThreads, 1L, new PriorityBlockingQueue<Runnable>(), true);
        this.tasks = Collections.synchronizedList(new LinkedList<SearchTask>());
        this.subject = PublishSubject.create();
    }

    public SearchManagerImpl() {
        this(DEFAULT_NTHREADS);
    }

    @Override
    public Observable<SearchManagerSignal> observable() {
        return subject;
    }

    @Override
    public void perform(final SearchPerformer performer) {
        if (performer != null) {
            if (performer.getToken() < 0) {
                throw new IllegalArgumentException("Search token id must be >= 0");
            }

            performer.observable().subscribe(new Action1<List<? extends SearchResult>>() {
                // NOTE: This is what's called when we do subject.onNext(List<>)
                @Override
                public void call(List<? extends SearchResult> results) {
                    performerOnResults(performer, results);
                }
            });

            SearchTask task = new PerformTask(this, performer, getOrder(performer.getToken()));

            submitSearchTask(task);
        } else {
            LOG.warn("Search performer is null, review your logic");
        }
    }

    public void submitSearchTask(SearchTask task) {
        tasks.add(task);
        executor.execute(task);
    }

    @Override
    public void stop() {
        stopTasks(-1L);
    }

    @Override
    public void stop(long token) {
        stopTasks(token);
    }

    @Override
    public boolean shutdown(long timeout, TimeUnit unit) {
        stop();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
                // wait a while for tasks to respond to being cancelled
                if (!executor.awaitTermination(timeout, unit)) {
                    LOG.error("Pool did not terminate");
                    return false;
                }
            }
        } catch (InterruptedException ie) {
            // (re-)cancel if current thread also interrupted
            executor.shutdownNow();
            // preserve interrupt status
            Thread.currentThread().interrupt();
        }

        return tasks.isEmpty();
    }

    protected void onResults(SearchPerformer performer, List<? extends SearchResult> results) {
        try {
            if (results != null) {
                subject.onNext(new SearchManagerSignal.Results(performer.getToken(), results));
            }
        } catch (Throwable e) {
            LOG.warn("Error sending results back to receiver: " + e.getMessage(), e);
        }
    }

    protected void onFinished(long token) {
        try {
            subject.onNext(new SearchManagerSignal.End(token));
        } catch (Throwable e) {
            LOG.warn("Error sending results back to receiver: " + e.getMessage(), e);
        }
    }

    private void crawl(SearchPerformer performer, CrawlableSearchResult sr) {
        if (performer != null && !performer.isStopped()) {
            try {
                SearchTask task = new CrawlTask(this, performer, sr, getOrder(performer.getToken()));
                submitSearchTask(task);
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
                if (token == -1L || task.getToken() == token) {
                    task.stopSearch();
                }
            }
        }
    }

    private void checkIfFinished(SearchPerformer performer) {
        SearchTask pendingTask = null;

        synchronized (tasks) {
            Iterator<SearchTask> it = tasks.iterator();
            while (it.hasNext() && pendingTask == null) {
                SearchTask task = it.next();
                if (task.getToken() == performer.getToken() && !task.isStopped()) {
                    pendingTask = task;
                }

                if (task.isStopped()) {
                    it.remove();
                }
            }
        }

        if (pendingTask == null) {
            onFinished(performer.getToken());
        }
    }

    private int getOrder(long token) {
        int order = 0;
        synchronized (tasks) {
            Iterator<SearchTask> it = tasks.iterator();
            while (it.hasNext()) {
                SearchTask task = it.next();
                if (task.getToken() == token) {
                    order = order + 1;
                }
            }
        }
        return order;
    }

    private void performerOnResults(SearchPerformer performer, List<? extends SearchResult> results) {
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
            onResults(performer, list);
        }
    }

    private static abstract class SearchTask extends Thread implements Comparable<SearchTask> {

        protected final SearchManagerImpl manager;
        protected final SearchPerformer performer;
        private final int order;

        public SearchTask(SearchManagerImpl manager, SearchPerformer performer, int order) {
            this.manager = manager;
            this.performer = performer;
            this.order = order;
            this.setName(performer.getClass().getName() + "-SearchTask");
        }

        public long getToken() {
            return performer.getToken();
        }

        public boolean isStopped() {
            return performer.isStopped();
        }

        public void stopSearch() {
            performer.stop();
        }

        @Override
        public int compareTo(SearchTask o) {
            return order - o.order;
        }
    }

    private static final class PerformTask extends SearchTask {

        public PerformTask(SearchManagerImpl manager, SearchPerformer performer, int order) {
            super(manager, performer, order);
        }

        @Override
        public void run() {
            try {
                if (!isStopped()) {
                    performer.perform();
                }
            } catch (Throwable e) {
                LOG.warn("Error performing search: " + performer + ", e=" + e.getMessage());
            } finally {
                if (manager.tasks.remove(this)) {
                    manager.checkIfFinished(performer);
                }
            }
        }
    }

    private static final class CrawlTask extends SearchTask {

        private final CrawlableSearchResult sr;

        public CrawlTask(SearchManagerImpl manager, SearchPerformer performer, CrawlableSearchResult sr, int order) {
            super(manager, performer, order);
            this.sr = sr;
        }

        @Override
        public void run() {
            try {
                if (!isStopped()) {
                    performer.crawl(sr);
                }
            } catch (Throwable e) {
                LOG.warn("Error performing crawling of: " + sr + ", e=" + e.getMessage());
            } finally {
                if (manager.tasks.remove(this)) {
                    manager.checkIfFinished(performer);
                }
            }
        }
    }
}
