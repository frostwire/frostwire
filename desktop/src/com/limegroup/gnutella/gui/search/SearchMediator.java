/*
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.bittorrent.BTDownload;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.filters.SearchFilter;
import com.frostwire.gui.filters.SearchFilterFactory;
import com.frostwire.gui.filters.SearchFilterFactoryImpl;
import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.search.*;
import com.frostwire.search.archiveorg.ArchiveorgCrawledSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.ApplicationHeader;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SearchSettings;
import org.limewire.util.I18NConvert;
import org.limewire.util.StringUtils;

import javax.swing.*;
import java.text.Normalizer;
import java.util.*;

/**
 * This class acts as a mediator between the various search components --
 * the hub that all traffic passes through.  This allows the decoupling of
 * the various search packages and simplifies the responsibilities of the
 * underlying classes.
 */
@SuppressWarnings("RegExpRedundantEscape")
public final class SearchMediator {
    private static final Logger LOG = Logger.getLogger(SearchMediator.class);
    /**
     * Query text is valid.
     */
    public static final int QUERY_VALID = 0;
    /**
     * Query text is empty.
     */
    public static final int QUERY_EMPTY = 1;
    /**
     * Query xml is too long.
     */
    public static final int QUERY_XML_TOO_LONG = 4;
    static final String DOWNLOAD_STRING = I18n.tr("Download");
    static final String REPEAT_SEARCH_STRING = I18n.tr("Repeat Search");
    static final String SEARCH_FOR_KEYWORDS = I18n.tr("Search for Keywords: {0}");
    static final String DOWNLOAD_PARTIAL_FILES_STRING = I18n.tr("Download Partial Files");
    static final String TORRENT_DETAILS_STRING = I18n.tr("Torrent Details");
    static final String SOUNDCLOUD_DETAILS_STRING = I18n.tr("View in Soundcloud");
    static final String ARCHIVEORG_DETAILS_STRING = I18n.tr("View in Archive.org");
    static final String CLOSE_TAB_STRING = I18n.tr("Close Tab");
    static final String CLOSE_ALL_TABS = I18n.tr("Close All Tabs");
    static final String CLOSE_OTHER_TABS_STRING = I18n.tr("Close Other Tabs");
    static final String CLOSE_TABS_TO_THE_RIGHT = I18n.tr("Close Tabs to the Right");
    /**
     * Query text is too short.
     */
    private static final int QUERY_TOO_SHORT = 2;
    /**
     * Query text is too long.
     */
    private static final int QUERY_TOO_LONG = 3;
    private static final SearchMediator instance = new SearchMediator();
    /**
     * This instance handles the display of all search results.
     * TODO: Changed to package-protected for testing to add special results
     */
    private static SearchResultDisplayer RESULT_DISPLAYER;
    private static SearchFilterFactory SEARCH_FILTER_FACTORY;
    private final long MAX_CRAWLCACHE_SIZE = 250 * 1000 * 1024;
    private final SearchManager manager;

    /**
     * Constructs the UI components of the search result display area of the
     * search tab.
     */
    private SearchMediator() {
        // Set the splash screen text...
        final String splashScreenString = I18n.tr("Loading Search Window...");
        GUIMediator.setSplashScreenString(splashScreenString);
        GUIMediator.addRefreshListener(getSearchResultDisplayer());
        // Link up the tabs of results with the filters of the input screen.
        getSearchResultDisplayer().setSearchListener(e -> {
            SearchResultMediator resultPanel = getSearchResultDisplayer().getSelectedResultPanel();
            if (resultPanel != null) {
                resultPanel.updateFiltersPanel();
            }
        });
        new Thread(() -> {
            try {
                DatabaseCrawlCache databaseCrawlCache = new DatabaseCrawlCache();
                if (databaseCrawlCache.sizeInBytes() > MAX_CRAWLCACHE_SIZE) {
                    LOG.info("SearchMediator() - reseting crawl cache, too big");
                    databaseCrawlCache.clear();
                    databaseCrawlCache = new DatabaseCrawlCache();
                    LOG.info("SearchMediator() - crawl cache reset successful");
                }
                CrawlPagedWebSearchPerformer.setCache(databaseCrawlCache);
            } catch (Throwable t) {
                LOG.error("could not set database crawl cache", t);
            }
        },
                "CrawlPagedWebSearchPerformer-initializer").start();
        CrawlPagedWebSearchPerformer.setMagnetDownloader(new LibTorrentMagnetDownloader());
        this.manager = SearchManager.getInstance();
        this.manager.setListener(new SearchListener() {
            @Override
            public void onResults(long token, List<? extends SearchResult> results) {
                SearchMediator.this.onResults(token, results);
            }

            @Override
            public void onError(long token, SearchError error) {
            }

            @Override
            public void onStopped(long token) {
                SearchMediator.this.onFinished(token);
            }
        });
    }

    public static SearchMediator instance() {
        return instance;
    }

    /**
     * Requests the search focus in the INPUT_MANAGER.
     */
    public static void requestSearchFocus() {
        GUIMediator.instance().getMainFrame().getApplicationHeader().requestSearchFocus();
    }

    /**
     * Validates the given search information.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean validate(SearchInformation info) {
        switch (validateInfo(info)) {
            case QUERY_EMPTY:
                return false;
            case QUERY_TOO_SHORT:
                GUIMediator.showMessage(I18n.tr("Your search must be at least three characters to avoid congesting the network."));
                return false;
            case QUERY_TOO_LONG:
                GUIMediator.showMessage(I18n.tr("Your search is too long. Please make your search smaller and try again."));
                return false;
            default:
                return true;
        }
    }

    /**
     * Validates the a search info and returns {@link #QUERY_VALID} if it is
     * valid.
     */
    public static int validateInfo(SearchInformation info) {
        String query = I18NConvert.instance().getNorm(info.getQuery());
        if (query.length() == 0) {
            return QUERY_EMPTY;
        } else if (query.length() > SearchSettings.MAX_QUERY_LENGTH.getValue()) {
            return QUERY_TOO_LONG;
        } else {
            return QUERY_VALID;
        }
    }

    private static String stripHtml(String str) {
        str = str.replaceAll("\\<.*?>", "");
        str = str.replaceAll("\\&.*?\\;", "");
        return str;
    }

    private static void updateSearchIcon(final long token, final boolean active) {
        GUIMediator.safeInvokeAndWait(() -> {
            SearchResultMediator trp = getResultPanelForGUID(token);
            if (trp != null) {
                trp.updateSearchIcon(active);
            }
        });
    }

    private static List<UISearchResult> convertResults(List<? extends SearchResult> results, SearchEngine engine, String query) {
        List<UISearchResult> result = new ArrayList<>();
        for (SearchResult sr : results) {
            UISearchResult ui = null;
            if (sr instanceof SoundcloudSearchResult) {
                ui = new SoundcloudUISearchResult((SoundcloudSearchResult) sr, engine, query);
            } else if (sr instanceof TorrentSearchResult) {
                ui = new TorrentUISearchResult((TorrentSearchResult) sr, engine, query);
            } else if (sr instanceof ArchiveorgCrawledSearchResult) {
                ui = new ArchiveorgUISearchResult((ArchiveorgCrawledSearchResult) sr, engine, query);
            }
            if (ui != null) {
                result.add(ui);
            }
        }
        return result;
    }

    /**
     * Adds a single result tab for the specified GUID, type,
     * standard query string, and XML query string.
     */
    private static void addResultTab(long token, SearchInformation info) {
        List<String> searchTokens = instance().tokenize(info.getQuery());
        getSearchResultDisplayer().addResultTab(token, searchTokens, info);
    }

    /**
     * Downloads all the selected table lines from the given result panel.
     */
    public static void downloadFromPanel(SearchResultMediator rp, SearchResultDataLine[] lines) {
        downloadAll(lines);
        rp.refresh();
    }

    /**
     * Downloads the selected files in the currently displayed
     * <tt>ResultPanel</tt> if there is one.
     */
    static void doDownload(final SearchResultMediator rp) {
        final SearchResultDataLine[] lines = rp.getAllSelectedLines();
        SwingUtilities.invokeLater(() -> {
            SearchMediator.downloadAll(lines);
            rp.refresh();
        });
    }

    /**
     * Downloads all the selected lines.
     */
    private static void downloadAll(SearchResultDataLine[] lines) {
        if (lines == null || lines.length == 0) {
            return;
        }
        GUIMediator.instance().showTransfers(TransfersTab.FilterMode.DOWNLOADING);
        for (SearchResultDataLine line : lines) {
            if (line != null) {
                downloadLine(line);
            }
        }
        if (lines.length == 1) {
            SearchResultDataLine srDataline = lines[0];
            String hash = srDataline.getHash();
            BTDownloadMediator btDownloadMediator = GUIMediator.instance().getBTDownloadMediator();
            List<BTDownload> downloads =
                    btDownloadMediator.getDownloads();
            for (BTDownload d : downloads) {
                if (d.getHash() != null && d.getHash().equals(hash)) {
                    btDownloadMediator.selectBTDownload(d);
                    btDownloadMediator.ensureDownloadVisible(d);
                    return;
                }
            }
        }
    }

    /**
     * Downloads the given TableLine.
     */
    private static void downloadLine(SearchResultDataLine line) {
        if (line == null) {
            throw new NullPointerException("Tried to download null line");
        }
        line.getSearchResult().download(false);
    }

    /**
     * called by ResultPanel when the views are changed. Used to set the
     * tab to indicate the correct number of TableLines in the current
     * view.
     */
    static void setTabDisplayCount(SearchResultMediator rp) {
        getSearchResultDisplayer().setTabDisplayCount(rp);
    }

    /**
     * Notification that a search has been killed.
     */
    static void searchKilled(SearchResultMediator panel) {
        instance().stopSearch(panel.getToken());
        panel.cleanup();
        ApplicationHeader header = GUIMediator.instance().getMainFrame().getApplicationHeader();
        header.requestSearchFocus();
    }

    /**
     * Returns the <tt>ResultPanel</tt> for the specified GUID.
     *
     * @return the <tt>ResultPanel</tt> that matches the GUID, or null
     * if none match.
     */
    private static SearchResultMediator getResultPanelForGUID(long token) {
        return getSearchResultDisplayer().getResultPanelForGUID(token);
    }

    /**
     * Returns the <tt>JComponent</tt> instance containing all of the
     * search result UI components.
     *
     * @return the <tt>JComponent</tt> instance containing all of the
     * search result UI components
     */
    public static JComponent getResultComponent() {
        return getSearchResultDisplayer().getComponent();
    }

    public static SearchResultDisplayer getSearchResultDisplayer() {
        if (RESULT_DISPLAYER == null) {
            RESULT_DISPLAYER = new SearchResultDisplayer();
        }
        return RESULT_DISPLAYER;
    }

    private static SearchFilterFactory getSearchFilterFactory() {
        if (SEARCH_FILTER_FACTORY == null) {
            SEARCH_FILTER_FACTORY = new SearchFilterFactoryImpl();
        }
        return SEARCH_FILTER_FACTORY;
    }

    /**
     * Repeats the given search.
     */
    void repeatSearch(SearchResultMediator rp, SearchInformation info) {
        if (!validate(info)) {
            return;
        }
        stopSearch(rp.getToken());
        long token = newSearchToken();
        rp.setToken(token);
        updateSearchIcon(token, true);
        rp.resetFiltersPanel();
        performSearch(token, info.getQuery());
    }

    /**
     * Initiates a new search with the specified SearchInformation.
     * <p/>
     * Returns the GUID of the search if a search was initiated,
     * otherwise returns null.
     */
    public long triggerSearch(final SearchInformation info) {
        if (!validate(info)) {
            return 0;
        }
        long token = newSearchToken();
        addResultTab(token, info);
        performSearch(token, info.getQuery());
        return token;
    }

    private long newSearchToken() {
        return Math.abs(System.nanoTime());
    }

    private void performSearch(final long token, String query) {
        if (StringUtils.isNullOrEmpty(query, true)) {
            return;
        }
        manager.stop(token);
        for (SearchEngine se : SearchEngine.getEngines()) {
            if (se.isEnabled()) {
                SearchPerformer p = se.getPerformer(token, query);
                manager.perform(p);
            }
        }
    }

    private List<SearchResult> filter(List<SearchResult> results, List<String> searchTokens) {
        List<SearchResult> list;
        if (searchTokens == null || searchTokens.isEmpty()) {
            list = Collections.emptyList();
        } else {
            list = filter2(results, searchTokens);
        }
        return list;
    }
    ////////////////////////// Other Controls ///////////////////////////

    private List<SearchResult> filter2(List<? extends SearchResult> results, List<String> searchTokens) {
        List<SearchResult> list = new LinkedList<>();
        try {
            for (SearchResult sr : results) {
                if (sr instanceof CrawledSearchResult) {
                    // special case for youtube
                    if (filter(new LinkedList<>(searchTokens), sr)) {
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

    private List<String> tokenize(String keywords) {
        keywords = sanitize(keywords);
        Set<String> tokens = new HashSet<>(Arrays.asList(keywords.toLowerCase(Locale.US).split(" ")));
        return new ArrayList<>(normalizeTokens(tokens));
    }

    private Set<String> normalizeTokens(Set<String> tokens) {
        Set<String> normalizedTokens = new HashSet<>();
        for (String token : tokens) {
            String norm = normalize(token);
            normalizedTokens.add(norm);
        }
        return normalizedTokens;
    }

    private String normalize(String token) {
        String norm = Normalizer.normalize(token, Normalizer.Form.NFKD);
        norm = norm.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        norm = norm.toLowerCase(Locale.US);
        return norm;
    }

    void stopSearch(long token) {
        manager.stop(token);
    }

    public void shutdown() {
        manager.stop();
    }

    private void onResults(final long token, List<? extends SearchResult> results) {
        final SearchResultMediator rp = getResultPanelForGUID(token);
        if (rp != null && !rp.isStopped()) {
            @SuppressWarnings("unchecked")
            List<SearchResult> filtered = filter((List<SearchResult>) results, rp.getSearchTokens());
            if (filtered != null && !filtered.isEmpty()) {
                SearchEngine se = SearchEngine.getSearchEngineByName(filtered.get(0).getSource());
                if (se == null) {
                    return;
                }
                final List<UISearchResult> uiResults = convertResults(filtered, se, rp.getQuery());
                GUIMediator.safeInvokeAndWait(() -> {
                    try {
                        SearchFilter filter = getSearchFilterFactory().createFilter();
                        for (UISearchResult sr : uiResults) {
                            if (filter.allow(sr)) {
                                getSearchResultDisplayer().addQueryResult(token, sr, rp);
                            }
                        }
                    } catch (Exception e) {
                        LOG.error("Error adding search result to UI", e);
                    }
                });
            }
        }
    }

    private void onFinished(long token) {
        SearchResultMediator rp = getResultPanelForGUID(token);
        if (rp != null) {
            updateSearchIcon(token, false);
            rp.setToken(0); // to identify that the search is stopped (needs refactor)
        }
    }

    public void clearCache() {
        try {
            CrawlPagedWebSearchPerformer.clearCache();
        } catch (Throwable ignored) {
        }
    }

    public long getTotalTorrents() {
        long r = 0;
        try {
            r = CrawlPagedWebSearchPerformer.getCacheNumEntries();
        } catch (Throwable ignored) {
        }
        return r;
    }
}
