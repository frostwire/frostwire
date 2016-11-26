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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Created on 11/26/2016
 *
 * @author gubatron
 * @author aldenml
 */
public final class KeywordDetector {

    public enum Feature {
        FILE_NAME,
        FILE_EXTENSION,
        SEARCH_SOURCE
    }

    public enum Mode {
        INACTIVE,
        INCLUSIVE,
        EXCLUSIVE
    }

    private static Logger LOG = Logger.getLogger(KeywordDetector.class);
    private static final Set<String> stopWords = new HashSet<>();
    private final Map<Feature, HistoHashMap<String>> histograms;
    private final Map<Feature,Integer> numSearchesProcessed;
    private final ExecutorService threadPool;
    private KeywordDetectorListener listener;


    @SuppressWarnings("unused")
    public KeywordDetector() {
        this(null);
    }

    public KeywordDetector(ExecutorService threadPool) {
        histograms = new HashMap<>();
        // TODO: Turn these two on
        //histograms.put(Feature.FILE_NAME, new HistoHashMap<String>());
        //histograms.put(Feature.FILE_EXTENSION, new HistoHashMap<String>());
        histograms.put(Feature.SEARCH_SOURCE, new HistoHashMap<String>());

        numSearchesProcessed = new HashMap<>();
        this.threadPool = threadPool;
    }

    public void notifyListener() {
        if (this.listener != null) {
            this.listener.notify(this, histograms);
        }
    }

    public void setKeywordDetectorListener(KeywordDetectorListener listener) {
        this.listener = listener;
    }

    public KeywordDetectorListener getKeywordDetectorListener() {
        return listener;
    }

    public void addSearchTerms(Feature feature, String terms) {
        // tokenize
        String[] pre_tokens = terms.split("\\s");
        if (pre_tokens.length == 0) {
            return;
        }
        // count consequential terms only
        for (String token : pre_tokens) {
            if (!stopWords.contains(token)) {
                updateHistogramTokenCount(feature, token);
            }
        }
        Integer numTermsProcessed = numSearchesProcessed.get(feature);
        if (numTermsProcessed == null) {
            numTermsProcessed = new Integer(1);
        } else {
            numTermsProcessed++;
        }
        numSearchesProcessed.put(feature, numTermsProcessed);

        if (listener != null) {
            listener.onSearchReceived(this, feature, numTermsProcessed);
        }
    }

    /** Cheap */
    private void updateHistogramTokenCount(Feature feature, String token) {
        HistoHashMap<String> histogram = histograms.get(feature);
        if (histogram != null) {
            histogram.update(token);
        }
    }

    /** Expensive */
    public void requestHistogramUpdate(Feature feature) {
        HistoHashMap<String> histoHashMap = histograms.get(feature);
        if (histoHashMap != null) {
            LOG.info("KeywordDetector.requestHistogramUpdateAsync(" + feature + ")");
            requestHistogramUpdateAsync(feature, histoHashMap);
        } else {
            LOG.info("KeywordDetector.requestHistogramUpdateAsync(" + feature + ") failed. No histoHashMap for this feature.");
        }
    }

    /** Expensive */
    private void requestHistogramUpdateAsync(final Feature feature, final HistoHashMap<String> histoHashMap) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    LOG.info("KeywordDetector.requestHistogramUpdateAsync(" + feature + "): calculating histogram...");
                    Map.Entry<String, Integer>[] histogram = histoHashMap.histogram();
                    LOG.info("KeywordDetector.requestHistogramUpdateAsync(" + feature + "): histogram has " + histogram.length + " entries");
                    listener.onHistogramUpdate(KeywordDetector.this, feature, histogram);
                } else {
                    LOG.warn("KeywordDetector.requestHistogramUpdateAsync(feature=" + feature + ", histoHashMap=...) inconsequential. No listener found.");
                }
            }
        };
        if (threadPool != null) {
            threadPool.submit(r);
        } else {
            new Thread(r, "KeywordDetector::requestHistogramUpdateAsync()").start();
        }
    }

    public void reset() {
        numSearchesProcessed.clear();
        if (histograms != null && !histograms.isEmpty()) {
            for (HistoHashMap<String> stringHistoHashMap : histograms.values()) {
                stringHistoHashMap.reset();
            }
        }
        notifyListener();
    }

    private static void feedStopWords(String ... words) {
        Collections.addAll(stopWords, words);
    }

    static {
        // english
        feedStopWords("-","a", "an", "and", "are", "as", "at", "be", "by", "for");
        feedStopWords("from", "has", "he", "in", "is", "it", "its", "of", "on");
        feedStopWords("that", "the", "to", "that", "this");
        // TODO: Add more here as we start testing and getting noise
    }

    public interface KeywordDetectorListener {
        void onSearchReceived(final KeywordDetector detector, final Feature feature, final int numSearchesProcessed);

        void onHistogramUpdate(final KeywordDetector detector, final Feature feature, final Map.Entry<String, Integer>[] histogram);

        void notify(final KeywordDetector detector, final Map<Feature, HistoHashMap<String>> histograms);
    }
}