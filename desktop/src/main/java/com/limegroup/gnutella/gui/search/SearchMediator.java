/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.bittorrent.BTDownload;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.filters.SearchFilter;
import com.frostwire.gui.filters.SearchFilterFactory;
import com.frostwire.gui.filters.SearchFilterFactoryImpl;
import com.frostwire.gui.tabs.TransfersTab;
import com.frostwire.search.*;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.internetarchive.InternetArchiveCrawledSearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.ApplicationHeader;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.NamedMediaType;
import com.limegroup.gnutella.settings.SearchSettings;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.I18NConvert;
import org.limewire.util.StringUtils;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
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
    static final String DOWNLOAD_STRING = I18n.tr("Download");
    static final String REPEAT_SEARCH_STRING = I18n.tr("Repeat Search");
    static final String SEARCH_FOR_KEYWORDS = I18n.tr("Search for Keywords: {0}");
    static final String DOWNLOAD_PARTIAL_FILES_STRING = I18n.tr("Download Partial Files");
    static final String TORRENT_DETAILS_STRING = I18n.tr("Torrent Details");
    static final String SOUNDCLOUD_DETAILS_STRING = I18n.tr("View in Soundcloud");
    static final String INTERNET_ARCHIVE_DETAILS_STRING = I18n.tr("View in Archive.org");
    static final String TELLURIDE_DETAILS_STRING = I18n.tr("View in");
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
    private static volatile SearchMediator instance;
    /**
     * This instance handles the display of all search results.
     * TODO: Changed to package-protected for testing to add special results
     */
    private static SearchResultDisplayer RESULT_DISPLAYER;
    private static JComponent RESULT_COMPONENT_PLACEHOLDER;
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

        // Defer SearchResultDisplayer initialization to avoid blocking EDT with class loading
        SwingUtilities.invokeLater(() -> {
            GUIMediator.addRefreshListener(getSearchResultDisplayer());
            // Link up the tabs of results with the filters of the input screen.
            getSearchResultDisplayer().setSearchListener(e -> {
                SearchResultMediator resultPanel = getSearchResultDisplayer().getSelectedResultPanel();
                if (resultPanel != null) {
                    resultPanel.updateFiltersPanel();
                }
            });
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
                CrawlCacheManager.setCache(databaseCrawlCache);
            } catch (Throwable t) {
                LOG.error("could not set database crawl cache", t);
            }
        }, "CrawlCacheManager-initializer").start();
        CrawlCacheManager.setMagnetDownloader(new LibTorrentMagnetDownloader());
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
        if (instance == null) {
            synchronized (SearchMediator.class) {
                if (instance == null) {
                    instance = new SearchMediator();
                }
            }
        }
        return instance;
    }

    /**
     * Update the search title's tab given the token id and the new title
     */
    public void updateSearchPanelTitle(final long token, final String title) {
        GUIMediator.safeInvokeLater(() -> {
            SearchResultMediator resultsPanel = getSearchResultDisplayer().getResultPanelForGUID(token);
            if (resultsPanel == null) {
                LOG.info("updateSearchPanelTitle: could not find SearchResultMediator for token " + token + ", closed prematurely perhaps");
                return;
            }
            SearchMediator.getSearchResultDisplayer().updateSearchTitle(resultsPanel, title);
        });
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
            } else if (sr instanceof InternetArchiveCrawledSearchResult) {
                ui = new InternetArchiveUISearchResult((InternetArchiveCrawledSearchResult) sr, engine, query);
            } else if (sr instanceof TellurideSearchResult) {
                TellurideSearchResult tsr = (TellurideSearchResult) sr;
                ui = new TellurideUISearchResult(tsr, engine, query, false);
                /// if the tsr is an mp4 video, we create an extra TellurideUISearchResult with extractAudioAndDeleteOriginal set to true
                if (FilenameUtils.getExtension(tsr.getFilename()).equals("mp4")) {
                    //See BTDownloadMediator::extractAudioAndRemoveOriginalVideo
                    TellurideSearchResult tsr2 = new TellurideSearchResult(
                            tsr.getId(),
                            "(Faster audio download) " + tsr.getDisplayName() + " (.m4a)",
                            ui.getFilename(),
                            tsr.getSource(),
                            tsr.getDetailsUrl(),
                            tsr.getDownloadUrl(),
                            tsr.getThumbnailUrl(),
                            tsr.getSize(),
                            tsr.getCreationTime());
                    UISearchResult ui2 = new TellurideUISearchResult(tsr2, engine, query, true);
                    result.add(ui2);
                }
            } else if (sr instanceof CompositeFileSearchResult) {
                // V2 flat architecture - CompositeFileSearchResult from V2 search patterns
                CompositeFileSearchResult cfsr = (CompositeFileSearchResult) sr;
                // Create UI wrapper for all CompositeFileSearchResult instances
                // They can be streaming (YouTube), torrents (1337X), or other content types
                ui = new FileSearchResultUIWrapper(cfsr, engine, query);
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
     * `ResultPanel` if there is one.
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

        if (lines.length == 1) {
            UISearchResult uiSearchResult = lines[0].getSearchResult();
            SearchResult sr = uiSearchResult.getSearchResult();
            // Only switch to Transfers for direct downloads, not for preliminary results
            if (!sr.isPreliminary() && !(uiSearchResult instanceof TelluridePartialUISearchResult)) {
                GUIMediator.instance().showTransfers(TransfersTab.FilterMode.DOWNLOADING);
            }
        }

        for (SearchResultDataLine line : lines) {
            if (line != null) {
                downloadLine(line);
            }
        }
        if (lines.length == 1) {
            SearchResultDataLine srDataline = lines[0];
            String hash = srDataline.getHash();
            BTDownloadMediator btDownloadMediator = GUIMediator.instance().getBTDownloadMediator();
            List<BTDownload> downloads = btDownloadMediator.getDownloads();
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
        SearchResult sr = line.getSearchResult().getSearchResult();
        boolean isTorrent = sr instanceof TorrentSearchResult || sr instanceof CrawlableSearchResult;
        line.getSearchResult().download(isTorrent);
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
        instance().stopSearch();
        panel.cleanup();
        ApplicationHeader header = GUIMediator.instance().getMainFrame().getApplicationHeader();
        header.requestSearchFocus();
    }

    /**
     * Returns the `ResultPanel` for the specified GUID.
     *
     * @return the `ResultPanel` that matches the GUID, or null
     * if none match.
     */
    private static SearchResultMediator getResultPanelForGUID(long token) {
        return getSearchResultDisplayer().getResultPanelForGUID(token);
    }

    /**
     * Returns the `JComponent` instance containing all of the
     * search result UI components.
     * Returns a placeholder immediately to avoid blocking EDT.
     * The real component is created asynchronously.
     *
     * @return the `JComponent` instance containing all of the
     * search result UI components
     */
    public static JComponent getResultComponent() {
        if (RESULT_COMPONENT_PLACEHOLDER == null) {
            // Return a placeholder panel immediately to avoid EDT blocking
            RESULT_COMPONENT_PLACEHOLDER = new JPanel();
            RESULT_COMPONENT_PLACEHOLDER.setLayout(new BorderLayout());
            RESULT_COMPONENT_PLACEHOLDER.setBackground(Color.WHITE);

            // Defer the real SearchResultDisplayer creation to avoid EDT blocking with class loading
            SwingUtilities.invokeLater(() -> {
                synchronized (SearchMediator.class) {
                    if (RESULT_DISPLAYER == null && RESULT_COMPONENT_PLACEHOLDER != null) {
                        RESULT_DISPLAYER = new SearchResultDisplayer();
                        // Update placeholder with real component
                        RESULT_COMPONENT_PLACEHOLDER.removeAll();
                        RESULT_COMPONENT_PLACEHOLDER.add(RESULT_DISPLAYER.getComponent(), BorderLayout.CENTER);
                        RESULT_COMPONENT_PLACEHOLDER.revalidate();
                        RESULT_COMPONENT_PLACEHOLDER.repaint();
                    }
                }
            });
        }
        return RESULT_COMPONENT_PLACEHOLDER;
    }

    public static SearchResultDisplayer getSearchResultDisplayer() {
        if (RESULT_DISPLAYER == null) {
            synchronized (SearchMediator.class) {
                if (RESULT_DISPLAYER == null) {
                    // Ensure placeholder exists (in case this is called before getResultComponent)
                    if (RESULT_COMPONENT_PLACEHOLDER == null) {
                        RESULT_COMPONENT_PLACEHOLDER = new JPanel();
                        RESULT_COMPONENT_PLACEHOLDER.setLayout(new BorderLayout());
                        RESULT_COMPONENT_PLACEHOLDER.setBackground(Color.WHITE);
                    }
                    // Create SearchResultDisplayer and update placeholder if needed
                    RESULT_DISPLAYER = new SearchResultDisplayer();
                    if (RESULT_COMPONENT_PLACEHOLDER.getComponentCount() == 0) {
                        RESULT_COMPONENT_PLACEHOLDER.add(RESULT_DISPLAYER.getComponent(), BorderLayout.CENTER);
                        RESULT_COMPONENT_PLACEHOLDER.revalidate();
                        RESULT_COMPONENT_PLACEHOLDER.repaint();
                    }
                }
            }
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
        stopSearch();
        updateSearchIcon(rp.getToken(), true);
        rp.resetFiltersPanel();
        performSearch(rp.getToken(), info.getQuery());
    }

    /**
     * Initiates a new search with the specified SearchInformation.
     * <p>
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

        if (!query.startsWith("http")) {
            manager.stop();
        }

        if (query.startsWith("http") && !query.endsWith(".torrent")) {
            manager.perform(SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.FROSTCLICK_ID).getPerformer(token, query));
            manager.perform(SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.TELLURIDE_ID).getPerformer(token, query));
            return;
        }

        for (SearchEngine se : SearchEngine.getEngines()) {
            if (se.isEnabled() && se.isReady()) {
                ISearchPerformer p = se.getPerformer(token, query);
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

    void stopSearch() {
        manager.stop();
    }

    public void shutdown() {
        manager.stop();
    }

    private void onResults(final long token, List<? extends SearchResult> results) {
        final SearchResultMediator rp = getResultPanelForGUID(token);
        boolean isTellurideSearchResult = results.get(0).getSource().startsWith("Cloud:"); // will be stopped
        if (rp != null && (isTellurideSearchResult || !rp.isStopped())) {
            @SuppressWarnings("unchecked") List<SearchResult> filtered = filter((List<SearchResult>) results, rp.getSearchTokens());
            if (filtered != null && !filtered.isEmpty()) {
                SearchEngine se = SearchEngine.getSearchEngineByName(filtered.get(0).getSource());
                if (se == null) {
                    return;
                }
                final List<UISearchResult> uiResults = convertResults(filtered, se, rp.getQuery());
                GUIMediator.safeInvokeAndWait(() -> {
                    try {
                        SearchFilter filter = getSearchFilterFactory().createFilter();
                        SearchResultDisplayer searchResultDisplayer = getSearchResultDisplayer();
                        int added = 0;
                        NamedMediaType firstMediaType = null;
                        for (UISearchResult sr : uiResults) {
                            if (filter.allow(sr)) {
                                searchResultDisplayer.addQueryResult(token, sr, rp);
                                added++;
                                // Track the first media type to auto-select the appropriate tab
                                if (firstMediaType == null && sr.getExtension() != null) {
                                    firstMediaType = NamedMediaType.getFromExtension(sr.getExtension());
                                }
                            }
                        }
                        // Auto-select the appropriate media type tab for the first result
                        if (firstMediaType != null && added > 0) {
                            rp.selectMediaType(firstMediaType);
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
            rp.stop();
        }
    }

    public void clearCache() {
        try {
            CrawlCacheManager.clearCache();
        } catch (Throwable ignored) {
        }
    }

    public long getTotalTorrents() {
        long r = 0;
        try {
            r = CrawlCacheManager.getCacheNumEntries();
        } catch (Throwable ignored) {
        }
        return r;
    }
}
