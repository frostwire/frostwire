/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui;

import android.text.Html;
import com.frostwire.android.gui.views.AbstractListAdapter;
import com.frostwire.search.*;
import com.frostwire.search.extratorrent.ExtratorrentSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.util.StringUtils;

import java.text.Normalizer;
import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class LocalSearchEngine {

    private final SearchManager manager;
    private SearchListener listener;

    // filter constants
    private static final int KAT_MIN_SEEDS_TORRENT_RESULT = 2;
    private final int MIN_SEEDS_TORRENT_RESULT;

    private static LocalSearchEngine instance;
    private final HashSet<Integer> opened = new HashSet<>();
    private long currentSearchToken;
    private List<String> currentSearchTokens;
    private boolean searchFinished;

    public synchronized static void create() {
        if (instance != null) {
            return;
        }
        instance = new LocalSearchEngine();
    }

    public static LocalSearchEngine instance() {
        return instance;
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

        // TODO: review the logic behind putting this in a preference
        this.MIN_SEEDS_TORRENT_RESULT = 10;//ConfigurationManager.instance().getInt(Constants.PREF_KEY_SEARCH_MIN_SEEDS_FOR_TORRENT_RESULT);
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

        manager.stop();

        currentSearchToken = Math.abs(System.nanoTime());
        currentSearchTokens = tokenize(query);
        searchFinished = false;

        for (SearchEngine se : SearchEngine.getEngines()) {
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
        opened.add(sr.uid());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

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
        List<SearchResult> list = new LinkedList<SearchResult>();

        try {
            for (SearchResult sr : results) {
                if (sr instanceof TorrentSearchResult) {
                    if (((TorrentSearchResult) sr).getSeeds() == -1) {
                        long creationTime = sr.getCreationTime();
                        long age = System.currentTimeMillis() - creationTime;
                        if (age > 31536000000l) {
                            continue;
                        }
                    } else if (sr instanceof ExtratorrentSearchResult) {
                        // TODO: Search architecture hack, gotta abstract these guys.
                        if (((TorrentSearchResult) sr).getSeeds() < KAT_MIN_SEEDS_TORRENT_RESULT) {
                            continue;
                        }
                    } else if (sr instanceof ScrapedTorrentFileSearchResult) {
                        // TODO: Search architecture hack, gotta abstract these guys.
                        if (((TorrentSearchResult) sr).getSeeds() < KAT_MIN_SEEDS_TORRENT_RESULT) {
                            continue;
                        }
                    } else if (((TorrentSearchResult) sr).getSeeds() < MIN_SEEDS_TORRENT_RESULT) {
                        continue;
                    }
                }

                if (sr instanceof CrawledSearchResult) {
                    if (sr instanceof YouTubeCrawledSearchResult) {
                        // special case for flv files
                        if (!((YouTubeCrawledSearchResult) sr).getFilename().endsWith(".flv")) {
                            list.add(sr);
                        }
                    } else if (sr instanceof ScrapedTorrentFileSearchResult) {
                        list.add(sr);
                    } else if (filter(new LinkedList<String>(currentSearchTokens), sr)) {
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
        Set<String> normalizedTokens = new HashSet<String>();

        for (String token : tokens) {
            String norm = normalize(token);
            normalizedTokens.add(norm);
        }

        return normalizedTokens;
    }

    private List<String> tokenize(String keywords) {
        keywords = sanitize(keywords);

        Set<String> tokens = new HashSet<String>(Arrays.asList(keywords.toLowerCase(Locale.US).split(" ")));

        return new ArrayList<String>(normalizeTokens(tokens));
    }
}
