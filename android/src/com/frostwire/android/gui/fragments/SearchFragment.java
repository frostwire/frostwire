/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.LocalSearchEngine;
import com.frostwire.android.gui.adapters.OnFeedbackClickAdapter;
import com.frostwire.android.gui.adapters.PromotionDownloader;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
import com.frostwire.android.gui.adapters.SearchResultListAdapter.FilteredSearchResults;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.dialogs.NewTransferDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.tasks.DownloadSoundcloudFromUrlTask;
import com.frostwire.android.gui.tasks.StartDownloadTask;
import com.frostwire.android.gui.tasks.Tasks;
import com.frostwire.android.gui.transfers.HttpSlideSearchResult;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.DirectionDetectorScrollListener;
import com.frostwire.android.gui.util.ScrollDirectionListener;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.KeywordFilterDrawerView;
import com.frostwire.android.gui.views.PromotionsView;
import com.frostwire.android.gui.views.RichNotification;
import com.frostwire.android.gui.views.RichNotificationActionLink;
import com.frostwire.android.gui.views.SearchInputView;
import com.frostwire.android.gui.views.SearchProgressView;
import com.frostwire.android.gui.views.SwipeLayout;
import com.frostwire.android.offers.Offers;
import com.frostwire.frostclick.Slide;
import com.frostwire.frostclick.SlideList;
import com.frostwire.frostclick.TorrentPromotionSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.KeywordDetector;
import com.frostwire.search.KeywordFilter;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.search.youtube.YouTubeSearchResult;
import com.frostwire.util.HistoHashMap;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.http.HttpClient;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import org.apache.commons.io.FilenameUtils;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchFragment extends AbstractFragment implements
        MainFragment,
        OnDialogClickListener,
        SearchProgressView.CurrentQueryReporter, PromotionDownloader, KeywordFilterDrawerView.KeywordFilterDrawerController {
    private static final Logger LOG = Logger.getLogger(SearchFragment.class);
    private SearchResultListAdapter adapter;
    private List<Slide> slides;
    private SearchInputView searchInput;
    private ProgressBar deepSearchProgress;
    private PromotionsView promotions;
    private SearchProgressView searchProgress;
    private ListView list;
    private FilterToolbarButton filterButton;
    private String currentQuery;
    private final FileTypeCounter fileTypeCounter;
    private final KeywordDetector keywordDetector;
    private DrawerLayout drawerLayout;
    private KeywordFilterDrawerView keywordFilterDrawerView;
    private OnClickListener headerClickListener;

    public SearchFragment() {
        super(R.layout.fragment_search);
        fileTypeCounter = new FileTypeCounter();
        currentQuery = null;
        keywordDetector = new KeywordDetector(Engine.instance().getThreadPool());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupAdapter();
        setupPromoSlides();
        setRetainInstance(true);
    }

    private void setupPromoSlides() {
        if (slides != null) {
            promotions.setSlides(slides);
        } else {
            new LoadSlidesTask(this).execute();
        }
    }

    @Override
    public View getHeader(Activity activity) {
        LayoutInflater inflater = LayoutInflater.from(activity);

        LinearLayout header = (LinearLayout) inflater.inflate(R.layout.view_search_header, null, false);
        TextView title = (TextView) header.findViewById(R.id.view_search_header_text_title);
        title.setText(R.string.search);

        title.setOnClickListener(getHeaderClickListener());

        ImageButton filterButtonIcon = (ImageButton) header.findViewById(R.id.view_search_header_search_filter_button);
        TextView filterCounter = (TextView) header.findViewById(R.id.view_search_header_search_filter_counter);
        filterButton = new FilterToolbarButton(filterButtonIcon, filterCounter);
        filterButton.updateVisibility();

        return header;
    }


    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null && (adapter.getCount() > 0 || adapter.getTotalCount() > 0)) {
            refreshFileTypeCounters(true);
            searchInput.selectTabByMediaType((byte) ConfigurationManager.instance().getLastMediaTypeFilter());
            filterButton.reset(false);
            updateKeywordDetector(adapter.getList());
            filterButton.updateVisibility();
        } else {
            setupPromoSlides();
        }
        if (list != null && ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_DISTRACTION_FREE_SEARCH)) {
            list.setOnScrollListener(
                    DirectionDetectorScrollListener.createOnScrollListener(
                            createScrollDirectionListener(),
                            Engine.instance().getThreadPool()));
        }
    }

    @Override
    public void onDestroy() {
        LocalSearchEngine.instance().setListener(null);
        super.onDestroy();
    }

    @Override
    public void onShow() {
    }

    @Override
    protected void initComponents(final View view, Bundle savedInstanceState) {
        searchInput = findView(view, R.id.fragment_search_input);
        searchInput.setShowKeyboardOnPaste(true);
        searchInput.setOnSearchListener(new SearchInputOnSearchListener((LinearLayout) view, this));

        deepSearchProgress = findView(view, R.id.fragment_search_deepsearch_progress);
        deepSearchProgress.setVisibility(View.GONE);
        promotions = findView(view, R.id.fragment_search_promos);
        // Click Listeners of the inner promos need this reference because there's too much logic
        // on starting a download already here. See PromotionsView.setupView()
        promotions.setPromotionDownloader(this);
        searchProgress = findView(view, R.id.fragment_search_search_progress);
        searchProgress.setCurrentQueryReporter(this);
        searchProgress.setCancelOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (LocalSearchEngine.instance().isSearchFinished()) {
                    performSearch(searchInput.getText(), adapter.getFileType()); // retry
                } else {
                    cancelSearch();
                }
            }
        });
        list = findView(view, R.id.fragment_search_list);
        SwipeLayout swipe = findView(view, R.id.fragment_search_swipe);
        swipe.setOnSwipeListener(new SwipeLayout.OnSwipeListener() {
            @Override
            public void onSwipeLeft() {
                switchToThe(true);
            }

            @Override
            public void onSwipeRight() {
                switchToThe(false);
            }
        });
        showSearchView(view);
        showRatingsReminder(view);
    }

    private void startMagnetDownload(String magnet) {
        UIUtils.showLongMessage(getActivity(), R.string.torrent_url_added);
        TransferManager.instance().downloadTorrent(magnet,
                new HandpickedTorrentDownloadDialogOnFetch(getActivity()));
    }

    private static String extractYTId(String ytUrl) {
        String vId = null;
        Pattern pattern = Pattern.compile(".*(?:youtu.be\\/|v\\/|u\\/\\w\\/|embed\\/|watch\\?v=)([^#\\&\\?]*).*");
        Matcher matcher = pattern.matcher(ytUrl);
        if (matcher.matches()) {
            vId = matcher.group(1);
        }
        return vId;
    }

    private void setupAdapter() {
        if (adapter == null) {
            adapter = new SearchResultListAdapter(getActivity()) {
                @Override
                protected void searchResultClicked(SearchResult sr) {
                    startTransfer(sr, getString(R.string.download_added_to_queue));
                }
            };
            LocalSearchEngine.instance().setListener(new SearchListener() {
                @Override
                public void onResults(long token, final List<? extends SearchResult> results) {
                    onSearchResults(results);
                }

                @Override
                public void onError(long token, SearchError error) {
                    LOG.error("Some error in search stream: " + error);
                }

                @Override
                public void onStopped(long token) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            searchProgress.setProgressEnabled(false);
                            deepSearchProgress.setVisibility(View.GONE);
                        }
                    });
                }
            });
        }
        list.setAdapter(adapter);
    }

    private void onSearchResults(final List<? extends SearchResult> results) {
        FilteredSearchResults fsr = adapter.filter((List<SearchResult>) results);
        final List<SearchResult> filteredList = fsr.filtered;
        fileTypeCounter.add(fsr);
        // if it's a fresh search, make sure to clear keyword detector
        if (adapter.getCount() == 0 && adapter.getKeywordFiltersPipeline().size() == 0) {
            resetKeywordDetector();
        }

        updateKeywordDetector(results);

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.addResults(results, filteredList);
                showSearchView(getView());
                refreshFileTypeCounters(true);
            }
        });
    }

    private void updateKeywordDetector(final List<? extends SearchResult> results) {
        Engine.instance().getThreadPool().submit(new Runnable() {
            @Override
            public void run() {
                if (filterButton != null) {
                    keywordDetector.setKeywordDetectorListener(filterButton);
                }
                if (results != null) {
                    boolean searchFinished = LocalSearchEngine.instance().isSearchFinished();

                    // the second condition exists to accomodate a reset keywordDetector upon screen rotation
                    if (!searchFinished || (keywordDetector.totalHistogramKeys() == 0 && results.size() > 0)) {
                        for (SearchResult sr : results) {
                            keywordDetector.addSearchTerms(KeywordDetector.Feature.SEARCH_SOURCE, sr.getSource().toLowerCase());
                            if (sr instanceof FileSearchResult) {
                                String fileName = ((FileSearchResult) sr).getFilename().toLowerCase();
                                String ext = FilenameUtils.getExtension(fileName).toLowerCase();
                                if (fileName != null && !fileName.isEmpty()) {
                                    keywordDetector.addSearchTerms(KeywordDetector.Feature.FILE_NAME, fileName);
                                }
                                if (ext != null && !ext.isEmpty()) {
                                    keywordDetector.addSearchTerms(KeywordDetector.Feature.FILE_EXTENSION, FilenameUtils.getExtension(fileName));
                                }
                            }
                        }
                        keywordDetector.requestHistogramUpdate(KeywordDetector.Feature.SEARCH_SOURCE, false);
                        keywordDetector.requestHistogramUpdate(KeywordDetector.Feature.FILE_EXTENSION, false);
                        keywordDetector.requestHistogramUpdate(KeywordDetector.Feature.FILE_NAME, false);
                    } else {
                        keywordDetector.notifyListener();
                    }
                } else {
                    keywordDetector.notifyListener();
                }
            }
        });
    }

    private ScrollDirectionListener createScrollDirectionListener() {
        return new ScrollDirectionListener() {
            @Override
            public void onScrollUp() {
                onSearchScrollUp();
            }

            @Override
            public void onScrollDown() {
                onSearchScrollDown();
            }
        };
    }

    private void onSearchScrollDown() {
        hideSearchBox();
    }

    private void onSearchScrollUp() {
        showSearchBox();
    }

    private void showSearchBox() {
        searchInput.showTextInput();
    }

    private void hideSearchBox() {
        searchInput.hideTextInput();
    }

    private void refreshFileTypeCounters(boolean fileTypeCountersVisible) {
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_APPLICATIONS, fileTypeCounter.fsr.numApplications);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_AUDIO, fileTypeCounter.fsr.numAudio);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_DOCUMENTS, fileTypeCounter.fsr.numDocuments);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_PICTURES, fileTypeCounter.fsr.numPictures);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_TORRENTS, fileTypeCounter.fsr.numTorrents);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_VIDEOS, fileTypeCounter.fsr.numVideo);
        searchInput.setFileTypeCountersVisible(fileTypeCountersVisible);
    }

    public void performYTSearch(String query) {
        String ytId = extractYTId(query);
        if (ytId != null) {
            searchInput.setText("");
            searchInput.selectTabByMediaType(Constants.FILE_TYPE_VIDEOS);
            performSearch(ytId, Constants.FILE_TYPE_VIDEOS);
            searchInput.setText("youtube:" + ytId);
        }
    }

    private void performSearch(String query, int mediaTypeId) {
        adapter.clear();
        adapter.setFileType(mediaTypeId);
        fileTypeCounter.clear();
        refreshFileTypeCounters(false);
        resetKeywordDetector();

        List<KeywordFilter> keywordFilters = KeywordFilter.parseKeywordFilters(query);
        if (!keywordFilters.isEmpty()) {
            query = KeywordFilter.cleanQuery(query, keywordFilters);
            adapter.setKeywordFiltersPipeline(keywordFilters);
        }

        currentQuery = query;
        LocalSearchEngine.instance().performSearch(query);
        searchProgress.setProgressEnabled(true);
        showSearchView(getView());
        UXStats.instance().log(UXAction.SEARCH_STARTED_ENTER_KEY);
    }

    private void cancelSearch() {
        adapter.clear();
        fileTypeCounter.clear();
        refreshFileTypeCounters(false);
        resetKeywordDetector();
        currentQuery = null;
        LocalSearchEngine.instance().cancelSearch();
        searchProgress.setProgressEnabled(false);
        showSearchView(getView());
        filterButton.reset(true); // hide=true
    }

    private void showSearchView(View view) {
        boolean searchFinished = LocalSearchEngine.instance().isSearchFinished();

        if (LocalSearchEngine.instance().isSearchStopped()) {
            switchView(view, R.id.fragment_search_promos);
            deepSearchProgress.setVisibility(View.GONE);
        } else {
            boolean adapterHasResults = adapter != null && adapter.getCount() > 0;
            if (adapterHasResults) {
                switchView(view, R.id.fragment_search_list);
                deepSearchProgress.setVisibility(searchFinished ? View.GONE : View.VISIBLE);
                filterButton.updateVisibility();
            } else {
                switchView(view, R.id.fragment_search_search_progress);
                deepSearchProgress.setVisibility(View.GONE);
            }
        }
        searchProgress.setProgressEnabled(!searchFinished);
    }

    private void switchView(View v, int id) {
        if (v != null) {
            FrameLayout frameLayout = findView(v, R.id.fragment_search_framelayout);
            int childCount = frameLayout.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View childAt = frameLayout.getChildAt(i);
                childAt.setVisibility((childAt.getId() == id) ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    private OnClickListener getHeaderClickListener() {
        if (headerClickListener == null) {
            headerClickListener = new OnClickListener() {
                private int clickCount = 0;
                @Override
                public void onClick(View v) {
                    clickCount++;
                    LOG.info("header.onClick() - clickCount => " + clickCount);
                    if (clickCount % 5 == 0) {
                        Offers.showInterstitial(getActivity(), Offers.PLACEMENT_INTERSTITIAL_EXIT, false, false);
                    }
                }
            };
        }
        return headerClickListener;
    }

    @Override
    public void onDialogClick(String tag, int which) {
        if (tag.equals(NewTransferDialog.TAG) && which == Dialog.BUTTON_POSITIVE) {
            if (Ref.alive(NewTransferDialog.srRef)) {
                startDownload(this.getActivity(), NewTransferDialog.srRef.get(), getString(R.string.download_added_to_queue));
                LocalSearchEngine.instance().markOpened(NewTransferDialog.srRef.get(), adapter);
            }
        }
    }

    private void startTransfer(final SearchResult sr, final String toastMessage) {
        Engine.instance().getVibrator().hapticFeedback();
        if (!(sr instanceof AbstractTorrentSearchResult || sr instanceof TorrentPromotionSearchResult) &&
                ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG)) {
            if (sr instanceof FileSearchResult && !(sr instanceof YouTubeSearchResult)) {
                try {
                    NewTransferDialog dlg = NewTransferDialog.newInstance((FileSearchResult) sr, false);
                    dlg.show(getFragmentManager());
                } catch (IllegalStateException e) {
                    // android.app.FragmentManagerImpl.checkStateLoss:1323 -> java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
                    // just start the download then if the dialog crapped out.
                    onDialogClick(NewTransferDialog.TAG, Dialog.BUTTON_POSITIVE);
                }
            } else if (sr instanceof YouTubeSearchResult) {
                startDownload(getActivity(), sr, toastMessage);
            }
        } else {
            if (isVisible()) {
                startDownload(getActivity(), sr, toastMessage);
            }
        }
        uxLogAction(sr);
    }

    public static void startDownload(Context ctx, SearchResult sr, String message) {
        if (sr instanceof AbstractTorrentSearchResult) {
            UIUtils.showShortMessage(ctx, R.string.fetching_torrent_ellipsis);
        }
        StartDownloadTask task = new StartDownloadTask(ctx, sr, message);
        Tasks.executeParallel(task);
    }

    private void showRatingsReminder(View v) {
        final RichNotification ratingReminder = findView(v, R.id.fragment_search_rating_reminder_notification);
        ratingReminder.setVisibility(View.GONE);
        final ConfigurationManager CM = ConfigurationManager.instance();
        boolean alreadyRated = CM.getBoolean(Constants.PREF_KEY_GUI_ALREADY_RATED_US_IN_MARKET);
        if (alreadyRated || ratingReminder.wasDismissed()) {
            return;
        }
        final int finishedDownloads = Engine.instance().getNotifiedDownloadsBloomFilter().count();
        final int intervalFactor = Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? 4 : 1;
        final int REMINDER_INTERVAL = intervalFactor * CM.getInt(Constants.PREF_KEY_GUI_FINISHED_DOWNLOADS_BETWEEN_RATINGS_REMINDER);
        //LOG.info("successful finishedDownloads: " + finishedDownloads);
        if (finishedDownloads < REMINDER_INTERVAL) {
            return;
        }
        ClickAdapter<SearchFragment> onRateAdapter = createOnRateClickAdapter(ratingReminder, CM);
        ratingReminder.setOnClickListener(onRateAdapter);
        RichNotificationActionLink rateFrostWireActionLink =
                new RichNotificationActionLink(ratingReminder.getContext(),
                        getString(R.string.love_frostwire),
                        onRateAdapter);
        RichNotificationActionLink sendFeedbackActionLink =
                new RichNotificationActionLink(ratingReminder.getContext(),
                        getString(R.string.send_feedback),
                        createOnFeedbackClickAdapter(ratingReminder, CM));
        ratingReminder.updateActionLinks(rateFrostWireActionLink, sendFeedbackActionLink);
        ratingReminder.setVisibility(View.VISIBLE);
    }

    // takes user to Google Play store so it can rate the app.
    private ClickAdapter<SearchFragment> createOnRateClickAdapter(final RichNotification ratingReminder, final ConfigurationManager CM) {
        return new OnRateClickAdapter(SearchFragment.this, ratingReminder, CM);
    }

    // opens default email client and pre-fills email to support@frostwire.com
    // with some information about the app and environment.
    private OnFeedbackClickAdapter createOnFeedbackClickAdapter(final RichNotification ratingReminder, final ConfigurationManager CM) {
        return new OnFeedbackClickAdapter(SearchFragment.this, ratingReminder, CM);
    }

    public void startPromotionDownload(Slide slide) {
        SearchResult sr;
        switch (slide.method) {
            case Slide.DOWNLOAD_METHOD_TORRENT:
                sr = new TorrentPromotionSearchResult(slide);
                break;
            case Slide.DOWNLOAD_METHOD_HTTP:
                sr = new HttpSlideSearchResult(slide);
                break;
            default:
                sr = null;
                break;
        }
        if (sr == null) {
            //check if there is a URL available to open a web browser.
            if (slide.clickURL != null) {
                Intent i = new Intent("android.intent.action.VIEW", Uri.parse(slide.clickURL));
                try {
                    getActivity().startActivity(i);
                } catch (Throwable t) {
                    // some devices incredibly may have no apps to handle this intent.
                }
            }
            return;
        }
        String stringDownloadingPromo;
        try {
            stringDownloadingPromo = getString(R.string.downloading_promotion, sr.getDisplayName());
        } catch (Throwable e) {
            stringDownloadingPromo = getString(R.string.azureus_manager_item_downloading);
        }
        startTransfer(sr, stringDownloadingPromo);
    }

    private void uxLogAction(SearchResult sr) {
        UXStats.instance().log(UXAction.SEARCH_RESULT_CLICKED);
        if (sr instanceof HttpSearchResult) {
            UXStats.instance().log(UXAction.DOWNLOAD_CLOUD_FILE);
        } else if (sr instanceof TorrentSearchResult) {
            if (sr instanceof TorrentCrawledSearchResult) {
                UXStats.instance().log(UXAction.DOWNLOAD_PARTIAL_TORRENT_FILE);
            } else {
                UXStats.instance().log(UXAction.DOWNLOAD_FULL_TORRENT_FILE);
            }
        }
    }

    @Override
    public String getCurrentQuery() {
        return currentQuery;
    }

    private void switchToThe(boolean right) {
        searchInput.switchToThe(right);
    }

    public void connectDrawerLayoutFilterView(DrawerLayout drawerLayout, View filterView) {
        this.drawerLayout = drawerLayout;
        keywordFilterDrawerView = (KeywordFilterDrawerView) filterView;
        keywordFilterDrawerView.setKeywordFilterDrawerController(this);
    }

    @Override
    public void closeKeywordFilterDrawer() {
        if (keywordFilterDrawerView != null) {
            drawerLayout.closeDrawer(keywordFilterDrawerView);
        }
    }

    @Override
    public void openKeywordFilterDrawer() {
        if (drawerLayout == null || keywordFilterDrawerView == null) {
            return;
        }

        if (keywordFilterDrawerView != null) {
            drawerLayout.openDrawer(keywordFilterDrawerView);
        }

        if (keywordDetector != null) {
            keywordDetector.requestHistogramUpdate(KeywordDetector.Feature.SEARCH_SOURCE, true);
            keywordDetector.requestHistogramUpdate(KeywordDetector.Feature.FILE_EXTENSION, true);
            keywordDetector.requestHistogramUpdate(KeywordDetector.Feature.FILE_NAME, true);
        }

    }

    private static class SearchInputOnSearchListener implements SearchInputView.OnSearchListener {
        private final LinearLayout parentView;
        private final SearchFragment fragment;

        SearchInputOnSearchListener(LinearLayout parentView, SearchFragment fragment) {
            this.parentView = parentView;
            this.fragment = fragment;
        }

        public void onSearch(View v, String query, int mediaTypeId) {
            fragment.resetKeywordDetector();
            if (query.contains("://m.soundcloud.com/") || query.contains("://soundcloud.com/")) {
                fragment.cancelSearch();
                new DownloadSoundcloudFromUrlTask(fragment.getActivity(), query).execute();
                fragment.searchInput.setText("");
            } else if (query.contains("youtube.com/")) {
                fragment.performYTSearch(query);
            } else if (query.startsWith("magnet:?xt=urn:btih:")) {
                fragment.startMagnetDownload(query);
                fragment.currentQuery = null;
                fragment.searchInput.setText("");
            } else {
                fragment.performSearch(query, mediaTypeId);
            }
        }

        public void onMediaTypeSelected(View view, int mediaTypeId) {
            ConfigurationManager.instance().setLastMediaTypeFilter(mediaTypeId);
            fragment.adapter.setFileType(mediaTypeId);
            fragment.showSearchView(parentView);
        }

        public void onClear(View v) {
            fragment.cancelSearch();
        }
    }

    private void resetKeywordDetector() {
        keywordDetector.reset();
        keywordFilterDrawerView.reset();
    }

    private static class LoadSlidesTask extends AsyncTask<Void, Void, List<Slide>> {

        private final WeakReference<SearchFragment> fragment;

        LoadSlidesTask(SearchFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        protected List<Slide> doInBackground(Void... params) {
            try {
                HttpClient http = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
                String url = String.format("%s?from=android&fw=%s&sdk=%s", Constants.SERVER_PROMOTIONS_URL, Constants.FROSTWIRE_VERSION_STRING, Build.VERSION.SDK_INT);
                String json = http.get(url);
                SlideList slides = JsonUtils.toObject(json, SlideList.class);
                // HACK: Gets rid of the old "see more search results" slide.
                // TODO: Remove this when unnecessary after several updates
                if (slides != null && slides.slides != null) {
                    Iterator<Slide> it = slides.slides.iterator();
                    while (it.hasNext()) {
                        Slide slide = it.next();
                        if (slide.imageSrc.equals("http://static.frostwire.com/images/overlays/fw-results-overlay-2.jpg")) {
                            it.remove();
                        }
                    }
                }
                // yes, these requests are done only once per session.
                //LOG.info("SearchFragment.LoadSlidesTask performed http request to " + url);
                return slides != null ? slides.slides : null;
            } catch (Throwable e) {
                LOG.error("Error loading slides from url", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<Slide> result) {
            SearchFragment f;
            if (result != null && !result.isEmpty() && (f = fragment.get()) != null) {
                f.slides = result;
                f.promotions.setSlides(result);
            }
        }
    }

    private static final class FileTypeCounter {

        private final FilteredSearchResults fsr = new FilteredSearchResults();

        public void add(FilteredSearchResults fsr) {
            this.fsr.numAudio += fsr.numAudio;
            this.fsr.numApplications += fsr.numApplications;
            this.fsr.numDocuments += fsr.numDocuments;
            this.fsr.numPictures += fsr.numPictures;
            this.fsr.numTorrents += fsr.numTorrents;
            this.fsr.numVideo += fsr.numVideo;
        }

        public void clear() {
            this.fsr.numAudio = 0;
            this.fsr.numApplications = 0;
            this.fsr.numDocuments = 0;
            this.fsr.numPictures = 0;
            this.fsr.numTorrents = 0;
            this.fsr.numVideo = 0;
        }
    }

    private static class OnRateClickAdapter extends ClickAdapter<SearchFragment> {
        private final WeakReference<RichNotification> ratingReminderRef;
        private final ConfigurationManager CM;

        OnRateClickAdapter(final SearchFragment owner, final RichNotification ratingReminder, final ConfigurationManager CM) {
            super(owner);
            ratingReminderRef = Ref.weak(ratingReminder);
            this.CM = CM;
        }

        @Override
        public void onClick(SearchFragment owner, View v) {
            if (Ref.alive(ratingReminderRef)) {
                ratingReminderRef.get().setVisibility(View.GONE);
            }
            CM.setBoolean(Constants.PREF_KEY_GUI_ALREADY_RATED_US_IN_MARKET, true);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + Constants.APP_PACKAGE_NAME));
            try {
                owner.startActivity(intent);
            } catch (Throwable ignored) {
            }
        }
    }

    private class FilterToolbarButton implements KeywordDetector.KeywordDetectorListener, KeywordFilterDrawerView.KeywordFiltersPipelineListener {
        private ImageButton imageButton;
        private TextView counterTextView;
        private long lastKeywordFilterDrawerViewUpdate;

        FilterToolbarButton(ImageButton imageButton, TextView counterTextView) {
            this.imageButton = imageButton;
            this.counterTextView = counterTextView;
            initListeners();
        }

        // self determine if it should be hidden or not
        public void updateVisibility() {
            setVisible(keywordDetector.totalHistogramKeys() > 0);
        }

        @Override
        public void onHistogramUpdate(final KeywordDetector detector, final KeywordDetector.Feature feature, final Entry<String, Integer>[] histogram, boolean forceUIUpdate) {
            long now = SystemClock.currentThreadTimeMillis();
            if (!forceUIUpdate && now - lastKeywordFilterDrawerViewUpdate < 2000) {
                // expensive operation for main thread, ignore sub-second requests for refreshing
                return;
            }
            lastKeywordFilterDrawerViewUpdate = now;
            Runnable uiRunnable = new Runnable() {
                @Override
                public void run() {
                    updateVisibility();
                    keywordFilterDrawerView.updateData(getKeywordFiltersPipeline(), feature, histogram);
                }
            };
            getActivity().runOnUiThread(uiRunnable);
        }

        @Override
        public void notify(final KeywordDetector detector, Map<KeywordDetector.Feature, HistoHashMap<String>> histograms) {
            if (histograms != null && !histograms.isEmpty()) {
                Set<KeywordDetector.Feature> features = histograms.keySet();
                for (KeywordDetector.Feature feature : features) {
                    onHistogramUpdate(detector, feature, histograms.get(feature).histogram(), false);
                }
            }
        }

        public void reset(boolean hide) { //might do, parameter to not hide drawer
            setVisible(!hide);
            keywordDetector.reset();
            closeKeywordFilterDrawer();
        }

        public void reset() {
            reset(true);
        }

        @Override
        public void onPipelineUpdate(List<KeywordFilter> pipeline) {
            adapter.setKeywordFiltersPipeline(pipeline);
            if (pipeline != null) {
                if (pipeline.isEmpty()) {
                    counterTextView.setText("");
                } else {
                    counterTextView.setText("" + pipeline.size());
                }
            }
            updateVisibility();
        }

        @Override
        public void onAddKeywordFilter(KeywordFilter keywordFilter) {
            adapter.addKeywordFilter(keywordFilter);
        }

        @Override
        public void onRemoveKeywordFilter(KeywordFilter keywordFilter) {
            adapter.removeKeywordFilter(keywordFilter);
        }

        @Override
        public List<KeywordFilter> getKeywordFiltersPipeline() {
            if (adapter == null) {
                return Collections.EMPTY_LIST;
            }
            return adapter.getKeywordFiltersPipeline();
        }

        private void setVisible(boolean visible) {
            int visibility = visible ? View.VISIBLE : View.GONE;
            imageButton.setVisibility(visibility);
            if (visible) {
                counterTextView.setVisibility(getKeywordFiltersPipeline().size() > 0 ? View.VISIBLE : View.GONE);
                counterTextView.setText(String.valueOf(getKeywordFiltersPipeline().size()));
            } else {
                counterTextView.setVisibility(View.GONE);
            }
        }

        private void initListeners() {
            imageButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    openKeywordFilterDrawerView();
                }
            });
        }

        private void openKeywordFilterDrawerView() {
            keywordFilterDrawerView.setKeywordFiltersPipelineListener(this);
            openKeywordFilterDrawer();
        }
    }
}
