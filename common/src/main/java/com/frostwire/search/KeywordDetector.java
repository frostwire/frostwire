/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.

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

import com.frostwire.regex.Pattern;
import com.frostwire.util.HistoHashMap;
import com.frostwire.util.Logger;
import com.frostwire.util.ThreadPool;
import org.apache.commons.io.FilenameUtils;

import java.util.*;
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
    private static final Logger LOG = Logger.getLogger(KeywordDetector.class);
    private static final Set<String> stopWords = new HashSet<>();
    private static final Pattern REPLACE_ALL_PATTERN = Pattern.compile("[^\\p{L}0-9 .]|\\.{2,}");

    static {
        // english
        feedStopWords("-", "an", "and", "are", "as", "at", "be", "by", "for", "with", "when", "where");
        feedStopWords("from", "has", "he", "in", "is", "it", "its", "of", "on", "we", "why", "your");
        feedStopWords("that", "the", "to", "that", "this", "ft", "ft.", "feat", "feat.", "no", "me", "null");
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

    private final Map<Feature, HistoHashMap<String>> histoHashMaps;
    private final HistogramUpdateRequestDispatcher histogramUpdateRequestsDispatcher;
    private KeywordDetectorListener keywordDetectorListener;
    private ExecutorService threadPool;
    private int totalHistogramKeysCount = -1;

    public KeywordDetector() {
        histoHashMaps = new HashMap<>();
        histogramUpdateRequestsDispatcher = new HistogramUpdateRequestDispatcher();
        histoHashMaps.put(Feature.SEARCH_SOURCE, new HistoHashMap<>());
        histoHashMaps.put(Feature.FILE_EXTENSION, new HistoHashMap<>());
        histoHashMaps.put(Feature.FILE_NAME, new HistoHashMap<>());
    }

    public static List<Map.Entry<String, Integer>> highPassFilter(List<Map.Entry<String, Integer>> histogram, float threshold) {
        int high = 0;
        int totalCount = 0;
        for (Map.Entry<String, Integer> entry : histogram) {
            int count = entry.getValue();
            totalCount += count;
            if (count > high) {
                high = count;
            }
        }
        List<Map.Entry<String, Integer>> filteredValues = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : histogram) {
            float rate = (float) entry.getValue() / (high + totalCount);
            if (entry.getValue() > 1 && rate >= threshold) {
                filteredValues.add(entry);
            }
        }
        return filteredValues;
    }

    private static void feedStopWords(String... words) {
        Collections.addAll(stopWords, words);
    }

    public int totalHistogramKeys() {
        if (totalHistogramKeysCount == -1) {
            getFilteredHistograms();
        }
        return totalHistogramKeysCount;
    }

    public void notifyKeywordDetectorListener() {
        if (this.keywordDetectorListener != null) {
            this.keywordDetectorListener.notifyHistogramsUpdate(getFilteredHistograms());
        }
    }

    public void setKeywordDetectorListener(KeywordDetectorListener listener) {
        this.keywordDetectorListener = listener;
    }

    public void addSearchTerms(Feature feature, String terms) {
        // tokenize
        String[] pre_tokens = REPLACE_ALL_PATTERN.matcher(terms).replaceAll("").toLowerCase().split("\\s");
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

    public void feedSearchResults(final List<? extends SearchResult> copiedResults) {
        for (SearchResult sr : copiedResults) {
            addSearchTerms(KeywordDetector.Feature.SEARCH_SOURCE, sr.getSource());
            if (sr instanceof FileSearchResult) {
                String fileName = ((FileSearchResult) sr).getFilename();
                if (fileName == null || fileName.isEmpty()) {
                    continue;
                }
                addSearchTerms(KeywordDetector.Feature.FILE_NAME, fileName);
                // Check file extensions for YouTubeSearch results.
                // If we find files with extensions other than ".youtube", we make their mt = null and don't include them
                // in the keyword detector. IDEA: Make FileSearchResults have a .getMediaType() method and put this logic there.
                String extension = FilenameUtils.getExtension(fileName);
                if ("youtube".equals(extension)) {
                    continue;
                }
                KeywordMediaType mt = KeywordMediaType.getMediaTypeForExtension(extension);
                if (extension != null && !extension.isEmpty() && mt != null) {
                    addSearchTerms(KeywordDetector.Feature.FILE_EXTENSION, extension);
                }
            }
        }
    }

    /**
     * Cheap
     */
    private void updateHistogramTokenCount(Feature feature, String token) {
        HistoHashMap<String> histogram = histoHashMaps.get(feature);
        if (histogram != null && token != null) {
            histogram.update(token);
        }
    }

    public void clearHistogramUpdateRequestDispatcher() {
        histogramUpdateRequestsDispatcher.clear();
    }

    public void shutdownHistogramUpdateRequestDispatcher() {
        histogramUpdateRequestsDispatcher.shutdown();
    }

    public Map<Feature, List<Map.Entry<String, Integer>>> getFilteredHistograms() {
        HashMap<Feature, List<Map.Entry<String, Integer>>> filteredHistograms = new HashMap<>();
        totalHistogramKeysCount = 0;
        for (Feature feature : histoHashMaps.keySet()) {
            HistoHashMap<String> histoHashMap = histoHashMaps.get(feature);
            if (histoHashMap.getKeyCount() > 0) {
                List<Map.Entry<String, Integer>> histogram = histoHashMap.histogram();
                List<Map.Entry<String, Integer>> filteredHistogram = highPassFilter(histogram, feature.filterThreshold);
                if (filteredHistogram.size() > 0) {
                    filteredHistograms.put(feature, filteredHistogram);
                    totalHistogramKeysCount += filteredHistogram.size();
                }
            }
        }
        return filteredHistograms;
    }

    /**
     * Expensive
     */
    public void requestHistogramsUpdateAsync(List<SearchResult> filtered) {
        if (!histogramUpdateRequestsDispatcher.running.get()) {
            histogramUpdateRequestsDispatcher.start();
        }
        HistogramUpdateRequestTask histogramUpdateRequestTask = new HistogramUpdateRequestTask(this, filtered);
        histogramUpdateRequestsDispatcher.enqueue(histogramUpdateRequestTask);
    }

    public void reset() {
        histogramUpdateRequestsDispatcher.clear();
        if (histoHashMaps != null && !histoHashMaps.isEmpty()) {
            for (HistoHashMap<String> stringHistoHashMap : histoHashMaps.values()) {
                stringHistoHashMap.reset();
            }
        }
        notifyKeywordDetectorListener();
    }

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

    public interface KeywordDetectorListener {
        void notifyHistogramsUpdate(Map<Feature, List<Map.Entry<String, Integer>>> filteredHistograms);

        void onKeywordDetectorFinished();
    }

    private static class HistogramUpdateRequestTask implements Runnable {
        private final KeywordDetector keywordDetector;
        private final List<SearchResult> filtered;

        public HistogramUpdateRequestTask(KeywordDetector keywordDetector, List<SearchResult> filtered) {
            this.keywordDetector = keywordDetector;
            // TODO: this is necessary to due the amount of concurrency, but not
            // good for memory, need to refactor this
            this.filtered = filtered != null ? new ArrayList<>(filtered) : null;
        }

        @Override
        public void run() {
            if (keywordDetector != null) {
                try {
                    if (filtered != null) {
                        keywordDetector.reset();
                        keywordDetector.feedSearchResults(filtered);
                    }
                    keywordDetector.notifyKeywordDetectorListener();
                    keywordDetector.histogramUpdateRequestsDispatcher.onLastHistogramRequestFinished();
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
            synchronized (histogramUpdateRequests) {
                histogramUpdateRequests.add(0, updateRequestTask);
                if (histogramUpdateRequests.size() > 4) {
                    // All these acrobatics are because histogramUpdateRequests.sublist implementations always yield a concurrent modification exception
                    // when they're trying to obtain their internal array
                    Object[] requestsArray = histogramUpdateRequests.toArray();
                    ArrayList<HistogramUpdateRequestTask> head = new ArrayList<>(4);
                    head.add((HistogramUpdateRequestTask) requestsArray[0]);
                    head.add((HistogramUpdateRequestTask) requestsArray[1]);
                    head.add((HistogramUpdateRequestTask) requestsArray[2]);
                    head.add((HistogramUpdateRequestTask) requestsArray[3]);
                    histogramUpdateRequests.clear();
                    histogramUpdateRequests.addAll(head);
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
                                threadPool.execute(histogramUpdateRequestTask);
                            } catch (Throwable t) {
                                LOG.error(t.getMessage(), t);
                            }
                        }
                    }
                }
                try {
                    if (histogramUpdateRequests.size() == 0 && keywordDetectorListener != null) {
                        keywordDetectorListener.onKeywordDetectorFinished();
                        signalLoopLock();
                    }
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
            }
            signalLoopLock();
        }

        public void start() {
            clear();
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
                } catch (Throwable ignored) {
                }
            }
        }

        public void clear() {
            synchronized (histogramUpdateRequests) {
                histogramUpdateRequests.clear();
            }
            lastHistogramUpdateRequestFinished.set(0);
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
}
