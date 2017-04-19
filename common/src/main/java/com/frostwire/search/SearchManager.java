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

import com.frostwire.platform.AppSettings;
import com.frostwire.search.archiveorg.ArchiveorgSearchPerformer;
import com.frostwire.search.extratorrent.ExtratorrentSearchPerformer;
import com.frostwire.search.eztv.EztvSearchPerformer;
import com.frostwire.search.filter.SearchTable;
import com.frostwire.search.frostclick.FrostClickSearchPerformer;
import com.frostwire.search.limetorrents.LimeTorrentsSearchPerformer;
import com.frostwire.search.monova.MonovaSearchPerformer;
import com.frostwire.search.soundcloud.SoundcloudSearchPerformer;
import com.frostwire.search.torlock.TorLockSearchPerformer;
import com.frostwire.search.torrentdownloads.TorrentDownloadsSearchPerformer;
import com.frostwire.search.tpb.TPBSearchPerformer;
import com.frostwire.search.yify.YifySearchPerformer;
import com.frostwire.search.youtube.YouTubeSearchPerformer;
import com.frostwire.search.zooqle.ZooqleSearchPerformer;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.ThreadPool;

import java.lang.ref.WeakReference;
import java.util.*;
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
        this.executor = new ThreadPool("SearchManager", nThreads, nThreads, 1L, new PriorityBlockingQueue<Runnable>(), true);
        this.tasks = Collections.synchronizedList(new LinkedList<SearchTask>());
        this.tables = Collections.synchronizedList(new LinkedList<WeakReference<SearchTable>>());
    }

    private static class Loader {
        static final SearchManager INSTANCE = new SearchManager(6);
    }

    public static SearchManager getInstance() {
        return Loader.INSTANCE;
    }

    public void perform(final SearchPerformer performer) {
//        if (performer == null) {
//            throw new IllegalArgumentException("Search performer argument can't be null");
//        }
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

    private static abstract class SearchTask extends Thread implements Comparable<SearchTask> {

        protected final SearchManager manager;
        protected final SearchPerformer performer;
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
            int x = ordinal;
            int y = o.ordinal;
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
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

    // search engines

    private static final int DEFAULT_SEARCH_PERFORMER_TIMEOUT = 10000;

    private static final SearchEngine EXTRATORRENT = new SearchEngine("Extratorrent", AppSettings.SEARCH_EXTRATORRENT_ENABLED) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new ExtratorrentSearchPerformer("extratorrent.cc", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    private static final SearchEngine YOUTUBE = new SearchEngine("YouTube", AppSettings.SEARCH_YOUTUBE_ENABLED) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new YouTubeSearchPerformer("www.youtube.com", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    private static final SearchEngine SOUNCLOUD = new SearchEngine("Soundcloud", AppSettings.SEARCH_SOUNDCLOUD_ENABLED) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new SoundcloudSearchPerformer("api.sndcdn.com", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    private static final SearchEngine ARCHIVE = new SearchEngine("Archive", AppSettings.SEARCH_ARCHIVE_ENABLED) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new ArchiveorgSearchPerformer("archive.org", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    private static final SearchEngine FROSTCLICK = new SearchEngine("FrostClick", AppSettings.SEARCH_FROSTCLICK_ENABLED) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new FrostClickSearchPerformer("api.frostclick.com", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT, null);
        }
    };

    private static final SearchEngine TORLOCK = new SearchEngine("TorLock", AppSettings.SEARCH_TORLOCK_ENABLED) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new TorLockSearchPerformer("www.torlock.com", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    private static final SearchEngine TORRENTDOWNLOADS = new SearchEngine("TorrentDownloads", AppSettings.SEARCH_TORRENTDOWNLOADS_ENABLED) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new TorrentDownloadsSearchPerformer("www.torrentdownloads.me", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    static final SearchEngine LIMETORRENTS = new SearchEngine("LimeTorrents", AppSettings.SEARCH_LIMETORRENTS_ENABLED) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new LimeTorrentsSearchPerformer("www.limetorrents.cc", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    private static final SearchEngine EZTV = new SearchEngine("Eztv", AppSettings.SEARCH_EZTV_ENABLED) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new EztvSearchPerformer("eztv.ag", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    private static final SearchEngine TPB = new SearchEngine("TPB", AppSettings.SEARCH_TBP_ENABLED, false) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new TPBSearchPerformer("thepiratebay.se", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    static final SearchEngine MONOVA = new SearchEngine("Monova", AppSettings.SEARCH_MONOVA_ENABLED, false) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new MonovaSearchPerformer("monova.org", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    private static final SearchEngine YIFY = new SearchEngine("Yify", AppSettings.SEARCH_YIFY_ENABLED, false) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new YifySearchPerformer("www.yify-torrent.org", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    static final SearchEngine ZOOQLE = new SearchEngine("Zooqle", AppSettings.SEARCH_ZOOQLE_ENABLED, false) {
        @Override
        public SearchPerformer newPerformer(long token, String keywords) {
            return new ZooqleSearchPerformer("zooqle.com", token, keywords, DEFAULT_SEARCH_PERFORMER_TIMEOUT);
        }
    };

    @SuppressWarnings("unused")
    private static final List<SearchEngine> ALL_ENGINES = Arrays.asList(EXTRATORRENT, YIFY, YOUTUBE, FROSTCLICK, MONOVA, ZOOQLE, TPB, SOUNCLOUD, ARCHIVE, TORLOCK, TORRENTDOWNLOADS, EZTV, LIMETORRENTS);
}
