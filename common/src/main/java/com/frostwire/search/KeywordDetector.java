/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.

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

import com.frostwire.util.HistoHashMap;
import com.frostwire.util.Logger;
import com.frostwire.util.ThreadPool;

import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created on 11/26/2016
 *
 * @author gubatron
 * @author aldenml
 */
public final class KeywordDetector {

    public enum Feature {
        SEARCH_SOURCE(0.015f, 4, 20),
        FILE_EXTENSION(0f, 3, 8),
        FILE_NAME(0.01f, 3, 20),
        MANUAL_ENTRY(0, 2, 20);

        public final float filterThreshold;
        public final int minimumTokenLength;
        public final int maximumTokenLength;

        Feature(float filterThreshold, int minimumTokenLength, int maximumTokenLength) {
            this.filterThreshold = filterThreshold;
            this.minimumTokenLength = minimumTokenLength;
            this.maximumTokenLength = maximumTokenLength;
        }
    }

    private static Logger LOG = Logger.getLogger(KeywordDetector.class);
    private static final Set<String> stopWords = new HashSet<>();
    private final Map<Feature, HistoHashMap<String>> histograms;
    private KeywordDetectorListener keywordDetectorListener;
    private final HistogramUpdateRequestDispatcher histogramUpdateRequestsDispatcher;
    private ExecutorService threadPool;

    public KeywordDetector() {
        histograms = new HashMap<>();
        histogramUpdateRequestsDispatcher = new HistogramUpdateRequestDispatcher();
        histograms.put(Feature.SEARCH_SOURCE, new HistoHashMap<String>());
        histograms.put(Feature.FILE_EXTENSION, new HistoHashMap<String>());
        histograms.put(Feature.FILE_NAME, new HistoHashMap<String>());
    }

    public int totalHistogramKeys() {
        return histograms.get(Feature.SEARCH_SOURCE).getKeyCount() +
                histograms.get(Feature.FILE_EXTENSION).getKeyCount() +
                histograms.get(Feature.FILE_NAME).getKeyCount();
    }

    public void notifyKeywordDetectorListener() {
        if (this.keywordDetectorListener != null) {
            this.keywordDetectorListener.notify(this, histograms);
        }
    }

    public void setKeywordDetectorListener(KeywordDetectorListener listener) {
        this.keywordDetectorListener = listener;
    }

    public void addSearchTerms(Feature feature, String terms) {
        // tokenize
        String[] pre_tokens = terms.replaceAll("[^a-zA-Z0-9\\p{L}[.]{1} ]", "").toLowerCase().split("\\s");
        if (pre_tokens.length == 0) {
            return;
        }
        // count consequential terms only
        for (String token : pre_tokens) {
            token = token.trim();
            if (feature.minimumTokenLength <= token.length() && token.length() <= feature.maximumTokenLength && !stopWords.contains(token)) {
                updateHistogramTokenCount(feature, token);
            }
        }
    }

    public void feedSearchResults(final List<? extends SearchResult> results) {
        for (SearchResult sr : results) {
            addSearchTerms(KeywordDetector.Feature.SEARCH_SOURCE, sr.getSource().toLowerCase());
            if (sr instanceof FileSearchResult) {
                String fileName = ((FileSearchResult) sr).getFilename().toLowerCase();
                String ext = FilenameUtils.getExtension(fileName);
                if (fileName != null && !fileName.isEmpty()) {
                    addSearchTerms(KeywordDetector.Feature.FILE_NAME, fileName);
                }
                if (ext != null && !ext.isEmpty()) {
                    addSearchTerms(KeywordDetector.Feature.FILE_EXTENSION, ext);
                }
            }
        }
    }

    /**
     * Cheap
     */
    private void updateHistogramTokenCount(Feature feature, String token) {
        HistoHashMap<String> histogram = histograms.get(feature);
        if (histogram != null) {
            histogram.update(token);
        }
    }

    public void shutdownHistogramUpdateRequestDispatcher() {
        histogramUpdateRequestsDispatcher.shutdown();
    }

    private static class HistogramUpdateRequestTask implements Runnable {
        private final KeywordDetector keywordDetector;
        private final Feature feature;
        private final List<SearchResult> filtered;

        public HistogramUpdateRequestTask(KeywordDetector keywordDetector, final Feature feature, List<SearchResult> filtered) {
            this.keywordDetector = keywordDetector;
            this.feature = feature;
            // TODO: this is necessary to due the amount of concurrency, but not
            // good for memory, need to refactor this
            this.filtered = filtered != null ? new ArrayList<>(filtered) : null;
        }

        @Override
        public void run() {
            if (keywordDetector.keywordDetectorListener != null) {
                try {
                    if (filtered != null) {
                        keywordDetector.reset(feature);
                        keywordDetector.feedSearchResults(filtered);
                    }
                    keywordDetector.histogramUpdateRequestsDispatcher.onLastHistogramRequestFinished();
                    keywordDetector.notifyKeywordDetectorListener();
                } catch (Throwable t) {
                    LOG.error(t.getMessage(), t);
                }
            }
        }
    }

    /**
     * Keeps a queue of up to |Features| and executes them with a fixed delay.
     * If there are no Histogram Update Requests to perform it waits until there are
     * requests to process.
     * <p>
     * You must remember to shutdown the inner thread on shutdown.
     */
    private class HistogramUpdateRequestDispatcher implements Runnable {
        private static final long HISTOGRAM_REQUEST_TASK_DELAY_IN_MS = 500L;
        private final AtomicLong lastHistogramUpdateRequestFinished;
        /**
         * This Map can only contain as many elements as Features are available.
         * For now one SEARCH_SOURCE request
         * one FILE_EXTENSION request
         * one FILE_NAME request
         */
        private final List<HistogramUpdateRequestTask> histogramUpdateRequests;

        private final AtomicBoolean running = new AtomicBoolean(false);
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition loopLock = lock.newCondition();


        public HistogramUpdateRequestDispatcher() {
            histogramUpdateRequests = new LinkedList<>();
            lastHistogramUpdateRequestFinished = new AtomicLong(0);
        }

        public void enqueue(HistogramUpdateRequestTask updateRequestTask) {
            if (!running.get() || updateRequestTask == null) {
                return;
            }
            if (histogramUpdateRequests.isEmpty()) {
                histogramUpdateRequests.add(updateRequestTask);
            } else {
                // search if there's another update request task for this same feature and remove it
                // NOTE on 'synchronized' use: this should be super fast since it's only up to 3 elements
                synchronized (histogramUpdateRequests) {
                    for (int i = 0; i < histogramUpdateRequests.size(); i++) {
                        try {
                            HistogramUpdateRequestTask histogramUpdateRequestTask = histogramUpdateRequests.get(i);
                            if (histogramUpdateRequestTask.feature.equals(updateRequestTask.feature)) {
                                histogramUpdateRequests.remove(i);
                                break;
                            }
                        } catch (Throwable t) {
                            // possible IndexOutOfBounds on histogramUpdateRequests.get()
                        }
                    }
                    histogramUpdateRequests.add(updateRequestTask);
                }
            }
            signalLoopLock();
        }

        @Override
        public void run() {
            while (running.get()) {
                // are there any tasks left?
                if (histogramUpdateRequests.size() > 0) {
                    long timeSinceLastFinished = System.currentTimeMillis() - lastHistogramUpdateRequestFinished.get();
                    //LOG.info("HistogramUpdateRequestDispatcher timeSinceLastFinished: " + timeSinceLastFinished + "ms - tasks in queue:" + histogramUpdateRequests.size());
                    if (timeSinceLastFinished > HISTOGRAM_REQUEST_TASK_DELAY_IN_MS) {
                        // take next request in line
                        HistogramUpdateRequestTask histogramUpdateRequestTask;
                        synchronized (histogramUpdateRequests) {
                            try {
                                histogramUpdateRequestTask = histogramUpdateRequests.remove(0);
                            } catch (Throwable t) {
                                histogramUpdateRequestTask = null;
                            }
                        }
                        // submit next task if there is any left
                        if (histogramUpdateRequestTask != null && running.get() && threadPool != null) {
                            try {
                                threadPool.submit(histogramUpdateRequestTask);
                            } catch (Throwable t) {
                                LOG.error(t.getMessage(), t);
                            }
                        }
                    }
                }
                try {
                    if (running.get()) {
                        lock.lock();
                        loopLock.await(1, TimeUnit.MINUTES);
                        lock.unlock();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            clear();
            shutdownThreadPool();
        }

        public void onLastHistogramRequestFinished() {
            if (running.get()) {
                lastHistogramUpdateRequestFinished.set(System.currentTimeMillis());
                signalLoopLock();
            }
        }

        public void start() {
            running.set(true);
            threadPool = ThreadPool.newThreadPool("KeywordDetector-pool", 1, false);
            new Thread(this, "HistogramUpdateRequestDispatcher").start();
        }

        public void shutdown() {
            running.set(false);
            signalLoopLock();
            shutdownThreadPool();
        }

        private void shutdownThreadPool() {
            if (threadPool != null) {
                try {
                    threadPool.shutdown();
                } catch (Throwable ignored) {}
            }
        }

        public void clear() {
            lastHistogramUpdateRequestFinished.set(0);
            synchronized (histogramUpdateRequests) {
                histogramUpdateRequests.clear();
            }
            signalLoopLock();
        }

        private void signalLoopLock() {
            try {
                lock.lock();
                loopLock.signal();
            } catch (Throwable ignored) {
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Expensive
     */
    public void requestHistogramUpdateAsync(final Feature feature, List<SearchResult> filtered) {
        if (!histogramUpdateRequestsDispatcher.running.get()) {
            histogramUpdateRequestsDispatcher.start();
        }
        HistogramUpdateRequestTask histogramUpdateRequestTask = new HistogramUpdateRequestTask(this, feature, filtered);
        histogramUpdateRequestsDispatcher.enqueue(histogramUpdateRequestTask);
    }

    public void reset() {
        histogramUpdateRequestsDispatcher.clear();
        if (histograms != null && !histograms.isEmpty()) {
            for (HistoHashMap<String> stringHistoHashMap : histograms.values()) {
                stringHistoHashMap.reset();
            }
        }
        notifyKeywordDetectorListener();
    }

    private void reset(Feature featureKey) {
        if (histograms != null && !histograms.isEmpty()) {
            HistoHashMap<String> histogram = histograms.get(featureKey);
            histogram.reset();
        }
    }

    private static void feedStopWords(String... words) {
        Collections.addAll(stopWords, words);
    }

    static {
        // english
        feedStopWords("-", "an", "and", "are", "as", "at", "be", "by", "for", "with", "when", "where");
        feedStopWords("from", "has", "he", "in", "is", "it", "its", "of", "on", "we", "why", "your");
        feedStopWords("that", "the", "to", "that", "this", "ft", "ft.", "feat", "feat.", "no", "me");
        feedStopWords("can", "cant", "not", "get", "into", "have", "had", "put", "you", "dont", "youre");
        // spanish
        feedStopWords("son", "como", "en", "ser", "por", "dónde", "donde", "cuando", "el");
        feedStopWords("de", "tiene", "él", "en", "es", "su", "de", "en", "nosotros", "por", "qué", "que");
        feedStopWords("eso", "el", "esa", "esto", "yo", "usted", "tu", "los", "para");
        // portuguese
        feedStopWords("filho", "como", "em", "quando", "nos");
        feedStopWords("tem", "ele", "seu", "nós", "quem");
        feedStopWords("isto", "voce", "você", "seu");
        // french
        feedStopWords("fils", "sous", "par", "où", "ou", "quand");
        feedStopWords("leur", "dans", "nous", "par", "ce", "qui");
        feedStopWords("il", "le", "vous", "votre");
        // TODO: Add more here as we start testing and getting noise
    }

    public interface KeywordDetectorListener {
        void onHistogramUpdate(KeywordDetector detector, Feature feature, List<Map.Entry<String, Integer>> histogram);

        void notify(KeywordDetector detector, Map<Feature, HistoHashMap<String>> histograms);
    }
}
