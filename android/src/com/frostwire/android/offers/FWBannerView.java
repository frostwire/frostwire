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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxAdView;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 6/10/18.
 */

public class FWBannerView extends LinearLayout {
    ////////////// EOF OLD MOPUB UNIT_IDS //////////////
    // In XML: maxads:adUnitId="YOUR_AD_UNIT_ID"
    private static final String TEST_320X50_BANNER = "b195f8dd8ded45fe847ad89ed1d016da";
    private static final String TEST_300X250_MEDIUM_RECTANGLE = "252412d5e9364a05ab77d9396346d73d";
    private static final String TEST_UNIT_INTERSTITIAL = "24534e1901884e398f1253216226017e";
    private static final String TEST_UNIT_REWARDED_VIDEO = "920b6145fb1546cf8b5cf2ac34638bb7";

    public static final String UNIT_ID_HOME = (Offers.DEBUG_MODE) ? TEST_300X250_MEDIUM_RECTANGLE : "84abad953f40b933"; // aka 300×250 Search Screen
    public static final String UNIT_ID_PREVIEW_PLAYER_VERTICAL = (Offers.DEBUG_MODE) ? TEST_320X50_BANNER : "c902517bad27d4d7";
    static final String UNIT_ID_SEARCH_HEADER = (Offers.DEBUG_MODE) ? TEST_320X50_BANNER : "95c5db83dbc84c6b";

    public static final String UNIT_ID_AUDIO_PLAYER = (Offers.DEBUG_MODE) ? TEST_320X50_BANNER : "c902517bad27d4d7";
    public static final String UNIT_ID_PREVIEW_PLAYER_HORIZONTAL = (Offers.DEBUG_MODE) ? TEST_300X250_MEDIUM_RECTANGLE : "bd3bb3726c452b46";

    public static final String UNIT_ID_INTERSTITIAL_MOBILE = (Offers.DEBUG_MODE) ? TEST_UNIT_INTERSTITIAL : "caa48a59157a1b9b";
    public static final String UNIT_ID_REWARDED_AD = (Offers.DEBUG_MODE) ? TEST_UNIT_REWARDED_VIDEO : "c6415bf846434934";
    ////////////// EOF OLD MOPUB UNIT_IDS //////////////


    public enum Layers {
        APPLOVIN,
        FALLBACK,
        ALL
    }

    public static class LayersVisibility {
        Layers layers;
        boolean visible;
    }

    public interface OnBannerDismissedListener {
        void dispatch();
    }

    public interface OnBannerLoadedListener {
        void dispatch();
    }

    private static final Logger LOG = Logger.getLogger(FWBannerView.class);
    private ImageButton dismissBannerButton;
    private MaxAdView maxAdView320x50;
    private MaxAdView maxAdView320x250;
    private MaxAdView maxAdView; // the visible version of the ad
    private MaxAd maxAd; // gets set when the ad is loaded and the listener is notified
    private ImageView fallbackBannerView;
    private TextView mAdvertisementText;
    private TextView removeAdsTextView;
    private OnBannerLoadedListener onBannerLoadedListener;
    private OnBannerDismissedListener onBannerDismissedListener;
    private long lastInitAlbumArtBanner;
    private boolean isHidden;
    private boolean isLoaded;
    private boolean showFallbackBannerOnDismiss;
    private boolean showDismissButton;
    private boolean showRemoveAdsTextView;
    private final LayersVisibility layersVisibility = new LayersVisibility();

    private String adUnitId;

    public FWBannerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, true, true, true, null);
    }

    public FWBannerView(
            Context context,
            @Nullable AttributeSet attrs,
            boolean showFallbackBannerOnDismiss,
            boolean showDismissButton,
            boolean showRemoveAdsTextView,
            String adId) {
        super(context, attrs);
        onBannerDismissedListener = null;
        onBannerLoadedListener = null;
        this.showFallbackBannerOnDismiss = showFallbackBannerOnDismiss;
        this.showDismissButton = showDismissButton;
        this.showRemoveAdsTextView = showRemoveAdsTextView;
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater != null) {
            try {
                inflater.inflate(R.layout.view_frostwire_banner, this, true);
                TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.FWBannerView);
                if (adId == null) {
                    adUnitId = typedArray.getString(R.styleable.FWBannerView_adUnitId);
                } else {
                    adUnitId = adId;
                }

                this.showRemoveAdsTextView = typedArray.getBoolean(R.styleable.FWBannerView_showRemoveAdsTextView, this.showRemoveAdsTextView);
                onFinishInflate();
                typedArray.recycle();
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
                t.printStackTrace();
            }
        }

        if (attrs == null) {
            LinearLayout.LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            layoutParams.setLayoutDirection(LinearLayout.VERTICAL);
            layoutParams.setMargins(0, 0, 0, !showRemoveAdsTextView ? 20 : 0);
            setLayoutParams(layoutParams);
        }
    }

    public void setShowFallbackBannerOnDismiss(boolean showFallbackOnDismiss) {
        showFallbackBannerOnDismiss = showFallbackOnDismiss;
    }

    public void setShowDismissButton(boolean showDismissButton) {
        this.showDismissButton = showDismissButton;
        if (dismissBannerButton != null) {
            dismissBannerButton.setVisibility(showDismissButton ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void setOnBannerLoadedListener(OnBannerLoadedListener onBannerLoadedListener) {
        this.onBannerLoadedListener = onBannerLoadedListener;
    }

    public void setOnBannerDismissedListener(OnBannerDismissedListener onBannerDismissedListener) {
        this.onBannerDismissedListener = onBannerDismissedListener;
    }

    @Override
    protected void onFinishInflate() {
        dismissBannerButton = findViewById(R.id.fwbanner_dismiss_maxview_button);
        dismissBannerButton.setOnClickListener(onDismissBannerOnClickListener);
        dismissBannerButton.setClickable(true);
        dismissBannerButton.setVisibility(showDismissButton ? View.VISIBLE : View.INVISIBLE);
        fallbackBannerView = findViewById(R.id.fwbanner_fallback_imageview);
        fallbackBannerView.setClickable(true);
        mAdvertisementText = findViewById(R.id.fwbanner_advertisement_text);
        mAdvertisementText.setOnClickListener(onDismissBannerOnClickListener);
        mAdvertisementText.setClickable(true);
        removeAdsTextView = findViewById(R.id.fwbanner_remove_ads_text_link);
        removeAdsTextView.setClickable(true);
        removeAdsTextView.setOnClickListener(removeAdsTextViewOnClickListener);

        // Get references to the 2 possible versions of the add, currently with visibility=GONE
        maxAdView320x50 = findViewById(R.id.applovin_banner_maxadview_320x50);
        maxAdView320x50.setClickable(true);
        maxAdView320x250 = findViewById(R.id.applovin_banner_maxadview_320x250);
        maxAdView320x250.setClickable(true);

        //by default let's assign the small one as the current one
        if (adUnitId == null) {
            maxAdView = maxAdView320x50;
        } else {
            pickMaxAdViewByAdUnitId();
        }

        super.onFinishInflate();
    }

    private void pickMaxAdViewByAdUnitId() {
        if (FWBannerView.UNIT_ID_HOME.equals(adUnitId) || FWBannerView.UNIT_ID_PREVIEW_PLAYER_HORIZONTAL.equals(adUnitId)) {
            maxAdView = maxAdView320x250;
        }
        if (FWBannerView.UNIT_ID_PREVIEW_PLAYER_VERTICAL.equals(adUnitId)) {
            maxAdView = maxAdView320x50;
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public void loadMaxBanner() {
        isLoaded = false;
        long timeSinceLastBannerInit = System.currentTimeMillis() - lastInitAlbumArtBanner;
        if (timeSinceLastBannerInit < 5000) {
            LOG.info("loadMoPubBanner() aborted, too soon to attempt another banner load");
            return;
        }
        if (Offers.disabledAds()) {
            return;
        }

        pickMaxAdViewByAdUnitId();

        lastInitAlbumArtBanner = System.currentTimeMillis();
        if (maxAdView != null && dismissBannerButton != null) {
            if (!AppLovinAdNetwork.getInstance().started()) {
                LOG.warn("loadMoPubBanner() abort moPubView loading, MOPUB not started. Loading fallback");
                loadFallbackBanner(adUnitId);
                return;
            }
            loadFallbackBanner(adUnitId);
            try {
                maxAdView.setPlacement(adUnitId);
                maxAdView.setListener(maxAdViewAdListener);
                maxAdView.loadAd();
                maxAdView.startAutoRefresh();
            } catch (Throwable e) {
                LOG.warn("loadMoPubBanner() MopubBannerView banner could not be loaded", e);
                e.printStackTrace();
                loadFallbackBanner(adUnitId);
                maxAdView.destroy();
            }
        }
    }

    public void loadFallbackBanner(final String adUnitId) {
        SystemUtils.postToUIThread(() ->
                {
                    try {
                        InHouseBannerFactory.AdFormat adFormat;
                        if (FWBannerView.UNIT_ID_PREVIEW_PLAYER_HORIZONTAL.equals(adUnitId) ||
                                FWBannerView.UNIT_ID_HOME.equals(adUnitId)) {
                            adFormat = InHouseBannerFactory.AdFormat.BIG_300x250;
                        } else if (FWBannerView.UNIT_ID_SEARCH_HEADER.equals(adUnitId) ||
                                FWBannerView.UNIT_ID_AUDIO_PLAYER.equals(adUnitId)) {
                            adFormat = InHouseBannerFactory.AdFormat.SMALL_320x50;
                        } else {
                            throw new IllegalArgumentException("MopubBannerView.loadFallbackBanner() - invalid/unknown adUnitId <" + adUnitId + ">");
                        }

                        if (maxAdView != null) {
                            maxAdView.stopAutoRefresh();
                        }

                        try {
                            InHouseBannerFactory.loadAd(fallbackBannerView, adFormat);
                        } catch (Throwable t) {
                            setLayersVisibility(Layers.ALL, false);
                            if (onBannerDismissedListener != null) {
                                try {
                                    onBannerDismissedListener.dispatch();
                                } catch (Throwable t2) {
                                    t2.printStackTrace();
                                }
                            }
                            return;
                        }
                        setLayersVisibility(Layers.FALLBACK, true);
                        dismissBannerButton.setVisibility(showDismissButton ? View.VISIBLE : View.INVISIBLE);
                    } catch (Throwable t) {
                        LOG.error("loadFallbackBanner() error " + t.getMessage(), t);
                        if (BuildConfig.DEBUG) {
                            throw t;
                        }
                    }
                }
        );
    }

    public void setLayersVisibility(final Layers layers, final boolean visible) {
        isHidden = false;
        if (layers == Layers.ALL) {
            isHidden = !visible;
            if (isHidden) {
                setControlsVisibility(View.GONE);
                maxAdView.stopAutoRefresh();
            } else {
                maxAdView.startAutoRefresh();
            }
            setBannerViewVisibility(maxAdView, visible);
            setBannerViewVisibility(fallbackBannerView, visible);
        } else if (layers == Layers.APPLOVIN) {
            setBannerViewVisibility(fallbackBannerView, !visible);
            setControlsVisibility(View.VISIBLE);
            setBannerViewVisibility(maxAdView, visible);

            if (visible) {
                maxAdView.startAutoRefresh();
            } else {
                maxAdView.stopAutoRefresh();
            }
        } else if (layers == Layers.FALLBACK) {
            setControlsVisibility(View.VISIBLE);
            setBannerViewVisibility(maxAdView, !visible);
            setBannerViewVisibility(fallbackBannerView, visible);
            if (visible) {
                maxAdView.stopAutoRefresh();
            } else {
                maxAdView.startAutoRefresh();
            }
        }
        setVisibility(isHidden ? View.GONE : View.VISIBLE);
        layersVisibility.layers = layers;
        layersVisibility.visible = visible;
    }

    public boolean areLayerVisible(final Layers layers) {
        return layersVisibility.layers == layers
                && layersVisibility.visible;
    }

    public void destroy() {
        try {
            if (maxAdView != null) {
                maxAdView.destroy();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public boolean isHidden() {
        return isHidden;
    }

    private void setBannerViewVisibility(View bannerView, boolean visible) {
        if (bannerView != null) {
            bannerView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void setControlsVisibility(int controlsVisibility) {
        if (dismissBannerButton != null) {
            if (showDismissButton) {
                dismissBannerButton.setVisibility(controlsVisibility);
            } else {
                dismissBannerButton.setVisibility(View.INVISIBLE);
            }
        }
        if (mAdvertisementText != null) {
            mAdvertisementText.setVisibility(controlsVisibility);
        }
        if (removeAdsTextView != null) {
            int visibility = View.GONE;
            if (showRemoveAdsTextView &&
                    Offers.removeAdsOffersEnabled() &&
                    controlsVisibility == View.VISIBLE) {
                visibility = View.VISIBLE;
            }
            removeAdsTextView.setVisibility(visibility);
        }
    }

    private final MaxAdViewAdListener maxAdViewAdListener = new MaxAdViewAdListener() {
        @Override
        public void onAdExpanded(MaxAd ad) {
        }

        @Override
        public void onAdCollapsed(MaxAd banner) {
            LOG.info("onBannerCollapsed(): " + banner);
            setLayersVisibility(Layers.ALL, false);
            isLoaded = false;
            if (onBannerDismissedListener != null) {
                try {
                    onBannerDismissedListener.dispatch();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }

        }

        @Override
        public void onAdLoaded(MaxAd banner) {
            LOG.info("onBannerLoaded(): " + banner);
            isLoaded = true;
            maxAdView.setVisibility(View.VISIBLE);
            setLayersVisibility(Layers.APPLOVIN, true);
            maxAd = banner;

            if (onBannerLoadedListener != null) {
                try {
                    onBannerLoadedListener.dispatch();
                } catch (Throwable t) {
//                    onFallbackBannerLoadedListener.dispatch();
                    t.printStackTrace();
                }
            }
        }

        @Override
        public void onAdDisplayed(MaxAd ad) { /** DO NOT USE - RESERVED FOR FULLSCREEN ADS ONLY AND WILL BE REMOVED*/}

        @Override
        public void onAdHidden(MaxAd ad) { /** DO NOT USE - RESERVED FOR FULLSCREEN ADS ONLY AND WILL BE REMOVED*/}

        @Override
        public void onAdClicked(MaxAd ad) {
            LOG.info("onBannerClicked(): " + ad);
            if (showFallbackBannerOnDismiss) {
                setLayersVisibility(Layers.FALLBACK, true);
            }
        }

        @Override
        public void onAdLoadFailed(String adUnitId, MaxError error) {
            LOG.info("onBannerFailed(errorCode=" + error.getCode() + "): " + adUnitId);
            setLayersVisibility(Layers.FALLBACK, showFallbackBannerOnDismiss);
            isLoaded = false;
            // NOTE: I've tried waiting on a background thread for 6-10 seconds to re-invoke loadMoPubBanner (on main thread)
            // and if there's no ads, there's no ads, it's better to let MoPub reload on its own.
        }

        @Override
        public void onAdDisplayFailed(MaxAd ad, MaxError error) {
            LOG.info("onBannerFailed(errorCode=" + error.getCode() + "): " + ad.getAdUnitId());
            setLayersVisibility(Layers.FALLBACK, showFallbackBannerOnDismiss);
            isLoaded = false;
        }
    };

    private final OnClickListener onDismissBannerOnClickListener = view -> {
        if (maxAdView.getVisibility() == View.VISIBLE) {
            setLayersVisibility(
                    showFallbackBannerOnDismiss ? Layers.FALLBACK : Layers.ALL,
                    showFallbackBannerOnDismiss);
            if (maxAd != null) {
                maxAdViewAdListener.onAdCollapsed(maxAd);
            }
        } else if (fallbackBannerView.getVisibility() == View.VISIBLE) {
            setLayersVisibility(Layers.ALL, false);
        }
    };

    private final OnClickListener removeAdsTextViewOnClickListener = view -> {
        setLayersVisibility(Layers.ALL, false);
        Intent i = new Intent(getContext(), BuyActivity.class);
        if (getContext() instanceof Activity) {
            ((Activity) getContext()).startActivityForResult(i, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
        } else {
            throw new RuntimeException("MopubSquaredBanner.onClick(): Context is not an activity, what's up with that?");
        }
    };
}
