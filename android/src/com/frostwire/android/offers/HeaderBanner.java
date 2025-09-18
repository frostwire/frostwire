/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxAdView;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public final class HeaderBanner extends LinearLayout {

    private static final Logger LOG = Logger.getLogger(HeaderBanner.class);
    private long lastLoadAttemptTimestamp = 0L;

    public enum VisibleBannerType {
        APPLOVIN,
        FALLBACK,
        ALL
    }

    private LinearLayout bannerHeaderLayout;
    private ImageButton dismissBannerButton;
    private MaxAdView maxAdView;
    private LinearLayout fallbackBannerView;
    private TextView fallbackBannerTextView;

    private HeaderBannerListener moPubBannerListener;

    private boolean isAdLoaded = false;  // Track ad loading state

    public static void onResumeHideOrUpdate(HeaderBanner component) {
        if (component != null) {
            if (Offers.disabledAds()) {
                component.setBannerViewVisibility(VisibleBannerType.ALL, false);
            } else {
                component.updateComponents();
            }
        }
    }

    public static void destroy(HeaderBanner component) {
        if (component != null) {
            component.onDestroy();
        }
    }

    public HeaderBanner(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater != null) {
            try {
                inflater.inflate(R.layout.view_header_banner, this, true);
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
                t.printStackTrace();
            }
        }
    }

    public void onDestroy() {
        bannerHeaderLayout = null;
        fallbackBannerView = null;
        fallbackBannerTextView = null;
        getHeaderBannerListener().onDestroy(); // calls moPubView.onDestroy() and unregisters its IntentReceiver
    }

    private HeaderBannerListener getHeaderBannerListener() {
        if (moPubBannerListener == null) {
            moPubBannerListener = new HeaderBannerListener(this);
        }
        return moPubBannerListener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        bannerHeaderLayout = findViewById(R.id.fragment_search_advertisement_header_layout);
        dismissBannerButton = findViewById(R.id.view_search_header_banner_dismiss_banner_button);
        maxAdView = findViewById(R.id.view_search_header_banner_maxadview);
        maxAdView.setVisibility(View.GONE);
        maxAdView.stopAutoRefresh();
        fallbackBannerView = findViewById(R.id.view_search_header_banner_fallback_banner_linear_layout);
        fallbackBannerTextView = findViewById(R.id.view_search_header_banner_fallback_banner_textview);
        bannerHeaderLayout.setVisibility(View.GONE);
    }

    public void updateComponents() {
        if (Offers.disabledAds()) {
            return;
        }

        // Add a simple debounce check: don't reload if loaded recently
        long now = System.currentTimeMillis();
        if ((now - lastLoadAttemptTimestamp) < 2000) {
            // e.g., 2-second debounce
            LOG.info("HeaderBanner.updateComponents() - Debounced repeated reload");
            return;
        }
        lastLoadAttemptTimestamp = now;

        boolean adsDisabled = Offers.disabledAds();
        Activity activity = (Activity) getContext();
        // check how long getting display metrics twice is, if expensive gotta refactor these methods
        boolean screenTallEnough = UIUtils.getScreenInches(activity) >= 4.33;
        boolean diceRollPassed = true;//UIUtils.diceRollPassesThreshold(ConfigurationManager.instance(), Constants.PREF_KEY_GUI_MOPUB_SEARCH_HEADER_BANNER_THRESHOLD);
        boolean bannerVisible = !adsDisabled && screenTallEnough && diceRollPassed && !getHeaderBannerListener().tooEarlyToDisplay();
        if (!bannerVisible) {
            LOG.info("updateComponents(): not eligible for search banner display. adsDisabled=" + adsDisabled +
                    ", screenTallEnough=" + screenTallEnough +
                    ", diceRollPassed=" + diceRollPassed +
                    ", tooEarlyToDisplay=" + getHeaderBannerListener().tooEarlyToDisplay());
            setBannerViewVisibility(VisibleBannerType.ALL, false);
            return;
        }
        setBannerViewVisibility(VisibleBannerType.APPLOVIN, false);
        loadFallbackBanner();
        dismissBannerButton.setOnClickListener(new DismissBannerButtonClickListener(this));

        if (!AppLovinAdNetwork.getInstance().started()) {
            return;
        }

        try {
            maxAdView.setListener(getHeaderBannerListener());
            maxAdView.loadAd();
            maxAdView.startAutoRefresh();
            LOG.info("updateComponents(): moPubView.loadAd()");
        } catch (Throwable e) {
            LOG.warn("updateComponents(): SearchFragment Mopub banner could not be loaded", e);
            loadFallbackBanner();
            maxAdView.destroy();
        }
    }

    /**
     * You are responsible for hiding and showing every banner
     */
    public void setBannerViewVisibility(VisibleBannerType bannerType, boolean visible) {
        if (bannerHeaderLayout == null) {
            onFinishInflate();
        }
        // LOG.info("setBannerViewVisibility(" + bannerType + ",visible=" + visible + ")");
        if (Offers.disabledAds()) {
            LOG.info("setBannerViewVisibility() aborted. Offers disabled");
            bannerHeaderLayout.setVisibility(View.GONE);
            maxAdView.setVisibility(View.GONE);
            fallbackBannerView.setVisibility(View.GONE);
            return;
        }
        // LOG.info("setBannerViewVisibility() -> bannerHeaderLayout@"+bannerHeaderLayout.hashCode());
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (bannerType == VisibleBannerType.ALL) {
            // LOG.info("setBannerViewVisibility() hide everything");
            maxAdView.setVisibility(visibility);
            fallbackBannerView.setVisibility(visibility);
        } else if (bannerType == VisibleBannerType.APPLOVIN) {
            maxAdView.setVisibility(visibility);
        } else if (bannerType == VisibleBannerType.FALLBACK) {
            fallbackBannerView.setVisibility(visibility);
        }
        bannerHeaderLayout.setVisibility(visibility);
        // LOG.info("setBannerViewVisibility() bannerHeaderLayout.visible==" + (bannerHeaderLayout.getVisibility() == View.VISIBLE));
    }

    private void onBannerDismiss(VisibleBannerType bannerType) {
        if (bannerHeaderLayout != null) {
            getHeaderBannerListener().onBannerDismissed(bannerType);
            if (bannerType == VisibleBannerType.APPLOVIN) {
                loadFallbackBanner();
            } else if (bannerType == VisibleBannerType.FALLBACK) {
                bannerHeaderLayout.setVisibility(View.GONE);
            }
        }
    }

    private void loadFallbackBanner() {
        if (Offers.disabledAds()) {
            LOG.info("loadFallbackBanner() aborted. Offers disabled");
            setBannerViewVisibility(VisibleBannerType.FALLBACK, false);
            return;
        }
        if (getHeaderBannerListener().tooEarlyToDisplay()) {
            LOG.info("loadFallbackBanner() aborted. too early to display");
            setBannerViewVisibility(VisibleBannerType.ALL, false);
            return;
        }
        maxAdView.stopAutoRefresh();
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
        setBannerViewVisibility(VisibleBannerType.FALLBACK, true);
        //LOG.info("loadFallbackBanner() finished");
    }

    private static final class HeaderBannerListener implements MaxAdViewAdListener {
        private final WeakReference<HeaderBanner> headerBannerRef;
        private long lastDismissed = 0L;
        private final int dismissIntervalInMs;

        HeaderBannerListener(HeaderBanner searchFragment) {
            headerBannerRef = Ref.weak(searchFragment);
            dismissIntervalInMs = ConfigurationManager.instance().getInt(Constants.PREF_KEY_GUI_MOPUB_SEARCH_HEADER_BANNER_DISMISS_INTERVAL_IN_MS);
        }

        public boolean tooEarlyToDisplay() {
            return (System.currentTimeMillis() - lastDismissed) < dismissIntervalInMs;
        }

        public void onBannerDismissed(VisibleBannerType bannerType) {
            //LOG.info("onBannerDismissed(bannerType=" + bannerType + ")");
            if (bannerType == VisibleBannerType.FALLBACK) {
                // only changes when the banner container is fully dismissed
                lastDismissed = System.currentTimeMillis();
            }
            if (!Ref.alive(headerBannerRef)) {
                return;
            }
            headerBannerRef.get().setBannerViewVisibility(bannerType, false);
        }

        public void onDestroy() {
            //LOG.info("HeaderBannerListener.onDestroy()");
            if (!Ref.alive(headerBannerRef)) {
                LOG.warn("HeaderBannerListener.onDestroy(): check your logic. Could not correctly destroy moPubView, banner reference lost");
                return;
            }
            HeaderBanner headerBanner = headerBannerRef.get();
            try {
                headerBanner.setBannerViewVisibility(VisibleBannerType.ALL, false);
                if (headerBanner.maxAdView != null) {
                    headerBanner.maxAdView.destroy();
                    LOG.info("HeaderBannerListener.onDestroy() success");
                }
            } catch (Throwable throwable) {
                LOG.error(throwable.getMessage(), throwable);
            }
        }

        @Override
        public void onAdExpanded(MaxAd ad) {

        }

        @Override
        public void onAdCollapsed(MaxAd ad) {

        }

        @Override
        public void onAdLoaded(MaxAd ad) {
            if (tooEarlyToDisplay()) {
                LOG.info("onBannerLoaded() aborted, too early after dismissal");
                return;
            }
            if (!Ref.alive(headerBannerRef)) {
                LOG.info("onBannerLoaded() aborted, searchHeaderBanner reference lost");
                return;
            }
            HeaderBanner headerBanner = headerBannerRef.get();
            headerBanner.dismissBannerButton.setVisibility(View.VISIBLE);

            headerBanner.setBannerViewVisibility(VisibleBannerType.FALLBACK, false);
            headerBanner.setBannerViewVisibility(VisibleBannerType.APPLOVIN, true);
        }

        @Override
        public void onAdDisplayed(MaxAd ad) {

        }

        @Override
        public void onAdHidden(MaxAd ad) {

        }

        @Override
        public void onAdClicked(MaxAd ad) {
            if (!Ref.alive(headerBannerRef)) {
                LOG.info("onAdClicked() aborted, searchHeaderBanner reference lost");
                return;
            }
            HeaderBanner headerBanner = headerBannerRef.get();
            if (!Ref.alive(headerBannerRef)) {
                LOG.info("onAdClicked() aborted, searchHeaderBanner reference lost");
                return;
            }
            if (headerBanner.maxAdView != null) {
                headerBanner.setBannerViewVisibility(VisibleBannerType.APPLOVIN, false);
            }
            if (headerBanner.fallbackBannerView.getVisibility() == View.GONE) {
                headerBanner.loadFallbackBanner();
            }
        }

        @Override
        public void onAdLoadFailed(String adUnitId, MaxError error) {
            LOG.info("onBannerFailed");
            long timeSinceDismissal = System.currentTimeMillis() - lastDismissed;
            if (timeSinceDismissal < dismissIntervalInMs) {
                LOG.info("onBannerFailed() fallback loading aborted, too early after dismissal");
                return;
            }
            if (!Ref.alive(headerBannerRef)) {
                LOG.info("onBannerFailed() aborted, searchHeaderBanner reference lost");
                return;
            }
            HeaderBanner headerBanner = headerBannerRef.get();
            headerBanner.dismissBannerButton.setVisibility(View.INVISIBLE);
            if (headerBanner.maxAdView != null) {
                headerBanner.setBannerViewVisibility(VisibleBannerType.APPLOVIN, false);
                headerBanner.maxAdView.destroy();
            }
            if (headerBanner.fallbackBannerView.getVisibility() == View.GONE) {
                headerBanner.loadFallbackBanner();
            }
        }

        @Override
        public void onAdDisplayFailed(MaxAd ad, MaxError error) {

        }
    }

    private static final class DismissBannerButtonClickListener implements OnClickListener {
        private final WeakReference<HeaderBanner> headerBannerRef;

        DismissBannerButtonClickListener(HeaderBanner headerBanner) {
            headerBannerRef = Ref.weak(headerBanner);
        }

        @Override
        public void onClick(View view) {
            if (!Ref.alive(headerBannerRef)) {
                return;
            }
            headerBannerRef.get().dismissBannerButton.setVisibility(View.INVISIBLE);
            HeaderBanner headerBanner = headerBannerRef.get();
            VisibleBannerType bannerType = VisibleBannerType.APPLOVIN;
            if (headerBanner.fallbackBannerView.getVisibility() == View.VISIBLE &&
                    headerBanner.maxAdView.getVisibility() == View.GONE) {
                bannerType = VisibleBannerType.FALLBACK;
            }
            headerBanner.onBannerDismiss(bannerType);
        }
    }

    private static final class FallbackBannerOnClickListener implements OnClickListener {
        private final WeakReference<HeaderBanner> headerBannerRef;


        FallbackBannerOnClickListener(HeaderBanner searchFragment) {
            this.headerBannerRef = Ref.weak(searchFragment);
        }

        @Override
        public void onClick(View view) {
            if (!Ref.alive(headerBannerRef)) {
                return;
            }
            HeaderBanner headerBanner = this.headerBannerRef.get();
            headerBanner.setBannerViewVisibility(VisibleBannerType.ALL, false);
            // basic or debug
            if (Constants.IS_BASIC_AND_DEBUG || Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
                Activity activity = (Activity) headerBanner.getContext();
                activity.startActivity(new Intent(view.getContext(), BuyActivity.class));
            }
            // plus (donate)
            else {
                UIUtils.openURL(view.getContext(), Constants.FROSTWIRE_GIVE_URL + "plus-search-fallback");
            }
        }
    }
}
