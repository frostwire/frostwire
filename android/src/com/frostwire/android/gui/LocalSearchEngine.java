/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui;

import android.text.Html;

import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.search.CrawlPagedWebSearchPerformer;
import com.frostwire.search.CrawledSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.SearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public final class LocalSearchEngine {

    private final SearchManager manager;
    private SearchListener listener;

    private static final Object instanceLock = new Object();
    private static LocalSearchEngine instance;
    private final HashSet<Integer> opened = new HashSet<>();
    private long currentSearchToken;
    private List<String> currentSearchTokens;
    private boolean searchFinished;

    public static LocalSearchEngine instance() {
        if (instance != null) {
            return instance;
        } else {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new LocalSearchEngine();
                }
            }
            return instance;
        }
    }

    private LocalSearchEngine() {
        this.manager = SearchManager.getInstance();
        this.manager.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                LocalSearchEngine.this.onResults(token, results);
            }

            @Override
            public void onError(long token, SearchError error) {

            }

            @Override
            public void onStopped(long token) {
                LocalSearchEngine.this.onFinished(token);
            }
        });
    }

    @SuppressWarnings("unused")
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

        manager.stop();

        currentSearchToken = Math.abs(System.nanoTime());
        currentSearchTokens = tokenize(query);
        searchFinished = false;

        ArrayList<SearchEngine> shuffledEngines = new ArrayList<>(SearchEngine.getEngines());
        Collections.shuffle(shuffledEngines);
        for (SearchEngine se : shuffledEngines) {
            if (se.isEnabled()) {
                SearchPerformer p = se.getPerformer(currentSearchToken, query);
                manager.perform(p);
            }
        }
    }

    public void cancelSearch() {
        manager.stop();
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

    public void markOpened(SearchResult sr, AbstractListAdapter adapter) {
        opened.add(getSearchResultUID(sr));
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public boolean hasBeenOpened(SearchResult sr) {
        return sr != null && opened.contains(getSearchResultUID(sr));
    }

    private void onResults(long token, List<? extends SearchResult> results) {
        if (token == currentSearchToken) { // one more additional protection
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
                    if (filter(new LinkedList<>(currentSearchTokens), sr)) {
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
        str = Html.fromHtml(str).toString();
        //noinspection RegExpRedundantEscape
        str = str.replaceAll("\\.torrent|www\\.|\\.com|\\.net|[\\\\\\/%_;\\-\\.\\(\\)\\[\\]\\n\\rÐ&~{}\\*@\\^'=!,¡|#ÀÁ]", " ");
        str = StringUtils.removeDoubleSpaces(str);

        return str.trim();
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

    private int getSearchResultUID(SearchResult sr) {
        StringBuilder seed = new StringBuilder();
        if (sr.getDisplayName() != null) {
            seed.append(sr.getDisplayName());
        }
        if (sr.getDetailsUrl() != null) {
            seed.append(sr.getDetailsUrl());
        }
        if (sr.getSource() != null) {
            seed.append(sr.getSource());
        }
        if (sr instanceof TorrentSearchResult && ((TorrentSearchResult) sr).getHash() != null) {
            seed.append(((TorrentSearchResult) sr).getHash());
        }
        return StringUtils.quickHash(seed.toString());
    }
}
