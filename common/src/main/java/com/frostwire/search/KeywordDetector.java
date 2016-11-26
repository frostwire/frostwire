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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
        FILE_NAME(0.01f, 3, 20);

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

    public KeywordDetector(ExecutorService threadPool) {
        histograms = new HashMap<>();
        histogramUpdateRequestsDispatcher = new HistogramUpdateRequestDispatcher(threadPool);
        histograms.put(Feature.SEARCH_SOURCE, new HistoHashMap<String>());
        histograms.put(Feature.FILE_EXTENSION, new HistoHashMap<String>());
        histograms.put(Feature.FILE_NAME, new HistoHashMap<String>());
    }

    public int totalHistogramKeys() {
        return histograms.get(Feature.SEARCH_SOURCE).getKeyCount() +
                histograms.get(Feature.FILE_EXTENSION).getKeyCount() +
                histograms.get(Feature.FILE_NAME).getKeyCount();
    }

    public void notifyListener() {
        if (this.keywordDetectorListener != null) {
            this.keywordDetectorListener.notify(this, histograms);
        }
    }

    public void setKeywordDetectorListener(KeywordDetectorListener listener) {
        this.keywordDetectorListener = listener;
    }

    public void addSearchTerms(Feature feature, String terms) {
        // tokenize
        String[] pre_tokens = terms.replaceAll("[^a-zA-Z0-9\\p{L}\\. ]", "").toLowerCase().split("\\s");
        if (pre_tokens.length == 0) {
            return;
        }
        // count consequential terms only
        for (String token : pre_tokens) {
            token = token.trim();
            if (feature.minimumTokenLength <= token.length() && token.length() <= feature.maximumTokenLength && !stopWords.contains(token)) {
                updateHistogramTokenCount(feature, token);
            } else if (feature == Feature.FILE_EXTENSION) {
                LOG.info("!addSearchTerm(" + token + ")");
            }
        }
        notifyListener();
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

    /**
     * Expensive
     */
    public void requestHistogramUpdate(Feature feature, boolean forceUIUpdate) {
        HistoHashMap<String> histoHashMap = histograms.get(feature);
        if (histoHashMap != null) {
            requestHistogramUpdateAsync(feature, histoHashMap, forceUIUpdate);
        }
    }

    public void shutdownHistogramUpdateRequestDispatcher() {
        histogramUpdateRequestsDispatcher.shutdown();
    }

    private class HistogramUpdateRequestTask implements Runnable {

        private final Feature feature;
        private final HistoHashMap<String> histoHashMap;

        public HistogramUpdateRequestTask(final Feature feature, final HistoHashMap<String> histoHashMap) {
            this.feature = feature;
            this.histoHashMap = histoHashMap;
        }

        @Override
        public void run() {
            if (keywordDetectorListener != null) {
                Map.Entry<String, Integer>[] histogram = histoHashMap.histogram();
                histogramUpdateRequestsDispatcher.onLastHistogramRequestFinished();
                keywordDetectorListener.onHistogramUpdate(KeywordDetector.this, feature, histogram, true);
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
        private static final long HISTOGRAM_REQUEST_TASK_DELAY_IN_MS = 1000L;
        private final AtomicLong lastHistogramUpdateRequestFinished;
        /**
         * This Map can only contain as many elements as Features are available.
         * For now one SEARCH_SOURCE request
         * one FILE_EXTENSION request
         * one FILE_NAME request
         */
        private final List<HistogramUpdateRequestTask> histogramUpdateRequests;

        private final AtomicBoolean running = new AtomicBoolean(false);
        private final ExecutorService threadPool;
        private final Object loopLock = new Object();

        public HistogramUpdateRequestDispatcher(ExecutorService threadPool) {
            this.threadPool = threadPool;
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
                for (int i = 0; i < histogramUpdateRequests.size(); i++) {
                    HistogramUpdateRequestTask histogramUpdateRequestTask = histogramUpdateRequests.get(i);
                    if (histogramUpdateRequestTask.feature.equals(updateRequestTask.feature)) {
                        synchronized (histogramUpdateRequests) {
                            histogramUpdateRequests.remove(i);
                        }
                        break;
                    }
                }
                histogramUpdateRequests.add(updateRequestTask);
            }
            synchronized (loopLock) {
                loopLock.notify();
            }
        }

        @Override
        public void run() {
            while (running.get()) {
                // are there any tasks left?
                if (histogramUpdateRequests.size() > 0) {
                    long timeSinceLastFinished = System.currentTimeMillis() - lastHistogramUpdateRequestFinished.get();
                    LOG.info("HistogramUpdateRequestDispatcher timeSinceLastFinished: " + timeSinceLastFinished + "ms - tasks in queue:" + histogramUpdateRequests.size());
                    if (timeSinceLastFinished > HISTOGRAM_REQUEST_TASK_DELAY_IN_MS) {
                        LOG.info("HistogramUpdateRequestDispatcher waited long enough, submitting another task");
                        // take next request in line
                        HistogramUpdateRequestTask histogramUpdateRequestTask;
                        synchronized (histogramUpdateRequests) {
                            histogramUpdateRequestTask = histogramUpdateRequests.remove(0);
                        }
                        // submit next task if there is any left
                        if (threadPool != null && histogramUpdateRequestTask != null && running.get()) {
                            threadPool.submit(histogramUpdateRequestTask);
                        }
                    } else {
                        LOG.info("HistogramUpdateRequestDispatcher too early for submitting another task");
                    }
                }
                try {
                    if (running.get()) {
                        synchronized (loopLock) {
                            LOG.info("HistogramUpdateRequestDispatcher waiting...");
                            loopLock.wait();
                            LOG.info("HistogramUpdateRequestDispatcher resumed...");
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            LOG.info("HistogramUpdateRequestDispatcher thread shutdown.");
            clear();
        }

        public void onLastHistogramRequestFinished() {
            if (running.get()) {
                lastHistogramUpdateRequestFinished.set(System.currentTimeMillis());
                synchronized (loopLock) {
                    loopLock.notify();
                }
            }
        }

        public void start() {
            LOG.info("HistogramUpdateRequestDispatcher start()");
            running.set(true);
            new Thread(this, "HistogramUpdateRequestDispatcher").start();
        }

        public void shutdown() {
            LOG.info("HistogramUpdateRequestDispatcher shutdown()");
            running.set(false);
            synchronized (loopLock) {
                loopLock.notify();
            }
        }

        public void clear() {
            LOG.info("HistogramUpdateRequestDispatcher clear()");
            lastHistogramUpdateRequestFinished.set(0);
            synchronized (histogramUpdateRequests) {
                histogramUpdateRequests.clear();
            }
            synchronized (loopLock) {
                loopLock.notify();
            }
        }
    }

    /**
     * Expensive
     */
    private void requestHistogramUpdateAsync(final Feature feature, final HistoHashMap<String> histoHashMap, final boolean forceUIUpdate) {
        if (!histogramUpdateRequestsDispatcher.running.get()) {
            histogramUpdateRequestsDispatcher.start();
        }
        HistogramUpdateRequestTask histogramUpdateRequestTask = new HistogramUpdateRequestTask(feature, histoHashMap);
        if (forceUIUpdate) {
            histogramUpdateRequestsDispatcher.threadPool.submit(histogramUpdateRequestTask);
        } else {
            histogramUpdateRequestsDispatcher.enqueue(histogramUpdateRequestTask);
        }
    }

    public void reset() {
        histogramUpdateRequestsDispatcher.clear();
        if (histograms != null && !histograms.isEmpty()) {
            for (HistoHashMap<String> stringHistoHashMap : histograms.values()) {
                stringHistoHashMap.reset();
            }
        }
        notifyListener();
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
        void onHistogramUpdate(final KeywordDetector detector, final Feature feature, final Map.Entry<String, Integer>[] histogram, boolean force);

        void notify(final KeywordDetector detector, final Map<Feature, HistoHashMap<String>> histograms);
    }
}