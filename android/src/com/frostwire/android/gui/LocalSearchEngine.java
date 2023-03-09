/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui;

import android.text.Html;

import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.util.SystemUtils;
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
import java.util.stream.Collectors;

/**
 * @author gubatron
 * @author aldenml
 */
public final class LocalSearchEngine {

    private final SearchManager manager;
    private SearchListener searchListener;

    private static final Object instanceLock = new Object();
    private static LocalSearchEngine instance;
    private final HashSet<Integer> opened = new HashSet<>();
    private long currentSearchToken;
    private List<String> currentSearchTokens;
    private boolean searchFinished;
    private TellurideCourier.SearchPerformer lastTellurideCourier;

    public static LocalSearchEngine instance() {
        if (instance == null) {
            synchronized (instanceLock) {
                if (instance == null) {
                    instance = new LocalSearchEngine();
                }
            }
        }
        return instance;
    }


    private LocalSearchEngine() {
        this.manager = SearchManager.getInstance();
        this.manager.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                if (token == currentSearchToken) { // one more additional protection
                    List<SearchResult> filtered = filter(results);

                    if (!filtered.isEmpty()) {
                        if (searchListener != null) {
                            searchListener.onResults(token, filtered);
                            filtered.clear();
                        }
                    }
                }
            }

            @Override
            public void onStopped(long token) {
                if (token == currentSearchToken) {
                    searchFinished = true;
                    if (searchListener != null) {
                        searchListener.onStopped(token);
                    }
                }
            }

            @Override
            public void onError(long token, SearchError error) {

            }
        });
    }

    public SearchListener getListener() {
        return searchListener;
    }

    public void setSearchListener(SearchListener searchListener) {
        this.searchListener = searchListener;
    }

    public void performSearch(String query) {
        SystemUtils.ensureBackgroundThreadOrCrash("LocalSearchEngine::performSearch(query=" + query + ")");
        if (StringUtils.isNullOrEmpty(query, true)) {
            return;
        }
        manager.stop();
        currentSearchToken = Math.abs(System.nanoTime());
        currentSearchTokens = tokenize(query);
        searchFinished = false;
        ArrayList<SearchEngine> shuffledEngines = new ArrayList<>(SearchEngine.getEngines(true));
        Collections.shuffle(shuffledEngines);
        for (SearchEngine se : shuffledEngines) {
            if (se.isEnabled() && se.isReady()) {
                SearchPerformer p = se.getPerformer(currentSearchToken, query);
                manager.perform(p);
            }
        }
    }

    public void performTellurideSearch(String pageUrl, SearchResultListAdapter adapter, SearchFragment searchFragment) {
        SystemUtils.ensureBackgroundThreadOrCrash("LocalSearchEngine::performTellurideSearch(pageUrl=" + pageUrl + ")");
        if (StringUtils.isNullOrEmpty(pageUrl, true)) {
            return;
        }
        manager.stop();
        currentSearchToken = Math.abs(System.nanoTime());
        currentSearchTokens = new ArrayList<>();
        currentSearchTokens.add(pageUrl);
        searchFinished = false;
        lastTellurideCourier = SearchEngine.TELLURIDE_COURIER.getTelluridePerformer(currentSearchToken, pageUrl, adapter);
        manager.perform(lastTellurideCourier);
    }


    public void cancelSearch() {
        currentSearchToken = 0;
        currentSearchTokens = null;
        searchFinished = true;
        manager.stop();
        if (lastTellurideCourier != null) {
            lastTellurideCourier.stop();
        }
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

    public void markOpened(SearchResult sr, SearchResultListAdapter adapter) {
        opened.add(getSearchResultUID(sr));
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public boolean hasBeenOpened(SearchResult sr) {
        return sr != null && opened.contains(getSearchResultUID(sr));
    }

    private List<SearchResult> filter(List<? extends SearchResult> results) {
        List<SearchResult> list;

        if (currentSearchTokens == null || currentSearchTokens.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            list = results.stream().parallel().filter(sr -> {
                if (sr instanceof CrawledSearchResult && allQueryTokensExistInSearchResult(new LinkedList<>(currentSearchTokens), sr)) {
                    return true;
                }
                return !(sr instanceof CrawledSearchResult);
            }).collect(Collectors.toList());
        } catch (Throwable t) {
            list = new LinkedList<>();
        }

        return list;
    }


    /**
     * Using properties of the search result, we build a lowercase string and then we return true if ALL the tokens are to be found in that string
     */
    private boolean allQueryTokensExistInSearchResult(List<String> tokens, SearchResult sr) {
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
        str = Html.fromHtml(str, Html.FROM_HTML_MODE_LEGACY).toString();
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
