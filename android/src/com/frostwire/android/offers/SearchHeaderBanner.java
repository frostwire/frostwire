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

package com.frostwire.android.offers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubView;

import java.lang.ref.WeakReference;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public final class SearchHeaderBanner extends LinearLayout {

    private static final Logger LOG = Logger.getLogger(SearchHeaderBanner.class);

    public enum BannerType {
        MOPUB,
        FALLBACK,
        ALL
    }

    private WeakReference<SearchFragment> searchFragmentWeakReference;
    private LinearLayout bannerHeaderLayout;
    private ImageButton dismissBannerButton;
    private MoPubView moPubView;
    private LinearLayout fallbackBannerView;
    private TextView fallbackBannerTextView;

    private HeaderBannerListener moPubBannerListener;

    public SearchHeaderBanner(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater != null) {
            try {
                inflater.inflate(R.layout.view_search_header_banner, this, true);
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
                t.printStackTrace();
            }
        }
    }

    public void setSearchFragmentReference(SearchFragment searchFragment) {
        searchFragmentWeakReference = Ref.weak(searchFragment);
    }

    public void onDestroy() {
        bannerHeaderLayout = null;
        fallbackBannerView = null;
        fallbackBannerTextView = null;
        getMoPubBannerListener().onDestroy(); // calls moPubView.onDestroy() and unregisters its IntentReceiver
    }

    private HeaderBannerListener getMoPubBannerListener() {
        if (moPubBannerListener == null) {
            moPubBannerListener = new HeaderBannerListener(this);
        }
        return moPubBannerListener;
    }

    private SearchFragment getSearchFragment() {
        if (!Ref.alive(searchFragmentWeakReference)) {
            return null;
        }
        return searchFragmentWeakReference.get();
    }

    private String getCurrentQuery() {
        SearchFragment searchFragment = getSearchFragment();
        if (searchFragment == null) {
            return null;
        }
        return searchFragment.getCurrentQuery();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        bannerHeaderLayout = findViewById(R.id.fragment_search_advertisement_header_layout);
        dismissBannerButton = findViewById(R.id.view_search_header_banner_dismiss_banner_button);
        moPubView = findViewById(R.id.view_search_header_banner_mopubview);
        fallbackBannerView = findViewById(R.id.view_search_header_banner_fallback_banner_linear_layout);
        fallbackBannerTextView = findViewById(R.id.view_search_header_banner_fallback_banner_textview);
        bannerHeaderLayout.setVisibility(View.GONE);
    }

    public void updateComponents() {
        if (Offers.disabledAds() || getSearchFragment() == null) {
            return;
        }
        boolean adsDisabled = Offers.disabledAds();
        Activity activity = (Activity) getContext();
        // check how long getting display metrics twice is, if expensive gotta refactor these methods
        boolean screenTallEnough = UIUtils.getScreenInches(activity) >= 4.33;
        boolean isPortrait = UIUtils.isPortrait(activity);
        boolean isTablet = UIUtils.isTablet(activity.getResources());
        boolean diceRollPassed = UIUtils.diceRollPassesThreshold(ConfigurationManager.instance(), Constants.PREF_KEY_GUI_MOPUB_SEARCH_HEADER_BANNER_THRESHOLD);
        boolean bannerVisible = !adsDisabled && screenTallEnough && (isPortrait || isTablet) && diceRollPassed && !getMoPubBannerListener().tooEarlyToDisplay();
        if (!bannerVisible) {
            LOG.info("updateComponents(): not eligible for search banner display. adsDisabled=" + adsDisabled +
                    ", screenTallEnough=" + screenTallEnough +
                    ", isPortrait=" + isPortrait +
                    ", isTablet=" + isTablet +
                    ", diceRollPassed=" + diceRollPassed +
                    ", tooEarlyToDisplay=" + getMoPubBannerListener().tooEarlyToDisplay());
            setBannerViewVisibility(BannerType.ALL, false);
            return;
        }
        setBannerViewVisibility(BannerType.MOPUB, false);
        loadFallbackBanner();
        dismissBannerButton.setOnClickListener(new DismissBannerButtonClickListener(this));

        if (!Offers.MOPUB.started()) {
            return;
        }
        moPubView.setTesting(false);
        moPubView.setAutorefreshEnabled(true);
        moPubView.setAdUnitId(MoPubAdNetwork.UNIT_ID_SEARCH_HEADER);
        String currentQuery = getCurrentQuery();
        if (currentQuery != null) {
            moPubView.setKeywords(currentQuery);
        }
        moPubView.setBannerAdListener(getMoPubBannerListener());
        try {
            moPubView.loadAd();
            LOG.info("updateComponents(): moPubView.loadAd()");
        } catch (Throwable e) {
            LOG.warn("updateComponents(): SearchFragment Mopub banner could not be loaded", e);
            loadFallbackBanner();
            moPubView.destroy();
        }
    }


    /**
     * You are responsible for hiding and showing every banner
     */
    public void setBannerViewVisibility(BannerType bannerType, boolean visible) {
        if (bannerHeaderLayout == null) {
            onFinishInflate();
        }
        // LOG.info("setBannerViewVisibility(" + bannerType + ",visible=" + visible + ")");
        if (Offers.disabledAds()) {
            LOG.info("setBannerViewVisibility() aborted. Offers disabled");
            bannerHeaderLayout.setVisibility(View.GONE);
            moPubView.setVisibility(View.GONE);
            fallbackBannerView.setVisibility(View.GONE);
            return;
        }
        // LOG.info("setBannerViewVisibility() -> bannerHeaderLayout@"+bannerHeaderLayout.hashCode());
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (bannerType == BannerType.ALL) {
            // LOG.info("setBannerViewVisibility() hide everything");
            moPubView.setVisibility(visibility);
            fallbackBannerView.setVisibility(visibility);
        } else if (bannerType == BannerType.MOPUB) {
            moPubView.setVisibility(visibility);
        } else if (bannerType == BannerType.FALLBACK) {
            fallbackBannerView.setVisibility(visibility);
        }
        bannerHeaderLayout.setVisibility(visibility);
        // LOG.info("setBannerViewVisibility() bannerHeaderLayout.visible==" + (bannerHeaderLayout.getVisibility() == View.VISIBLE));
    }

    private void onBannerDismiss(BannerType bannerType) {
        if (bannerHeaderLayout != null) {
            getMoPubBannerListener().onBannerDismissed(bannerType);
            if (bannerType == BannerType.MOPUB) {
                loadFallbackBanner();
            } else if (bannerType == BannerType.FALLBACK) {
                bannerHeaderLayout.setVisibility(View.GONE);
            }
        }
    }

    private void loadFallbackBanner() {
        if (Offers.disabledAds()) {
            LOG.info("loadFallbackBanner() aborted. Offers disabled");
            setBannerViewVisibility(BannerType.FALLBACK, false);
            return;
        }
        if (getMoPubBannerListener().tooEarlyToDisplay()) {
            LOG.info("loadFallbackBanner() aborted. too early to display");
            setBannerViewVisibility(BannerType.ALL, false);
            return;
        }
        final FallbackBannerOnClickListener fallbackBannerOnClickListener = new FallbackBannerOnClickListener(this);
        if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            // FROSTWIRE PLUS DONATION REQUEST
            fallbackBannerTextView.setText(R.string.support_frostwire);
            fallbackBannerView.setOnClickListener(fallbackBannerOnClickListener);
        } else if ((Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Constants.IS_BASIC_AND_DEBUG)) {
            // LOAD REMOVE BANNER OR SHOP ADS
            fallbackBannerTextView.setText(R.string.remove_ads);
            fallbackBannerView.setOnClickListener(fallbackBannerOnClickListener);
        }
        setBannerViewVisibility(BannerType.FALLBACK, true);
        //LOG.info("loadFallbackBanner() finished");
    }

    private static final class HeaderBannerListener implements MoPubView.BannerAdListener {
        private final WeakReference<SearchHeaderBanner> searchHeaderBannerRef;
        private long lastDismissed = 0L;
        private final int dismissIntervalInMs;

        HeaderBannerListener(SearchHeaderBanner searchFragment) {
            searchHeaderBannerRef = Ref.weak(searchFragment);
            dismissIntervalInMs = ConfigurationManager.instance().getInt(Constants.PREF_KEY_GUI_MOPUB_SEARCH_HEADER_BANNER_DISMISS_INTERVAL_IN_MS);
        }

        public boolean tooEarlyToDisplay() {
            return (System.currentTimeMillis() - lastDismissed) < dismissIntervalInMs;
        }

        public void onBannerDismissed(BannerType bannerType) {
            //LOG.info("onBannerDismissed(bannerType=" + bannerType + ")");
            if (bannerType == BannerType.FALLBACK) {
                // only changes when the banner container is fully dismissed
                lastDismissed = System.currentTimeMillis();
            }
            if (!Ref.alive(searchHeaderBannerRef)) {
                return;
            }
            searchHeaderBannerRef.get().setBannerViewVisibility(bannerType, false);
        }

        @Override
        public void onBannerLoaded(MoPubView banner) {
            if (tooEarlyToDisplay()) {
                LOG.info("onBannerLoaded() aborted, too early after dismissal");
                return;
            }
            if (!Ref.alive(searchHeaderBannerRef)) {
                LOG.info("onBannerLoaded() aborted, searchHeaderBanner reference lost");
                return;
            }
            SearchHeaderBanner searchHeaderBanner = searchHeaderBannerRef.get();
            if (searchHeaderBanner.getCurrentQuery() == null) {
                LOG.info("onBannerLoaded() hiding, no ongoing query available");
                searchHeaderBanner.setBannerViewVisibility(BannerType.ALL, false);
                return;
            }
            searchHeaderBanner.setBannerViewVisibility(BannerType.FALLBACK, false);
            searchHeaderBanner.setBannerViewVisibility(BannerType.MOPUB, true);
        }

        @Override
        public void onBannerFailed(MoPubView banner, MoPubErrorCode errorCode) {
            LOG.info("onBannerFailed");
            long timeSinceDismissal = System.currentTimeMillis() - lastDismissed;
            if (timeSinceDismissal < dismissIntervalInMs) {
                LOG.info("onBannerFailed() fallback loading aborted, too early after dismissal");
                return;
            }
            if (!Ref.alive(searchHeaderBannerRef)) {
                LOG.info("onBannerFailed() aborted, searchHeaderBanner reference lost");
                return;
            }
            SearchHeaderBanner searchHeaderBanner = searchHeaderBannerRef.get();
            if (searchHeaderBanner.moPubView != null) {
                searchHeaderBanner.setBannerViewVisibility(BannerType.MOPUB, false);
                searchHeaderBanner.moPubView.destroy();
            }
            if (searchHeaderBanner.fallbackBannerView.getVisibility() == View.GONE) {
                searchHeaderBanner.loadFallbackBanner();
            }
        }

        @Override
        public void onBannerClicked(MoPubView banner) {
            //LOG.info("onBannerClicked: " + banner);
            banner.forceRefresh();
            if (!Ref.alive(searchHeaderBannerRef)) {
                LOG.info("onBannerClicked() aborted, searchHeaderBanner reference lost");
                return;
            }
            SearchHeaderBanner searchHeaderBanner = searchHeaderBannerRef.get();
            if (searchHeaderBanner.moPubView != null) {
                searchHeaderBanner.setBannerViewVisibility(BannerType.MOPUB, false);
            }
            if (searchHeaderBanner.fallbackBannerView.getVisibility() == View.GONE) {
                searchHeaderBanner.loadFallbackBanner();
            }
        }

        @Override
        public void onBannerExpanded(MoPubView banner) {
        }

        @Override
        public void onBannerCollapsed(MoPubView moPubView) {
            //LOG.info("onBannerCollapsed");
        }

        public void onDestroy() {
            //LOG.info("HeaderBannerListener.onDestroy()");
            if (!Ref.alive(searchHeaderBannerRef)) {
                LOG.warn("HeaderBannerListener.onDestroy(): check your logic. Could not correctly destroy moPubView, banner reference lost");
                return;
            }
            SearchHeaderBanner searchHeaderBanner = searchHeaderBannerRef.get();
            try {
                searchHeaderBanner.setBannerViewVisibility(BannerType.ALL, false);
                if (searchHeaderBanner.moPubView != null) {
                    searchHeaderBanner.moPubView.destroy();
                    LOG.info("HeaderBannerListener.onDestroy() success");
                }
            } catch (Throwable throwable) {
                LOG.error(throwable.getMessage(), throwable);
            }
        }
    }

    private static final class DismissBannerButtonClickListener implements OnClickListener {
        private final WeakReference<SearchHeaderBanner> searchHeaderBannerRef;

        DismissBannerButtonClickListener(SearchHeaderBanner searchHeaderBanner) {
            searchHeaderBannerRef = Ref.weak(searchHeaderBanner);
        }

        @Override
        public void onClick(View view) {
            if (!Ref.alive(searchHeaderBannerRef)) {
                return;
            }
            SearchHeaderBanner searchHeaderBanner = searchHeaderBannerRef.get();
            BannerType bannerType = BannerType.MOPUB;
            if (searchHeaderBanner.fallbackBannerView.getVisibility() == View.VISIBLE &&
                    searchHeaderBanner.moPubView.getVisibility() == View.GONE) {
                bannerType = BannerType.FALLBACK;
            }
            searchHeaderBanner.onBannerDismiss(bannerType);
        }
    }

    private static final class FallbackBannerOnClickListener implements OnClickListener {
        private final WeakReference<SearchHeaderBanner> searchHeaderBanner;


        FallbackBannerOnClickListener(SearchHeaderBanner searchFragment) {
            this.searchHeaderBanner = Ref.weak(searchFragment);
        }

        @Override
        public void onClick(View view) {
            if (!Ref.alive(searchHeaderBanner)) {
                return;
            }
            SearchHeaderBanner searchHeaderBanner = this.searchHeaderBanner.get();
            searchHeaderBanner.setBannerViewVisibility(BannerType.ALL, false);
            // basic or debug
            if (Constants.IS_BASIC_AND_DEBUG || Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
                Activity activity = (Activity) searchHeaderBanner.getContext();
                activity.startActivity(new Intent(view.getContext(), BuyActivity.class));
            }
            // plus (donate)
            else {
                UIUtils.openURL(view.getContext(), Constants.FROSTWIRE_GIVE_URL + "plus-search-fallback");
            }
        }
    }
}
