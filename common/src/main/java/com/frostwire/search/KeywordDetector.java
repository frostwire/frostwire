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

    public enum Mode {
        INACTIVE,
        INCLUSIVE,
        EXCLUSIVE
    }

    private static Logger LOG = Logger.getLogger(KeywordDetector.class);
    private static final Set<String> stopWords = new HashSet<>();
    private final Map<Feature, HistoHashMap<String>> histograms;
    private final Map<Feature, Integer> numSearchesProcessed;
    private final Map<Feature, Long> lastHistogramUpdates;
    private final ExecutorService threadPool;
    private KeywordDetectorListener keywordDetectorListener;

    public KeywordDetector(ExecutorService threadPool) {
        histograms = new HashMap<>();
        histograms.put(Feature.SEARCH_SOURCE, new HistoHashMap<String>());
        histograms.put(Feature.FILE_EXTENSION, new HistoHashMap<String>());
        histograms.put(Feature.FILE_NAME, new HistoHashMap<String>());
        numSearchesProcessed = new HashMap<>();
        lastHistogramUpdates = new HashMap<>();
        lastHistogramUpdates.put(Feature.SEARCH_SOURCE, 0l);
        lastHistogramUpdates.put(Feature.FILE_EXTENSION, 0l);
        lastHistogramUpdates.put(Feature.FILE_NAME, 0l);
        this.threadPool = threadPool;
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
        Integer numTermsProcessed = numSearchesProcessed.get(feature);
        if (numTermsProcessed == null) {
            numTermsProcessed = new Integer(1);
        } else {
            numTermsProcessed++;
        }
        numSearchesProcessed.put(feature, numTermsProcessed);
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

    /**
     * Expensive
     */
    private void requestHistogramUpdateAsync(final Feature feature, final HistoHashMap<String> histoHashMap, final boolean forceUIUpdate) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (keywordDetectorListener != null) {
                    //LOG.info("KeywordDetector.requestHistogramUpdateAsync(" + feature + "): calculating histogram...");
                    if (!forceUIUpdate) {
                        long now = System.currentTimeMillis();
                        long lastUpdate = lastHistogramUpdates.get(feature);
                        if (now - lastUpdate < 1000) {
                            return;
                        }
                    }
                    lastHistogramUpdates.put(feature, System.currentTimeMillis());
                    Map.Entry<String, Integer>[] histogram = histoHashMap.histogram();
                    keywordDetectorListener.onHistogramUpdate(KeywordDetector.this, feature, histogram, true);
                }
            }
        };
        if (threadPool != null) {
            threadPool.submit(r);
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

    private static void feedStopWords(String... words) {
        Collections.addAll(stopWords, words);
    }

    static {
        // english
        feedStopWords("-", "an", "and", "are", "as", "at", "be", "by", "for", "with", "when", "where");
        feedStopWords("from", "has", "he", "in", "is", "it", "its", "of", "on", "we", "why", "your");
        feedStopWords("that", "the", "to", "that", "this", "ft", "ft.", "feat", "feat.", "no", "me" );
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