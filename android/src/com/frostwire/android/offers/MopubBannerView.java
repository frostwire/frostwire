/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
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
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.util.Logger;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubView;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 6/10/18.
 */

public class MopubBannerView extends LinearLayout {
    public enum Layers {
        MOPUB,
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

    private static final Logger LOG = Logger.getLogger(MopubBannerView.class);
    private ImageButton dismissBannerButton;
    private MoPubView moPubView;
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
    private final LayersVisibility layersVisibility = new LayersVisibility();

    public MopubBannerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, true, true);
    }

    public MopubBannerView(Context context, @Nullable AttributeSet attrs, boolean showFallbackBannerOnDismiss, boolean showDismissButton) {
        super(context, attrs);
        onBannerDismissedListener = null;
        onBannerLoadedListener = null;
        this.showFallbackBannerOnDismiss = showFallbackBannerOnDismiss;
        this.showDismissButton = showDismissButton;
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater != null) {
            try {
                inflater.inflate(R.layout.view_mopub_banner, this, true);
                onFinishInflate();
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
                t.printStackTrace();
            }
        }
        if (attrs == null) {
            LinearLayout.LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            layoutParams.setLayoutDirection(LinearLayout.VERTICAL);
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
        moPubView = findViewById(R.id.mopub_banner_mopubview);
        moPubView.setClickable(true);
        super.onFinishInflate();
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public void loadMoPubBanner(final String adUnitId) {
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
        if (moPubView != null && dismissBannerButton != null) {
            if (!Offers.MOPUB.started()) {
                LOG.warn("loadMoPubBanner() abort moPubView loading, MOPUB not started. Loading fallback");
                loadFallbackBanner(adUnitId);
                return;
            }
            loadFallbackBanner(adUnitId);
            moPubView.setTesting(false);
            moPubView.setAutorefreshEnabled(true);
            moPubView.setAdUnitId(adUnitId);
            moPubView.setBannerAdListener(moPubBannerListener);
            try {
                moPubView.loadAd();
            } catch (Throwable e) {
                LOG.warn("loadMoPubBanner() MopubBannerView banner could not be loaded", e);
                e.printStackTrace();
                loadFallbackBanner(adUnitId);
                moPubView.destroy();
            }
        }
    }

    private void loadFallbackBanner(final String adUnitId) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() ->
                {
                    try {
                        InHouseBannerFactory.AdFormat adFormat;
                        if (MoPubAdNetwork.UNIT_ID_AUDIO_PLAYER.equals(adUnitId) ||
                                MoPubAdNetwork.UNIT_ID_PREVIEW_PLAYER_HORIZONTAL.equals(adUnitId) ||
                                MoPubAdNetwork.UNIT_ID_HOME.equals(adUnitId)) {
                            adFormat = InHouseBannerFactory.AdFormat.BIG_300x250;
                        } else if (MoPubAdNetwork.UNIT_ID_SEARCH_HEADER.equals(adUnitId) ||
                                MoPubAdNetwork.UNIT_ID_PREVIEW_PLAYER_VERTICAL.equals(adUnitId)) {
                            adFormat = InHouseBannerFactory.AdFormat.BIG_300x250;
                        } else {
                            throw new IllegalArgumentException("MopubBannerView.loadFallbackBanner() - invalid/unknown adUnitId <" + adUnitId + ">");
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
            }
            setBannerViewVisibility(moPubView, visible);
            setBannerViewVisibility(fallbackBannerView, visible);
        } else if (layers == Layers.MOPUB) {
            setBannerViewVisibility(fallbackBannerView, !visible);
            setControlsVisibility(View.VISIBLE);
            setBannerViewVisibility(moPubView, visible);
        } else if (layers == Layers.FALLBACK) {
            setControlsVisibility(View.VISIBLE);
            setBannerViewVisibility(moPubView, !visible);
            setBannerViewVisibility(fallbackBannerView, visible);
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
            if (moPubView != null) {
                moPubView.destroy();
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
            dismissBannerButton.setVisibility(controlsVisibility);
        }
        if (mAdvertisementText != null) {
            mAdvertisementText.setVisibility(controlsVisibility);
        }
        if (removeAdsTextView != null) {
            removeAdsTextView.setVisibility(Offers.removeAdsOffersEnabled() && controlsVisibility == View.VISIBLE ?
                    View.VISIBLE : View.GONE);
        }
    }

    private final MoPubView.BannerAdListener moPubBannerListener = new MoPubView.BannerAdListener() {
        @Override
        public void onBannerLoaded(MoPubView banner) {
            LOG.info("onBannerLoaded(): " + banner);
            isLoaded = true;
            setLayersVisibility(Layers.MOPUB, true);
            banner.setVisibility(View.VISIBLE);
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
        public void onBannerFailed(MoPubView banner, MoPubErrorCode errorCode) {
            LOG.info("onBannerFailed(errorCode=" + errorCode + "): " + banner);
            setLayersVisibility(Layers.FALLBACK, showFallbackBannerOnDismiss);
            isLoaded = false;
            // NOTE: I've tried waiting on a background thread for 6-10 seconds to re-invoke loadMoPubBanner (on main thread)
            // and if there's no ads, there's no ads, it's better to let MoPub reload on its own.
        }

        @Override
        public void onBannerClicked(MoPubView banner) {
            LOG.info("onBannerClicked(): " + banner);
            if (showFallbackBannerOnDismiss) {
                setLayersVisibility(Layers.FALLBACK, true);
            }
        }

        @Override
        public void onBannerExpanded(MoPubView banner) {
            LOG.info("onBannerExpanded(): " + banner);
        }

        @Override
        public void onBannerCollapsed(MoPubView banner) {
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
    };

    private final OnClickListener onDismissBannerOnClickListener = view -> {
        if (moPubView.getVisibility() == View.VISIBLE) {
            setLayersVisibility(
                    showFallbackBannerOnDismiss ? Layers.FALLBACK : Layers.ALL,
                    showFallbackBannerOnDismiss);
            MoPubView.BannerAdListener bannerAdListener = moPubView.getBannerAdListener();
            if (bannerAdListener != null) {
                bannerAdListener.onBannerCollapsed(moPubView);
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
