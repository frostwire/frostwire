/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
import com.frostwire.android.gui.tasks.AsyncDownloadSoundcloudFromUrl;
import com.frostwire.android.gui.tasks.AsyncStartDownload;
import com.frostwire.android.gui.transfers.HttpSlideSearchResult;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.DirectionDetectorScrollListener;
import com.frostwire.android.gui.util.ScrollListeners.ComposedOnScrollListener;
import com.frostwire.android.gui.util.ScrollListeners.FastScrollDisabledWhenIdleOnScrollListener;
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
import com.frostwire.android.offers.SearchHeaderBanner;
import com.frostwire.frostclick.Slide;
import com.frostwire.frostclick.SlideList;
import com.frostwire.frostclick.TorrentPromotionSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.KeywordDetector;
import com.frostwire.search.KeywordFilter;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.http.HttpClient;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.drawerlayout.widget.DrawerLayout;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchFragment extends AbstractFragment implements
        MainFragment,
        OnDialogClickListener,
        SearchProgressView.CurrentQueryReporter, PromotionDownloader, KeywordFilterDrawerView.KeywordFilterDrawerController, DrawerLayout.DrawerListener {
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
    private SearchHeaderBanner searchHeaderBanner;

    public SearchFragment() {
        super(R.layout.fragment_search);
        fileTypeCounter = new FileTypeCounter();
        currentQuery = null;
        keywordDetector = new KeywordDetector();
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
            //new LoadSlidesTask(this).execute();
            async(this, SearchFragment::loadSlidesInBackground, SearchFragment::onSlidesLoaded);
        }
    }

    private List<Slide> loadSlidesInBackground() {
        try {
            HttpClient http = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
            String url = String.format("%s&from=android&fw=%s&sdk=%s", Constants.SERVER_PROMOTIONS_URL, Constants.FROSTWIRE_VERSION_STRING, Build.VERSION.SDK_INT);
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

    private void onSlidesLoaded(List<Slide> result) {
        if (result != null && !result.isEmpty()) {
            slides = result;
        } else {
            slides = new ArrayList<>(0);
        }
        promotions.setSlides(slides);
        promotions.invalidate();
    }

    @Override
    public View getHeader(Activity activity) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        LinearLayout header = (LinearLayout) inflater.inflate(R.layout.view_search_header, null, false);
        TextView title = header.findViewById(R.id.view_search_header_text_title);
        title.setText(R.string.search);
        title.setOnClickListener(getHeaderClickListener());
        ImageButton filterButtonIcon = header.findViewById(R.id.view_search_header_search_filter_button);
        TextView filterCounter = header.findViewById(R.id.view_search_header_search_filter_counter);
        filterButton = new FilterToolbarButton(filterButtonIcon, filterCounter);
        filterButton.updateVisibility();
        return header;
    }


    @Override
    public void onResume() {
        super.onResume();
        // getHeader was conceived for quick update of main fragments headers,
        // mainly in a functional style, but it is ill suited to extract from
        // it a mutable state, like filterButton.
        // As a result, you will get multiple NPE during the normal lifestyle
        // of the fragmentRef, since getHeader is not guaranteed to be called
        // at the right time during a full resume of the fragmentRef.
        // TODO: refactor this
        if (filterButton == null && isAdded() && getActivity() != null) { // best effort
            // this will happen due to the call to onTabReselected on full resume
            // and this is only solving the NPE, the drawback is that it will
            // create a few orphan view objects to be GC'ed soon.
            // it'is a poor solution overall, but the right one requires
            // a big refactor.
            getHeader(getActivity());
        }
        ConfigurationManager CM = ConfigurationManager.instance();
        if (adapter != null && (adapter.getCount() > 0 || adapter.getTotalCount() > 0)) {
            refreshFileTypeCounters(true);
            searchInput.selectTabByMediaType((byte) CM.getLastMediaTypeFilter());
            filterButton.reset(false);
            boolean filtersApplied = !adapter.getKeywordFiltersPipeline().isEmpty();
            if (filtersApplied) {
                updateKeywordDetector(adapter.filter().keywordFiltered);
            } else {
                updateKeywordDetector(adapter.getList());
            }
            searchProgress.setKeywordFiltersApplied(filtersApplied);
            filterButton.updateVisibility();
            keywordFilterDrawerView.updateAppliedKeywordFilters(adapter.getKeywordFiltersPipeline());
        } else {
            setupPromoSlides();
        }

        if (list != null) {
            list.setOnScrollListener(new FastScrollDisabledWhenIdleOnScrollListener());
        }

        if (list != null && CM.getBoolean(Constants.PREF_KEY_GUI_DISTRACTION_FREE_SEARCH)) {
            list.setOnScrollListener(
                    new ComposedOnScrollListener(new FastScrollDisabledWhenIdleOnScrollListener(),
                            new DirectionDetectorScrollListener(
                                    new ScrollDirectionListener(this),
                                    Engine.instance().getThreadPool())
                    )
            );
        }
        if (searchHeaderBanner != null) {
            searchHeaderBanner.setSearchFragmentReference(this);
            if (getCurrentQuery() == null || Offers.disabledAds()){
                searchHeaderBanner.setBannerViewVisibility(SearchHeaderBanner.BannerType.ALL, false);
            }
        }
        if (getCurrentQuery() == null) {
            searchInput.setFileTypeCountersVisible(false);
        }
    }

    @Override
    public void onDestroy() {
        LocalSearchEngine.instance().setListener(null);
        keywordDetector.shutdownHistogramUpdateRequestDispatcher();
        destroyHeaderBanner();
        destroyPromotionsBanner();
        super.onDestroy();
    }

    public void destroyHeaderBanner() {
        if (searchHeaderBanner != null) {
            searchHeaderBanner.setSearchFragmentReference(this);
            searchHeaderBanner.onDestroy();
        }
    }

    public void destroyPromotionsBanner() {
        if (promotions != null) {
            promotions.destroyPromotionsBanner();
        }
    }

    @Override
    public void onShow() {
    }

    @Override
    protected void initComponents(final View view, Bundle savedInstanceState) {
        searchHeaderBanner = findView(view, R.id.fragment_search_header_banner);
        searchHeaderBanner.setSearchFragmentReference(this);
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
        searchProgress.setCancelOnClickListener(v -> {
            if (LocalSearchEngine.instance().isSearchFinished()) {
                performSearch(searchInput.getText(), adapter.getFileType()); // retry
            } else {
                cancelSearch();
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
    }

    private void startMagnetDownload(String magnet) {
        UIUtils.showLongMessage(getActivity(), R.string.torrent_url_added);
        TransferManager.instance().downloadTorrent(magnet,
                new HandpickedTorrentDownloadDialogOnFetch(getActivity()));
    }

    private static String extractYTId(String ytUrl) {
        String vId = null;
        //noinspection RegExpRedundantEscape
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
            LocalSearchEngine.instance().setListener(new LocalSearchEngineListener(this));
        }
        list.setAdapter(adapter);
    }

    private void onSearchResults(final List<SearchResult> results) {
        FilteredSearchResults fsr = adapter.filter(results);
        final List<SearchResult> mediaTypeFiltered = fsr.filtered;
        final List<SearchResult> keywordFiltered = fsr.keywordFiltered;
        fileTypeCounter.add(fsr);
        // if it's a fresh search, make sure to clear keyword detector
        if (adapter.getCount() == 0 && adapter.getKeywordFiltersPipeline().size() == 0) {
            resetKeywordDetector();
        }
        if (adapter.getKeywordFiltersPipeline().isEmpty()) {
            updateKeywordDetector(results);
        } else {
            updateKeywordDetector(keywordFiltered);
        }
        if (isAdded()) {
            getActivity().runOnUiThread(() -> {
                adapter.addResults(keywordFiltered, mediaTypeFiltered);
                showSearchView(getView());
                refreshFileTypeCounters(true);
            });
        }
    }

    private void updateKeywordDetector(final List<? extends SearchResult> results) {
        if (filterButton != null) {
            keywordDetector.setKeywordDetectorListener(filterButton);
        }
        if (results != null) {
            boolean searchFinished = LocalSearchEngine.instance().isSearchFinished();
            // the second condition exists to accommodate a reset keywordDetector upon screen rotation
            if (!searchFinished || (keywordDetector.totalHistogramKeys() == 0 && results.size() > 0)) {
                updateKeywordDetectorWithSearchResults(this, results);
            }
        }
    }

    @Override
    public void onDrawerSlide(View view, float v) {
        if ((!isVisible() || currentQuery == null) && view == keywordFilterDrawerView) {
            drawerLayout.closeDrawer(view);
        }
    }

    @Override
    public void onDrawerOpened(View view) {
    }

    @Override
    public void onDrawerClosed(View view) {
        if (view == keywordFilterDrawerView) {
            searchInput.selectTabByMediaType((byte) adapter.getFileType());
        }
        filterButton.updateVisibility();
    }

    @Override
    public void onDrawerStateChanged(int i) {
    }

    /**
     * When submitting an anonymous Runnable class to the threadpool, the anonymous class's outer object reference (this)
     * reference will not be our SearchFragment, it will be this KeywordDetectorFeeder static class.
     * <p>
     * If this result adding routine ever takes too long there won't be any references to the Fragment
     * thus we avoid any possibility of a Context leak while rotating the screen or going home and coming back.
     * <p>
     * The most this loop can take is about 1 second (maybe 1.5s on slow cpus) when the first big batch of results arrives,
     * otherwise it processes about 20-50 results at the time in up to 80ms. There's a chance the user will rotate
     * the screen by mistake when a search is submitted, otherwise I would've put this code directly on the main
     * thread, but some frames might be skipped, not a good experience whe you hit 'Search'
     */
    private static void updateKeywordDetectorWithSearchResults(SearchFragment fragment, final List<? extends SearchResult> results) {
        final WeakReference<SearchFragment> fragmentRef = Ref.weak(fragment);
        final ArrayList<SearchResult> resultsCopy = new ArrayList<>(results);
        Engine.instance().getThreadPool().execute(() -> {
            if (!Ref.alive(fragmentRef)) {
                return;
            }
            SearchFragment fragment1 = fragmentRef.get();
            if (fragment1 == null) {
                return; // everything is possible
            }
            fragment1.keywordDetector.feedSearchResults(resultsCopy);
            fragment1.keywordDetector.requestHistogramsUpdateAsync(null);
        });
    }

    private static final class ScrollDirectionListener implements DirectionDetectorScrollListener.ScrollDirectionListener {
        private final WeakReference<SearchFragment> searchFragmentWeakReference;

        ScrollDirectionListener(SearchFragment searchFragment) {
            searchFragmentWeakReference = Ref.weak(searchFragment);
        }

        @Override
        public void onScrollUp () {
            if (Ref.alive(searchFragmentWeakReference)) {
                searchFragmentWeakReference.get().onSearchScrollUp();
            }
        }
        @Override
        public void onScrollDown () {
            if (Ref.alive(searchFragmentWeakReference)) {
                searchFragmentWeakReference.get().onSearchScrollDown();
            }
        }
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

    private void updateFileTypeCounter(FilteredSearchResults filteredSearchResults) {
        if (filteredSearchResults != null) {
            fileTypeCounter.clear();
            fileTypeCounter.add(filteredSearchResults);
        }
        refreshFileTypeCounters(adapter != null && adapter.getList() != null && adapter.getList().size() > 0);
    }

    private void refreshFileTypeCounters(boolean fileTypeCountersVisible) {
        searchInput.setFileTypeCountersVisible(fileTypeCountersVisible);
        boolean keywordFiltersApplied = adapter.getKeywordFiltersPipeline().size() > 0;
        FilteredSearchResults fsr = fileTypeCounter.fsr;
        int applications = keywordFiltersApplied ? fsr.numFilteredApplications : fsr.numApplications;
        int audios = keywordFiltersApplied ? fsr.numFilteredAudio : fsr.numAudio;
        int documents = keywordFiltersApplied ? fsr.numFilteredDocuments : fsr.numDocuments;
        int pictures = keywordFiltersApplied ? fsr.numFilteredPictures : fsr.numPictures;
        int torrents = keywordFiltersApplied ? fsr.numFilteredTorrents : fsr.numTorrents;
        int videos = keywordFiltersApplied ? fsr.numFilteredVideo : fsr.numVideo;
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_APPLICATIONS, applications);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_AUDIO, audios);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_DOCUMENTS, documents);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_PICTURES, pictures);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_TORRENTS, torrents);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_VIDEOS, videos);
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
        currentQuery = query;
        keywordDetector.shutdownHistogramUpdateRequestDispatcher();
        LocalSearchEngine.instance().performSearch(query);
        searchProgress.setProgressEnabled(true);
        showSearchView(getView());
    }

    private void cancelSearch() {
        adapter.clear();
        searchInput.setFileTypeCountersVisible(false);
        fileTypeCounter.clear();
        refreshFileTypeCounters(false);
        resetKeywordDetector();
        currentQuery = null;
        LocalSearchEngine.instance().cancelSearch();
        searchProgress.setProgressEnabled(false);
        showSearchView(getView());
        filterButton.reset(true); // hide=true
        showRatingsReminder(getView());
        searchHeaderBanner.setBannerViewVisibility(SearchHeaderBanner.BannerType.ALL, false);
        keywordDetector.shutdownHistogramUpdateRequestDispatcher();
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
        if (getCurrentQuery() != null && adapter != null) {
            searchProgress.setKeywordFiltersApplied(!adapter.getKeywordFiltersPipeline().isEmpty());
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
                        Offers.showInterstitial(getActivity(), Offers.PLACEMENT_INTERSTITIAL_MAIN, false, false);
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
        Engine.instance().hapticFeedback();
        if (!(sr instanceof AbstractTorrentSearchResult || sr instanceof TorrentPromotionSearchResult) &&
                ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG)) {
            if (sr instanceof FileSearchResult) {
                try {
                    NewTransferDialog dlg = NewTransferDialog.newInstance((FileSearchResult) sr, false);
                    dlg.show(getFragmentManager());
                } catch (IllegalStateException e) {
                    // android.app.FragmentManagerImpl.checkStateLoss:1323 -> java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
                    // just start the download then if the dialog crapped out.
                    onDialogClick(NewTransferDialog.TAG, Dialog.BUTTON_POSITIVE);
                }
            }
        } else {
            if (isVisible()) {
                startDownload(getActivity(), sr, toastMessage);
            }
        }
    }

    public static void startDownload(Context ctx, SearchResult sr, String message) {
        if (sr instanceof AbstractTorrentSearchResult) {
            UIUtils.showShortMessage(ctx, R.string.fetching_torrent_ellipsis);
        }
        new AsyncStartDownload(ctx, sr, message);
    }

    private void showRatingsReminder(View v) {
        final RichNotification ratingReminder = findView(v, R.id.fragment_search_rating_reminder_notification);
        ratingReminder.setVisibility(View.GONE);
        final ConfigurationManager CM = ConfigurationManager.instance();
        boolean alreadyRated = CM.getBoolean(Constants.PREF_KEY_GUI_ALREADY_RATED_US_IN_MARKET);
        if (alreadyRated || ratingReminder.wasDismissed()) {
            //LOG.info("SearchFragment.showRatingsReminder() aborted. alreadyRated="+alreadyRated + " wasDismissed=" + ratingReminder.wasDismissed());
            return;
        }
        long installationTimestamp = CM.getLong(Constants.PREF_KEY_GUI_INSTALLATION_TIMESTAMP);
        long daysInstalled = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installationTimestamp);
        if (installationTimestamp == -1 || daysInstalled < 5) {
            //LOG.info("SearchFragment.showRatingsReminder() aborted. Too soon to show ratings reminder. daysInstalled=" + daysInstalled);
            return;
        }
        ClickAdapter<SearchFragment> onRateAdapter = new OnRateClickAdapter(SearchFragment.this, ratingReminder, CM);
        ratingReminder.setOnClickListener(onRateAdapter);
        RichNotificationActionLink rateFrostWireActionLink =
                new RichNotificationActionLink(ratingReminder.getContext(),
                        getString(R.string.love_frostwire),
                        onRateAdapter);
        RichNotificationActionLink sendFeedbackActionLink =
                new RichNotificationActionLink(ratingReminder.getContext(),
                        getString(R.string.send_feedback),
                        new OnFeedbackClickAdapter(this, ratingReminder, CM));
        ratingReminder.updateActionLinks(rateFrostWireActionLink, sendFeedbackActionLink);
        ratingReminder.setVisibility(View.VISIBLE);
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

    @Override
    public String getCurrentQuery() {
        return currentQuery;
    }

    private void switchToThe(boolean right) {
        searchInput.switchToThe(right);
    }

    public void connectDrawerLayoutFilterView(DrawerLayout drawerLayout, View filterView) {
        this.drawerLayout = drawerLayout;
        drawerLayout.removeDrawerListener(this);
        drawerLayout.addDrawerListener(this);
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
        drawerLayout.openDrawer(keywordFilterDrawerView);
        keywordDetector.requestHistogramsUpdateAsync(null);
    }

    private void resetKeywordDetector() {
        keywordDetector.reset();
        keywordFilterDrawerView.reset();
    }

    public void setDataUp(boolean value) {
        searchProgress.setDataUp(value);
    }

    private static class LocalSearchEngineListener implements SearchListener {

        private final WeakReference<SearchFragment> searchFragmentRef;

        LocalSearchEngineListener(SearchFragment searchFragment) {
            searchFragmentRef = Ref.weak(searchFragment);
        }

        @Override
        public void onResults(long token, final List<? extends SearchResult> results) {
            if (Ref.alive(searchFragmentRef)) {
                //noinspection unchecked
                searchFragmentRef.get().onSearchResults((List<SearchResult>) results);
            }
        }

        @Override
        public void onError(long token, SearchError error) {
            LOG.error("Some error in search stream: " + error);
        }

        @Override
        public void onStopped(long token) {
            if (Ref.alive(searchFragmentRef)) {
                SearchFragment searchFragment = searchFragmentRef.get();
                if (searchFragment.isAdded()) {
                    searchFragment.getActivity().runOnUiThread(() -> {
                        if (Ref.alive(searchFragmentRef)) {
                            SearchFragment searchFragment1 = searchFragmentRef.get();
                            searchFragment1.searchProgress.setProgressEnabled(false);
                            searchFragment1.deepSearchProgress.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }
    }

    private static final class SearchInputOnSearchListener implements SearchInputView.OnSearchListener {
        private final WeakReference<LinearLayout> rootViewRef;
        private final WeakReference<SearchFragment> fragmentRef;

        SearchInputOnSearchListener(LinearLayout rootView, SearchFragment fragment) {
            this.rootViewRef = Ref.weak(rootView);
            this.fragmentRef = Ref.weak(fragment);
        }

        public void onSearch(View v, String query, int mediaTypeId) {
            if (!Ref.alive(fragmentRef) || !Ref.alive(rootViewRef)) {
                return;
            }
            SearchFragment fragment = fragmentRef.get();
            fragment.searchHeaderBanner.setSearchFragmentReference(fragment);
            fragment.searchHeaderBanner.updateComponents();
            fragment.resetKeywordDetector();
            fragment.searchInput.selectTabByMediaType((byte) mediaTypeId);
            if (query.contains("://m.soundcloud.com/") || query.contains("://soundcloud.com/")) {
                fragment.cancelSearch();
                new AsyncDownloadSoundcloudFromUrl(fragment.getActivity(), query);
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
            if (!Ref.alive(fragmentRef)) {
                return;
            }
            SearchFragment fragment = fragmentRef.get();
            if (fragment.adapter.getFileType() != mediaTypeId) {
                ConfigurationManager.instance().setLastMediaTypeFilter(mediaTypeId);
                fragment.adapter.setFileType(mediaTypeId);
            }
            fragment.showSearchView(rootViewRef.get());
        }

        public void onClear(View v) {
            if (!Ref.alive(fragmentRef)) {
                return;
            }
            SearchFragment fragment = fragmentRef.get();
            fragment.cancelSearch();
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
            this.fsr.numFilteredAudio += fsr.numFilteredAudio;
            this.fsr.numFilteredApplications += fsr.numFilteredApplications;
            this.fsr.numFilteredDocuments += fsr.numFilteredDocuments;
            this.fsr.numFilteredPictures += fsr.numFilteredPictures;
            this.fsr.numFilteredTorrents += fsr.numFilteredTorrents;
            this.fsr.numFilteredVideo += fsr.numFilteredVideo;
        }

        public void clear() {
            this.fsr.numAudio = 0;
            this.fsr.numApplications = 0;
            this.fsr.numDocuments = 0;
            this.fsr.numPictures = 0;
            this.fsr.numTorrents = 0;
            this.fsr.numVideo = 0;
            this.fsr.numFilteredAudio = 0;
            this.fsr.numFilteredApplications = 0;
            this.fsr.numFilteredDocuments = 0;
            this.fsr.numFilteredPictures = 0;
            this.fsr.numFilteredTorrents = 0;
            this.fsr.numFilteredVideo = 0;
        }
    }

    private final static class OnRateClickAdapter extends ClickAdapter<SearchFragment> {
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

        private final ImageButton imageButton;
        private final TextView counterTextView;
        private Animation pulse;
        private boolean filterButtonClickedBefore;
        private long lastUIUpdate = 0;

        FilterToolbarButton(ImageButton imageButton, TextView counterTextView) {
            this.imageButton = imageButton;
            this.counterTextView = counterTextView;
            this.filterButtonClickedBefore = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_SEARCH_FILTER_DRAWER_BUTTON_CLICKED);
            if (!filterButtonClickedBefore) {
                this.pulse = AnimationUtils.loadAnimation(getActivity(), R.anim.pulse);
            }
            initListeners();
        }

        // self determine if it should be hidden or not
        public void updateVisibility() {
            setVisible(currentQuery != null && adapter != null && adapter.getTotalCount() > 0);
        }

        @Override
        public void notifyHistogramsUpdate(final Map<KeywordDetector.Feature, List<Map.Entry<String, Integer>>> filteredHistograms) {
            // TODO: review this, this is a workaround to a not clear framework logic problem
            long td = System.currentTimeMillis() - filterButton.lastUIUpdate;
            if (td <= 300) {
                // don't bother to enqueue the task
                return;
            }

            async(filterButton,
                    SearchFragment::possiblyWaitInBackgroundToUpdateUI,
                    keywordFilterDrawerView, filteredHistograms,
                    SearchFragment::updateUIWithFilteredHistogramsPerFeature);
        }

        @Override
        public void onKeywordDetectorFinished() {
            if (isAdded()) {
                getActivity().runOnUiThread(() -> {
                    keywordFilterDrawerView.hideIndeterminateProgressViews();
                    keywordFilterDrawerView.requestLayout();
                });
            }
        }

        public void reset(boolean hide) { //might do, parameter to not hide drawer
            setVisible(!hide);
            keywordDetector.reset();
            closeKeywordFilterDrawer();
        }

        @Override
        public void onPipelineUpdate(List<KeywordFilter> pipeline) {
            // this will make the adapter filter
            FilteredSearchResults filteredSearchResults = adapter.setKeywordFiltersPipeline(pipeline);
            updateFileTypeCounter(filteredSearchResults);
            if (pipeline != null) {
                if (pipeline.isEmpty()) {
                    counterTextView.setText("");
                } else {
                    counterTextView.setText(String.valueOf(pipeline.size()));
                }
            }
            updateVisibility();
            keywordFilterDrawerView.showIndeterminateProgressViews();
            List<SearchResult> results = adapter.getKeywordFiltersPipeline().isEmpty() ? adapter.getList() : filteredSearchResults.keywordFiltered;
            keywordDetector.reset();
            keywordDetector.requestHistogramsUpdateAsync(results);
        }

        @Override
        public void onAddKeywordFilter(KeywordFilter keywordFilter) {
            keywordDetector.clearHistogramUpdateRequestDispatcher();
            FilteredSearchResults filteredSearchResults = adapter.addKeywordFilter(keywordFilter);
            updateFileTypeCounter(filteredSearchResults);
        }

        @Override
        public void onRemoveKeywordFilter(KeywordFilter keywordFilter) {
            keywordDetector.clearHistogramUpdateRequestDispatcher();
            updateFileTypeCounter(adapter.removeKeywordFilter(keywordFilter));
        }

        @Override
        public List<KeywordFilter> getKeywordFiltersPipeline() {
            if (adapter == null) {
                return new ArrayList<>(0);
            }
            return adapter.getKeywordFiltersPipeline();
        }

        private void setVisible(boolean visible) {
            int visibility = visible ? View.VISIBLE : View.GONE;
            int oldVisibility = imageButton.getVisibility();
            imageButton.setVisibility(visibility);
            if (visible) {
                if (oldVisibility == View.GONE && !filterButtonClickedBefore) {
                    pulse.reset();
                    imageButton.setAnimation(pulse);
                    pulse.setStartTime(AnimationUtils.currentAnimationTimeMillis() + 1000);
                }
                counterTextView.setVisibility(getKeywordFiltersPipeline().size() > 0 ? View.VISIBLE : View.GONE);
                counterTextView.setText(String.valueOf(getKeywordFiltersPipeline().size()));
            } else {
                imageButton.clearAnimation();
                counterTextView.setVisibility(View.GONE);
            }
        }

        private void initListeners() {
            imageButton.setOnClickListener(v -> {
                if (!filterButtonClickedBefore) {
                    filterButtonClickedBefore = true;
                    ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_SEARCH_FILTER_DRAWER_BUTTON_CLICKED, true);
                    imageButton.clearAnimation();
                    pulse = null;
                }
                openKeywordFilterDrawerView();
            });
        }

        private void openKeywordFilterDrawerView() {
            keywordFilterDrawerView.setKeywordFiltersPipelineListener(this);
            openKeywordFilterDrawer();
        }
    }

    @SuppressWarnings("unused")
    private static void possiblyWaitInBackgroundToUpdateUI(FilterToolbarButton filterToolbarButton,
                                                           KeywordFilterDrawerView keywordFilterDrawerView,
                                                           Map<KeywordDetector.Feature, List<Map.Entry<String, Integer>>> filteredHistograms) {
        long timeSinceLastUpdate = System.currentTimeMillis() - filterToolbarButton.lastUIUpdate;
        if (timeSinceLastUpdate < 500) {
            try {
                Thread.sleep(500L - timeSinceLastUpdate);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static void updateUIWithFilteredHistogramsPerFeature(FilterToolbarButton filterToolbarButton,
                                                                 KeywordFilterDrawerView keywordFilterDrawerView,
                                                                 Map<KeywordDetector.Feature, List<Map.Entry<String, Integer>>> filteredHistograms) {
        filterToolbarButton.lastUIUpdate = System.currentTimeMillis();
        // should be safe from concurrent modification exception as new list with filtered elements
        for (KeywordDetector.Feature feature : filteredHistograms.keySet()) {
            List<Map.Entry<String, Integer>> filteredHistogram = filteredHistograms.get(feature);
            keywordFilterDrawerView.updateData(
                    feature,
                    filteredHistogram);
        }
        filterToolbarButton.updateVisibility();
        keywordFilterDrawerView.requestLayout();
    }
}
