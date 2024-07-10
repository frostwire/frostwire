/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter
 * Copyright (c) 2011-2024, FrostWire(R). All rights reserved.
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


package com.frostwire.android.gui.fragments;

import static com.frostwire.android.util.Asyncs.async;
import static com.frostwire.android.util.SystemUtils.HandlerThreadName.SEARCH_PERFORMER;
import static com.frostwire.android.util.SystemUtils.ensureUIThreadOrCrash;
import static com.frostwire.android.util.SystemUtils.postToHandler;
import static com.frostwire.android.util.SystemUtils.postToUIThread;

import android.annotation.SuppressLint;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.TellurideCourier;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.gui.SearchMediator;
import com.frostwire.android.gui.adapters.OnFeedbackClickAdapter;
import com.frostwire.android.gui.adapters.PromotionDownloader;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
import com.frostwire.android.gui.adapters.SearchResultListAdapter.FilteredSearchResults;
import com.frostwire.android.gui.dialogs.AbstractConfirmListDialog;
import com.frostwire.android.gui.dialogs.ConfirmListDialogDefaultAdapter;
import com.frostwire.android.gui.dialogs.HandpickedTorrentDownloadDialogOnFetch;
import com.frostwire.android.gui.dialogs.NewTransferDialog;
import com.frostwire.android.gui.dialogs.TellurideSearchResultDownloadDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.tasks.AsyncDownloadSoundcloudFromUrl;
import com.frostwire.android.gui.tasks.AsyncStartDownload;
import com.frostwire.android.gui.transfers.HttpSlideSearchResult;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.DirectionDetectorScrollListener;
import com.frostwire.android.gui.util.ScrollListeners.ComposedOnScrollListener;
import com.frostwire.android.gui.util.ScrollListeners.FastScrollDisabledWhenIdleOnScrollListener;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.ClickAdapter;
import com.frostwire.android.gui.views.PromotionsView;
import com.frostwire.android.gui.views.RichNotification;
import com.frostwire.android.gui.views.RichNotificationActionLink;
import com.frostwire.android.gui.views.SearchInputView;
import com.frostwire.android.gui.views.SearchProgressView;
import com.frostwire.android.gui.views.SwipeLayout;
import com.frostwire.android.offers.HeaderBanner;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.frostclick.Slide;
import com.frostwire.frostclick.SlideList;
import com.frostwire.frostclick.TorrentPromotionSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.SearchResult;
import com.frostwire.search.telluride.TellurideSearchResult;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.search.yt.YTSearchResult;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.JsonUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.http.HttpClient;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SearchFragment extends AbstractFragment implements MainFragment, OnDialogClickListener, SearchProgressView.CurrentQueryReporter, PromotionDownloader {
    private static final Logger LOG = Logger.getLogger(SearchFragment.class);
    @SuppressLint("StaticFieldLeak")
    private static SearchFragment lastInstance = null;
    private SearchResultListAdapter adapter;
    private List<Slide> slides;
    private SearchInputView searchInput;
    private ProgressBar deepSearchProgress;
    private PromotionsView promotions;
    private SearchProgressView searchProgress;
    private ListView list;
    private String currentQuery;
    private final FileTypeCounter fileTypeCounter;
    private OnClickListener headerClickListener;
    private HeaderBanner headerBanner;
    private final AtomicBoolean cancelling = new AtomicBoolean(false);

    public SearchFragment() {
        super(R.layout.fragment_search);
        fileTypeCounter = new FileTypeCounter();
        currentQuery = null;
        lastInstance = this;
    }

    public static SearchFragment instance() {
        return lastInstance;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupAdapter();
        setupPromoSlides();
        setRetainInstance(true);
        lastInstance = this;
    }

    private void setupPromoSlides() {
        if (slides != null) {
            promotions.setSlides(slides);
        } else {
            async(this, SearchFragment::loadSlidesInBackground, SearchFragment::onSlidesLoaded);
        }
    }

    private List<Slide> loadSlidesInBackground() {
        try {
            HttpClient http = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH);
            String url = String.format("%s&from=android&fw=%s&sdk=%s", Constants.SERVER_PROMOTIONS_URL, Constants.FROSTWIRE_VERSION_STRING, Build.VERSION.SDK_INT);
            LOG.info("SearchFragment::loadSlidesInBackground() @ " + url);
            String json = http.get(url);
            SlideList slides = JsonUtils.toObject(json, SlideList.class);
            // yes, these requests are done only once per session.
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
        @SuppressLint("InflateParams") LinearLayout header = (LinearLayout) inflater.inflate(R.layout.view_search_header, null, false);
        TextView title = header.findViewById(R.id.view_search_header_text_title);
        title.setText(R.string.search);
        title.setOnClickListener(getHeaderClickListener());
        return header;
    }

    @Override
    public void onResume() {
        super.onResume();
        lastInstance = this;
        // getHeader was conceived for quick update of main fragments headers,
        // mainly in a functional style, but it is ill suited to extract from
        // it a mutable state, like filterButton.
        // As a result, you will get multiple NPE during the normal lifestyle
        // of the fragmentRef, since getHeader is not guaranteed to be called
        // at the right time during a full resume of the fragmentRef.
        // TODO: refactor this
        if (isAdded() && getActivity() != null) { // best effort
            // this will happen due to the call to onTabReselected on full resume
            // and this is only solving the NPE, the drawback is that it will
            // create a few orphan view objects to be GC'ed soon.
            // it is a poor solution overall, but the right one requires
            // a big refactor.
            getHeader(getActivity());
        }
        ConfigurationManager CM = ConfigurationManager.instance();
        setupAdapter();
        if (adapter != null && (adapter.getCount() > 0 || adapter.getTotalCount() > 0)) {
            refreshFileTypeCounters(true, fileTypeCounter.fsr);
            searchInput.selectTabByMediaType((byte) CM.getLastMediaTypeFilter());
        } else {
            setupPromoSlides();
        }

        if (list != null) {
            list.setOnScrollListener(new FastScrollDisabledWhenIdleOnScrollListener());
        }

        if (list != null && CM.getBoolean(Constants.PREF_KEY_GUI_DISTRACTION_FREE_SEARCH)) {
            list.setOnScrollListener(new ComposedOnScrollListener(new FastScrollDisabledWhenIdleOnScrollListener(), new DirectionDetectorScrollListener(new ScrollDirectionListener(this), Engine.instance().getThreadPool())));
        }
        if (headerBanner != null) {
            if (getCurrentQuery() == null || Offers.disabledAds()) {
                headerBanner.setBannerViewVisibility(HeaderBanner.VisibleBannerType.ALL, false);
            }
        }
        if (getCurrentQuery() == null) {
            searchInput.setFileTypeCountersVisible(false);
        }
    }

    @Override
    public void onDestroy() {
        SearchMediator.instance().setSearchListener(null);
        destroyHeaderBanner();
        destroyPromotionsBanner();
        super.onDestroy();
        lastInstance = null;
    }

    public void destroyHeaderBanner() {
        if (headerBanner != null) {
            headerBanner.onDestroy();
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
        headerBanner = findView(view, R.id.fragment_header_banner);
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
        searchProgress.setCancelOnClickListener(new OnCancelSearchListener(Ref.weak(this)));
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
        switchView(view, R.id.fragment_search_promos);
    }

    public static class NotAvailableDialog extends AbstractDialog {
        public NotAvailableDialog() {
            super(R.layout.dialog_default);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            TextView defaultDialogTitle = findView(dlg, R.id.dialog_default_title);
            defaultDialogTitle.setText(R.string.information);

            TextView defaultDialogText = findView(dlg, R.id.dialog_default_text);
            defaultDialogText.setText(R.string.yt_not_supported_on_basic);

            Button button = findView(dlg, R.id.dialog_default_button_no);
            button.setVisibility(View.GONE);

            Button okButton = findView(dlg, R.id.dialog_default_button_yes);
            okButton.setText(R.string.ok);
            okButton.setOnClickListener((l) -> dismiss());
        }
    }

    public void performTellurideSearch(String pageUrl) {
        boolean itsYTURL = pageUrl.contains("youtube.com/") || pageUrl.contains("youtu.be/");
        if (itsYTURL && Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !BuildConfig.DEBUG) {
            cancelSearch();
            searchInput.setText("");
            NotAvailableDialog dialog = new NotAvailableDialog();
            dialog.show(getFragmentManager());
            return;
        }
        searchInput.selectTabByMediaType(Constants.FILE_TYPE_VIDEOS);
        setupAdapter();

        // manually prepare UI for telluride results
        View view = getView();
        switchView(view, R.id.fragment_search_search_progress);
        adapter.clear();
        fileTypeCounter.clear();
        adapter.setFileType(Constants.FILE_TYPE_VIDEOS, false, null);
        fileTypeCounter.clear();
        refreshFileTypeCounters(false, fileTypeCounter.fsr);
        deepSearchProgress.setVisibility(View.VISIBLE);

        postToHandler(SEARCH_PERFORMER, () -> SearchMediator.instance().performTellurideSearch(pageUrl, adapter));
        searchInput.setText(" "); // an empty space so the 'x' button is shown.
        switchView(view, R.id.fragment_search_list);
    }


    /**
     * Call only after search starts
     */
    private void prepareUIForSearch(int fileType) {
        SystemUtils.ensureUIThreadOrCrash("SearchFragment::prepareForSearch");
        currentQuery = searchInput.getText();
        adapter.clear();
        fileTypeCounter.clear();
        adapter.setFileType(fileType, false, () -> {
            refreshFileTypeCounters(false, fileTypeCounter.fsr);
            showSearchView(getView());
        });
    }

    private void startMagnetDownload(String magnet) {
        UIUtils.showLongMessage(getActivity(), R.string.torrent_url_added);
        TransferManager.instance().downloadTorrent(magnet, new HandpickedTorrentDownloadDialogOnFetch(getActivity(), false));
    }

    private void setupAdapter() {
        SearchMediator.instance().setSearchListener(new SearchFragmentSearchEngineListener(this));
        if (adapter == null) {
            adapter = new SearchResultListAdapter(getActivity()) {
                @Override
                protected void searchResultClicked(SearchResult sr) {
                    // Telluride Preliminary Search Results
                    if (sr instanceof YTSearchResult) {
                        startTellurideDownloadDialog((YTSearchResult) sr, getString(R.string.analyzing_downloadable_files));
                    } else {
                        startTransfer(sr, getString(R.string.download_added_to_queue));
                    }
                }
            };
        }
        list.setAdapter(adapter);
    }

    private void updateFilteredSearchResults(List<FileSearchResult> allFilteredSearchResults, boolean clearBeforeAdding) {
        SystemUtils.ensureUIThreadOrCrash("SearchFragment::updateFilteredSearchResults(List<FileSearchResult> allFilteredSearchResults)");
        if (isAdded()) {
            try {
                if (adapter != null) {
                    adapter.updateVisualListWithAllMediaTypeFilteredSearchResults(allFilteredSearchResults, clearBeforeAdding);
                }
                refreshFileTypeCounters(true, fileTypeCounter.fsr);
                showSearchView(getView());
            } catch (Throwable t) {
                if (BuildConfig.DEBUG) {
                    throw t;
                }
                LOG.error("onSearchResults() " + t.getMessage(), t);
            }
        }
    }

    private static final class ScrollDirectionListener implements DirectionDetectorScrollListener.ScrollDirectionListener {
        private final WeakReference<SearchFragment> searchFragmentWeakReference;

        ScrollDirectionListener(SearchFragment searchFragment) {
            searchFragmentWeakReference = Ref.weak(searchFragment);
        }

        @Override
        public void onScrollUp() {
            if (Ref.alive(searchFragmentWeakReference)) {
                searchFragmentWeakReference.get().onSearchScrollUp();
            }
        }

        @Override
        public void onScrollDown() {
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

    public void refreshFileTypeCounters(boolean fileTypeCountersVisible, FilteredSearchResults fsr) {
        searchInput.setFileTypeCountersVisible(fileTypeCountersVisible);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_APPLICATIONS, fsr.numApplications);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_AUDIO, fsr.numAudio);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_DOCUMENTS, fsr.numDocuments);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_PICTURES, fsr.numPictures);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_TORRENTS, fsr.numTorrents);
        searchInput.updateFileTypeCounter(Constants.FILE_TYPE_VIDEOS, fsr.numVideo);
    }

    private void cancelSearch() {
        if (cancelling.get()) {
            // avoid loops
            return;
        }
        cancelling.set(true);
        SystemUtils.ensureUIThreadOrCrash("SearchFragment::cancelSearch");
        adapter.clear();
        fileTypeCounter.clear();
        postToHandler(SEARCH_PERFORMER, () -> SearchMediator.instance().cancelSearch());
        postToHandler(SEARCH_PERFORMER, TellurideCourier::abortCurrentQuery);
        searchInput.setFileTypeCountersVisible(false);
        currentQuery = null;
        searchProgress.setProgressEnabled(false);
        showRatingsReminder(getView());
        headerBanner.setBannerViewVisibility(HeaderBanner.VisibleBannerType.ALL, false);
        refreshFileTypeCounters(false, fileTypeCounter.fsr);
        showSearchView(getView());
        UIUtils.forceShowKeyboard(getContext());
        cancelling.set(false);
    }

    private void showSearchView(View view) {
        ensureUIThreadOrCrash("SearchFragment::showSearchView");
        if (SearchMediator.instance() == null) {
            switchView(view, R.id.fragment_search_promos);
            LOG.info("SearchFragment::showSearchView no search instance available, going back to promos.");
            return;
        }

        boolean searchFinished = SearchMediator.instance().isSearchFinished();
        boolean searchStopped = SearchMediator.instance().isSearchStopped();
        boolean searchCancelled = cancelling.get() || (searchStopped && adapter.getTotalCount() == 0);
        boolean adapterHasResults = adapter != null && adapter.getTotalCount() > 0;

        if (searchCancelled) {
            switchView(view, R.id.fragment_search_promos);
            return;
        }

        if (adapterHasResults) {
            switchView(view, R.id.fragment_search_list);
        } else {
            switchView(view, R.id.fragment_search_search_progress);
        }
        searchProgress.setEnabled(!searchFinished);
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
                SearchMediator.instance().markOpened(NewTransferDialog.srRef.get(), adapter);
            }
        }
    }

    private void startTellurideDownloadDialog(final YTSearchResult sr, final String toastMessage) {
        SystemUtils.postToUIThread(() -> UIUtils.showShortMessage(getActivity(), toastMessage));

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, () -> {
            TellurideSearchResultDownloadDialog.TellurideSearchResultDownloadDialogAdapter tellurideSearchResultDownloadDialogAdapter = new TellurideSearchResultDownloadDialog.TellurideSearchResultDownloadDialogAdapter(getContext(), new ArrayList<>(), AbstractConfirmListDialog.SelectionMode.SINGLE_SELECTION);
            TellurideCourier.SearchPerformer<ConfirmListDialogDefaultAdapter<TellurideSearchResult>> searchPerformer =
                    new TellurideCourier.SearchPerformer<>(1,
                            sr.getDetailsUrl(),
                            tellurideSearchResultDownloadDialogAdapter);
            searchPerformer.perform();
            SystemUtils.postToUIThread(() -> {
                TellurideSearchResultDownloadDialog dlg = TellurideSearchResultDownloadDialog.newInstance(getContext(), tellurideSearchResultDownloadDialogAdapter.getFullList());
                dlg.show(getActivity().getFragmentManager());
                SearchManager.getInstance().perform(SearchEngine.FROSTCLICK.getPerformer(1, "https://plus.youtube.com"));
            });
        });
    }

    private void startTransfer(final SearchResult sr, final String toastMessage) {
        if (!(sr instanceof AbstractTorrentSearchResult || sr instanceof TorrentPromotionSearchResult) && ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_SHOW_NEW_TRANSFER_DIALOG)) {
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
        RichNotificationActionLink rateFrostWireActionLink = new RichNotificationActionLink(ratingReminder.getContext(), getString(R.string.love_frostwire), onRateAdapter);
        RichNotificationActionLink sendFeedbackActionLink = new RichNotificationActionLink(ratingReminder.getContext(), getString(R.string.send_feedback), new OnFeedbackClickAdapter(this, ratingReminder, CM));
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

    public void setDataUp(boolean value) {
        SystemUtils.ensureUIThreadOrCrash("SearchFragment::setDataUp");
        searchProgress.setDataUp(value);
    }

    private static class SearchFragmentSearchEngineListener implements SearchListener {
        SearchFragment searchFragment;

        SearchFragmentSearchEngineListener(SearchFragment searchFragment) {
            this.searchFragment = searchFragment;
        }

        @Override
        public void onResults(long token, final List<? extends SearchResult> newResults) {
            if (searchFragment.adapter == null) {
                LOG.info("SearchFragment::onResults aborted. Check your logic searchFragment.adapter is null");
                return;
            }
            final int fileType = searchFragment.searchInput.getSelectedFileType();
            // Add new newResults to the adapter
            searchFragment.adapter.addResults(newResults);
            // Sets the file type in the adapter and filters search results by file type
            boolean differentFileType = searchFragment.adapter.getFileType() != fileType;
            searchFragment.adapter.setFileType(fileType, () -> {
                // Ask the adapter for the filtered search results
                FilteredSearchResults fsr = searchFragment.adapter.getFilteredSearchResults();
                // Time to report to the UI, let the adapter know about the new newResults
                if (differentFileType) {
                    searchFragment.fileTypeCounter.updateFilteredSearchResults(fsr);
                } else {
                    searchFragment.fileTypeCounter.add(fsr);
                }
                SystemUtils.postToUIThread(() -> searchFragment.updateFilteredSearchResults(fsr.mediaTypeFiltered, differentFileType));
            }); //background call
        }

        @Override
        public void onError(long token, SearchError error) {
            LOG.error("Some error in search stream: " + error);
        }

        @Override
        public void onStopped(long token) {
            if (searchFragment.isAdded()) {
                searchFragment.getActivity().runOnUiThread(() -> {
                    try {
                        searchFragment.searchProgress.setProgressEnabled(false);
                        searchFragment.deepSearchProgress.setVisibility(View.GONE);

                        if (searchFragment.adapter.getTotalCount() == 0) {
                            Context context = searchFragment.getContext();
                            if (context != null) {
                                UIUtils.showLongMessage(context, R.string.no_results_feedback);
                                //searchFragment.switchView(searchFragment.getView(), R.id.fragment_search_promos);
                                searchFragment.cancelSearch();
                                searchFragment.searchInput.setText("");
                            }
                        }
                    } catch (Throwable t) {
                        if (BuildConfig.DEBUG) {
                            throw t;
                        }
                        LOG.error("onStopped() " + t.getMessage(), t);
                    }
                });
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
            fragment.headerBanner.updateComponents();
            fragment.searchInput.selectTabByMediaType((byte) mediaTypeId);
            if (query.contains("://m.soundcloud.com/") || query.contains("://soundcloud.com/")) {
                fragment.cancelSearch();
                new AsyncDownloadSoundcloudFromUrl(fragment.getActivity(), query);
                fragment.searchInput.setText("");
            } else if (query.startsWith("magnet:?xt=urn:btih:") || (query.startsWith("http") && query.endsWith(".torrent"))) {
                fragment.startMagnetDownload(query);
                fragment.currentQuery = null;
                fragment.searchInput.setText("");
            } else if (query.startsWith("http") && !query.endsWith(".torrent")) {
                // URls that are no torrents, Telluride Search
                fragment.performTellurideSearch(query);
            } else {
                postToHandler(SEARCH_PERFORMER, () -> {
                    SearchMediator.instance().performSearch(query);
                    postToUIThread(() -> fragment.prepareUIForSearch(mediaTypeId));
                });
            }
        }

        public void onMediaTypeSelected(View view, int mediaTypeId) {
            if (!Ref.alive(fragmentRef)) {
                return;
            }
            SearchFragment fragment = fragmentRef.get();
            if (fragment.adapter.getFileType() != mediaTypeId) {
                postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> ConfigurationManager.instance().setLastMediaTypeFilter(mediaTypeId));
                fragment.adapter.setFileType(mediaTypeId, false, () -> fragment.showSearchView(rootViewRef.get()));
            } else {
                fragment.showSearchView(rootViewRef.get());
            }
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

        public void updateFilteredSearchResults(FilteredSearchResults newFsr) {
            clear();
            add(newFsr);
        }

        public void add(final FilteredSearchResults fsr) {
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
            this.fsr.clear();
        }
    }

    private static final class OnRateClickAdapter extends ClickAdapter<SearchFragment> {
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

    private static final class OnCancelSearchListener implements OnClickListener {

        private final WeakReference<SearchFragment> searchFragmentRef;

        OnCancelSearchListener(WeakReference<SearchFragment> sfr) {
            this.searchFragmentRef = sfr;
        }

        @Override
        public void onClick(View view) {
            if (!Ref.alive(searchFragmentRef)) {
                return;
            }
            SearchFragment searchFragment = searchFragmentRef.get();
            if (searchFragment == null) {
                return;
            }
            SearchInputView searchInput = searchFragment.searchInput;
            SearchResultListAdapter adapter = searchFragment.adapter;
            SearchProgressView searchProgress = searchFragment.searchProgress;
            // retry
            if (SearchMediator.instance().isSearchFinished()) {
                final String query = searchInput.getText();
                searchProgress.setProgressEnabled(true);
                postToHandler(SEARCH_PERFORMER, () -> {
                    SearchMediator.instance().performSearch(query);
                    postToUIThread(() -> searchFragment.prepareUIForSearch(adapter.getFileType()));
                });
            }
            // cancel
            else {
                searchFragment.cancelSearch();
            }
        }
    }
}
