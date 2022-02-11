/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
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


package com.frostwire.android.offers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
    private MaxAdView maxAdView;
    private MaxAd maxAd; // gets set when the ad is loaded and the listener is notified
    private String adUnitId;
    private ImageView fallbackBannerView;
    private TextView mAdvertisementText;
    private TextView removeAdsTextView;
    private OnBannerLoadedListener onBannerLoadedListener;
    private OnBannerDismissedListener onBannerDismissedListener;
    private OnBannerLoadedListener onFallbackBannerLoadedListener;
    private OnBannerDismissedListener onFallbackBannerDismissedListener;
    private long lastInitAlbumArtBanner;
    private boolean isHidden;
    private boolean isLoaded;
    private boolean showFallbackBannerOnDismiss;
    private boolean showDismissButton;
    private boolean showRemoveAdsTextView;
    private final LayersVisibility layersVisibility = new LayersVisibility();

    public FWBannerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, true, true, true, null);
    }

    public FWBannerView(
            Context context,
            @Nullable AttributeSet attrs,
            boolean showFallbackBannerOnDismiss,
            boolean showDismissButton,
            boolean showRemoveAdsTextView,
            String adUnitId) {
        super(context, attrs);
        this.adUnitId = adUnitId;
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
                onFinishInflate();
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
        } else if (adUnitId == null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.FWBannerView);
            this.adUnitId = typedArray.getString(R.styleable.FWBannerView_adUnitId);
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

    public void setOnFallbackBannerLoadedListener(OnBannerLoadedListener onFallbackBannerLoadedListener) {
        this.onFallbackBannerLoadedListener = onFallbackBannerLoadedListener;
    }

    public void setOnFallbackBannerDismissedListener(OnBannerDismissedListener onFallbackBannerDismissedListener) {
        this.onFallbackBannerDismissedListener = onFallbackBannerDismissedListener;
    }

    @Override
    protected void onFinishInflate() {
        dismissBannerButton = findViewById(R.id.mopub_banner_dismiss_mopubview_button);
        dismissBannerButton.setOnClickListener(onDismissBannerOnClickListener);
        dismissBannerButton.setClickable(true);
        dismissBannerButton.setVisibility(showDismissButton ? View.VISIBLE : View.INVISIBLE);
        fallbackBannerView = findViewById(R.id.mopub_banner_fallback_imageview);
        fallbackBannerView.setClickable(true);
        mAdvertisementText = findViewById(R.id.mopub_banner_advertisement_text);
        mAdvertisementText.setOnClickListener(onDismissBannerOnClickListener);
        mAdvertisementText.setClickable(true);
        removeAdsTextView = findViewById(R.id.mopub_banner_remove_ads_text_link);
        removeAdsTextView.setClickable(true);
        removeAdsTextView.setOnClickListener(removeAdsTextViewOnClickListener);

        maxAdView = new MaxAdView(this.adUnitId, getContext());
        /**
        <com.applovin.mediation.ads.MaxAdView
        android:id="@+id/applovin_banner_maxadview"
        android:layout_height="wrap_content"
        android:layout_width="wrap_content"
        android:layout_gravity="center_horizontal"
        android:visibility="gone"/>
         */
        maxAdView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        maxAdView.setVisibility(View.GONE);

        LinearLayout linearLayout = findViewById(R.id.fw_banner_linear_layout);
        linearLayout.addView(maxAdView, 1);

        maxAdView.setClickable(true);
        super.onFinishInflate();
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public void loadMaxBanner(final String adUnitId) {

        isLoaded = false;
        long timeSinceLastBannerInit = System.currentTimeMillis() - lastInitAlbumArtBanner;
        if (timeSinceLastBannerInit < 5000) {
            LOG.info("loadMoPubBanner() aborted, too soon to attempt another banner load");
            return;
        }
        if (Offers.disabledAds()) {
            return;
        }
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
                                FWBannerView.UNIT_ID_PREVIEW_PLAYER_VERTICAL.equals(adUnitId) ||
                                FWBannerView.UNIT_ID_AUDIO_PLAYER.equals(adUnitId)) {
                            adFormat = InHouseBannerFactory.AdFormat.SMALL_320x50;
                        } else {
                            throw new IllegalArgumentException("MopubBannerView.loadFallbackBanner() - invalid/unknown adUnitId <" + adUnitId + ">");
                        }

                        maxAdView.stopAutoRefresh();

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
                        if (onFallbackBannerLoadedListener != null) {
                            try {
                                onFallbackBannerLoadedListener.dispatch();
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }
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
            setLayersVisibility(Layers.APPLOVIN, true);
            maxAdView.setVisibility(View.INVISIBLE);
            maxAd = banner;

            if (onBannerLoadedListener != null) {
                try {
                    onBannerLoadedListener.dispatch();
                } catch (Throwable t) {
                    onFallbackBannerLoadedListener.dispatch();
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
            if (onFallbackBannerDismissedListener != null) {
                try {
                    onFallbackBannerDismissedListener.dispatch();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
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
