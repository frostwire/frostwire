/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui;

import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.CrawlCacheManager;
import com.frostwire.search.CrawledSearchResult;
import com.frostwire.search.PerformersHelper;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchMediator {

    private final SearchManager manager;
    private SearchListener searchListener;

    private final HashSet<Integer> opened = new HashSet<>();
    private long currentSearchToken;
    private List<String> currentSearchTokens;
    private boolean searchFinished;
    private TellurideCourier.SearchPerformer lastTellurideCourier;

    private static final class InstanceHolder {
        private static final SearchMediator instance = new SearchMediator();
    }

    public static SearchMediator instance() {
        return InstanceHolder.instance;
    }


    private SearchMediator() {
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
        currentSearchTokens = PerformersHelper.tokenizeSearchKeywords(query);
        searchFinished = false;
        ArrayList<SearchEngine> shuffledEngines = new ArrayList<>(SearchEngine.getEngines(true));
        Collections.shuffle(shuffledEngines);
        for (SearchEngine se : shuffledEngines) {
            if (se.isEnabled() && se.isReady()) {
                ISearchPerformer p = se.getPerformer(currentSearchToken, query);
                manager.perform(p);
            }
        }
    }

    public void performTellurideSearch(String pageUrl, SearchResultListAdapter adapter) {
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
        manager.perform(SearchEngine.FROSTCLICK.getPerformer(1, pageUrl));
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
        CrawlCacheManager.clearCache();
    }

    public long getCacheSize() {
        return CrawlCacheManager.getCacheNumEntries();
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
                if (sr instanceof CrawledSearchResult && PerformersHelper.oneKeywordMatchedOrFuzzyMatchedFilter(new LinkedList<>(currentSearchTokens), sr)) {
                    return true;
                }
                return !(sr instanceof CrawledSearchResult);
            }).collect(Collectors.toList());


        } catch (Throwable t) {
            list = new LinkedList<>();
        }

        return list;
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