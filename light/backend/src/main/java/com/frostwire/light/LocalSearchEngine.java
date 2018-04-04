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

package com.frostwire.light;

import com.frostwire.search.*;
import com.frostwire.search.frostclick.UserAgent;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.util.StringUtils;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author gubatron
 * @author aldenml
 */
public final class LocalSearchEngine {

    private final Map<SearchEngine.Type, SearchManager> managers;
    private SearchListener listener;

    private static Future<LocalSearchEngine> instance;
    private final HashSet<Integer> opened = new HashSet<>();
    private long currentSearchToken;
    private List<String> currentSearchTokens;
    private boolean searchFinished;

    public synchronized static void create(
            UserAgent frostWireUserAgent,
            ExecutorService cloudExecutorService,
            ExecutorService torrentExecutorService,
            ExecutorService p2pExecutorService) {
        if (frostWireUserAgent == null) {
            throw new IllegalArgumentException("LocalSearchEngine.create(): UserAgent parameter can't be null");
        }
        if (instance != null) {
            return;
        }
        SearchEngine.initFrostWireUserAgent(frostWireUserAgent);
        instance = cloudExecutorService.submit(() -> new LocalSearchEngine(cloudExecutorService, torrentExecutorService, p2pExecutorService));
    }

    public static LocalSearchEngine instance() {
        try {
            return instance.get();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private LocalSearchEngine(ExecutorService cloudExecutorService,
                              ExecutorService torrentExecutorService,
                              ExecutorService p2pExecutorService) {
        this.managers = new HashMap<>();

        this.managers.put(SearchEngine.Type.CLOUD, new SearchManager(cloudExecutorService));
        this.managers.put(SearchEngine.Type.TORRENT, new SearchManager(torrentExecutorService));
        //this.managers.put(SearchEngine.Type.P2P, new SearchManager(p2pExecutorService));

        LocalSearchEngineSearchListener localSearchEngineSearchListener = new LocalSearchEngineSearchListener(this);
        this.managers.get(SearchEngine.Type.CLOUD).setListener(localSearchEngineSearchListener);
        this.managers.get(SearchEngine.Type.TORRENT).setListener(localSearchEngineSearchListener);
        //this.managers.get(SearchEngine.Type.P2P).setListener(localSearchEngineSearchListener);
    }

    public SearchListener getListener() {
        return listener;
    }

    public void setListener(SearchListener listener) {
        this.listener = listener;
    }

    public void performSearch(String query) {
        if (StringUtils.isNullOrEmpty(query, true)) {
            return;
        }

        cancelSearch();

        currentSearchToken = Math.abs(System.nanoTime());
        currentSearchTokens = tokenize(query);
        searchFinished = false;

        SearchManager manager = managers.get(SearchEngine.Type.CLOUD);
        for (SearchEngine se : SearchEngine.getEngines(SearchEngine.Type.CLOUD)) {
            if (se.isEnabled()) {
                SearchPerformer p = se.getPerformer(currentSearchToken, query);
                manager.perform(p);
            }
        }
        manager = managers.get(SearchEngine.Type.TORRENT);
        for (SearchEngine se : SearchEngine.getEngines(SearchEngine.Type.TORRENT)) {
            if (se.isEnabled()) {
                SearchPerformer p = se.getPerformer(currentSearchToken, query);
                manager.perform(p);
            }
        }
        /**
        manager = managers.get(SearchEngine.Type.P2P);
        for (SearchEngine se : SearchEngine.getEngines(SearchEngine.Type.P2P)) {
            if (se.isEnabled()) {
                SearchPerformer p = se.getPerformer(currentSearchToken, query);
                manager.perform(p);
            }
        }
        */
    }

    public void cancelSearch() {
        managers.get(SearchEngine.Type.CLOUD).stop();
        managers.get(SearchEngine.Type.TORRENT).stop();
        //managers.get(SearchEngine.Type.P2P).stop();

        currentSearchToken = 0;
        currentSearchTokens = null;
        searchFinished = true;
    }

    public boolean isSearchStopped() {
        return currentSearchToken == 0;
    }

    public boolean isSearchFinished() {
        return searchFinished;
    }

    public void clearCache() {
        CrawlPagedWebSearchPerformer.clearCache();
    }

    public long getCacheSize() {
        return CrawlPagedWebSearchPerformer.getCacheSize();
    }

    /**
    public void markOpened(SearchResult sr, AbstractListAdapter adapter) {
        opened.add(sr.uid());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    */

    public boolean hasBeenOpened(SearchResult sr) {
        return sr != null && opened.contains(sr.uid());
    }

    private void onResults(long token, List<? extends SearchResult> results) {
        if (token == currentSearchToken) { // one more additional protection
            @SuppressWarnings("unchecked")
            List<SearchResult> filtered = filter(results);

            if (!filtered.isEmpty()) {
                if (listener != null) {
                    listener.onResults(token, filtered);
                }
            }
        }
    }

    private void onFinished(long token) {
        if (token == currentSearchToken) {
            searchFinished = true;
            if (listener != null) {
                listener.onStopped(token);
            }
        }
    }

    private List<SearchResult> filter(List<? extends SearchResult> results) {
        List<SearchResult> list;

        if (currentSearchTokens == null || currentSearchTokens.isEmpty()) {
            list = Collections.emptyList();
        } else {
            list = filter2(results);
        }

        return list;
    }

    private List<SearchResult> filter2(List<? extends SearchResult> results) {
        List<SearchResult> list = new LinkedList<>();

        try {
            for (SearchResult sr : results) {
                if (sr instanceof CrawledSearchResult) {
                    if (sr instanceof YouTubeCrawledSearchResult) {
                        // special case for flv files
                        if (!((YouTubeCrawledSearchResult) sr).getFilename().endsWith(".flv")) {
                            list.add(sr);
                        }
                    } else if (filter(new LinkedList<>(currentSearchTokens), sr)) {
                        list.add(sr);
                    }
                } else {
                    list.add(sr);
                }
            }
        } catch (Throwable e) {
            // possible NPE due to cancel search or some inner error in search results, ignore it and cleanup list
            list.clear();
        }

        return list;
    }

    private boolean filter(List<String> tokens, SearchResult sr) {
        StringBuilder sb = new StringBuilder();

        sb.append(sr.getDisplayName());
        if (sr instanceof CrawledSearchResult) {
            sb.append(((CrawledSearchResult) sr).getParent().getDisplayName());
        }

        if (sr instanceof FileSearchResult) {
            sb.append(((FileSearchResult) sr).getFilename());
        }

        String str = sanitize(sb.toString());
        str = normalize(str);

        Iterator<String> it = tokens.iterator();
        while (it.hasNext()) {
            String token = it.next();
            if (str.contains(token)) {
                it.remove();
            }
        }

        return tokens.isEmpty();
    }

    private String sanitize(String str) {
        str = stripHtml(str);
        str = str.replaceAll("\\.torrent|www\\.|\\.com|\\.net|[\\\\\\/%_;\\-\\.\\(\\)\\[\\]\\n\\rÐ&~{}\\*@\\^'=!,¡|#ÀÁ]", " ");
        str = StringUtils.removeDoubleSpaces(str);
        return str.trim();
    }

    private static String stripHtml(String str) {
        str = str.replaceAll("\\<.*?>", "");
        str = str.replaceAll("\\&.*?\\;", "");
        return str;
    }

    private String normalize(String token) {
        String norm = Normalizer.normalize(token, Normalizer.Form.NFKD);
        norm = norm.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        norm = norm.toLowerCase(Locale.US);

        return norm;
    }

    private Set<String> normalizeTokens(Set<String> tokens) {
        Set<String> normalizedTokens = new HashSet<>(0);

        for (String token : tokens) {
            String norm = normalize(token);
            normalizedTokens.add(norm);
        }

        return normalizedTokens;
    }

    private List<String> tokenize(String keywords) {
        keywords = sanitize(keywords);

        Set<String> tokens = new HashSet<>(Arrays.asList(keywords.toLowerCase(Locale.US).split(" ")));

        return new ArrayList<>(normalizeTokens(tokens));
    }

    private static class LocalSearchEngineSearchListener implements SearchListener {
        private LocalSearchEngine localSearchEngine;

        LocalSearchEngineSearchListener(LocalSearchEngine localSearchEngine) {
            this.localSearchEngine = localSearchEngine;
        }

        @Override
        public void onResults(long token, List<? extends SearchResult> results) {
            localSearchEngine.onResults(token, results);
        }

        @Override
        public void onError(long token, SearchError error) {

        }

        @Override
        public void onStopped(long token) {
            localSearchEngine.onFinished(token);
        }
    }
}
