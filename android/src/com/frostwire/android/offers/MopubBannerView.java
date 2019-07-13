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
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.util.Logger;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubView;

import androidx.annotation.Nullable;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 6/10/18.
 */

public class MopubBannerView extends LinearLayout {
    public enum Visibility {
        MOPUB,
        FALLBACK,
        ALL
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
            LOG.info("initAlbumArtBanner() aborted, too soon to attempt another banner load");
            return;
        }
        if (Offers.disabledAds()) {
            return;
        }
        lastInitAlbumArtBanner = System.currentTimeMillis();
        if (moPubView != null && dismissBannerButton != null) {
            if (!Offers.MOPUB.started()) {
                LOG.warn("MopubBannerView.loadMoPubBanner() abort moPubView loading, MOPUB not started. Loading fallback");
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
                LOG.warn("MopubBannerView banner could not be loaded", e);
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
                    InHouseBannerFactory.AdFormat adFormat;
                    if (MoPubAdNetwork.UNIT_ID_AUDIO_PLAYER.equals(adUnitId) ||
                            MoPubAdNetwork.UNIT_ID_PREVIEW_PLAYER_HORIZONTAL.equals(adUnitId) ||
                            MoPubAdNetwork.UNIT_ID_HOME.equals(adUnitId)) {
                        adFormat = InHouseBannerFactory.AdFormat.BIG_300x250;
                    } else if (MoPubAdNetwork.UNIT_ID_SEARCH_HEADER.equals(adUnitId) ||
                            MoPubAdNetwork.UNIT_ID_PREVIEW_PLAYER_VERTICAL.equals(adUnitId)) {
                        adFormat = InHouseBannerFactory.AdFormat.SMALL_320x50;
                    } else {
                        throw new IllegalArgumentException("MopubBannerView.loadFallbackBanner() - invalid/unknown adUnitId <" + adUnitId + ">");
                    }
                    InHouseBannerFactory.loadAd(fallbackBannerView, adFormat);
                    setVisible(Visibility.FALLBACK, false);
                    setVisibility(View.INVISIBLE);
                    dismissBannerButton.setVisibility(showDismissButton ? View.VISIBLE : View.INVISIBLE);
                    if (onFallbackBannerLoadedListener != null) {
                        try {
                            onFallbackBannerLoadedListener.dispatch();
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
        );
    }

    public void setVisible(Visibility visibility, boolean visible) {
        isHidden = false;
        if (visibility == Visibility.ALL) {
            isHidden = !visible;
            if (isHidden) {
                setControlsVisibility(View.GONE);
            }
            setBannerViewVisibility(moPubView, visible);
            setBannerViewVisibility(fallbackBannerView, visible);
        } else if (visibility == Visibility.MOPUB) {
            setBannerViewVisibility(fallbackBannerView, !visible);
            setControlsVisibility(View.VISIBLE);
            setBannerViewVisibility(moPubView, visible);
        } else if (visibility == Visibility.FALLBACK) {
            setControlsVisibility(View.VISIBLE);
            setBannerViewVisibility(moPubView, !visible);
            setBannerViewVisibility(fallbackBannerView, visible);
        }
        setVisibility(isHidden ? View.GONE : View.VISIBLE);
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
            setVisible(Visibility.MOPUB, true);
            if (onBannerLoadedListener != null) {
                try {
                    onBannerLoadedListener.dispatch();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        @Override
        public void onBannerFailed(MoPubView banner, MoPubErrorCode errorCode) {
            LOG.info("onBannerFailed(errorCode=" + errorCode + "): " + banner);
            setVisible(Visibility.FALLBACK, true);
            isLoaded = false;
            // NOTE: I've tried waiting on a background thread for 6-10 seconds to re-invoke loadMoPubBanner (on main thread)
            // and if there's no ads, there's no ads, it's better to let MoPub reload on its own.
        }

        @Override
        public void onBannerClicked(MoPubView banner) {
            LOG.info("onBannerClicked(): " + banner);
            if (showFallbackBannerOnDismiss) {
                setVisible(Visibility.FALLBACK, true);
            }
        }

        @Override
        public void onBannerExpanded(MoPubView banner) {
            LOG.info("onBannerExpanded(): " + banner);
        }

        @Override
        public void onBannerCollapsed(MoPubView banner) {
            LOG.info("onBannerCollapsed(): " + banner);
            setVisible(Visibility.ALL, false);
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
            MoPubView.BannerAdListener bannerAdListener = moPubView.getBannerAdListener();
            setVisible(
                    showFallbackBannerOnDismiss ? Visibility.FALLBACK : Visibility.ALL,
                    showFallbackBannerOnDismiss);
            if (bannerAdListener != null) {
                bannerAdListener.onBannerCollapsed(moPubView);
            }
        } else if (fallbackBannerView.getVisibility() == View.VISIBLE) {
            setVisible(Visibility.ALL, false);
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
        setVisible(Visibility.ALL, false);
        Intent i = new Intent(getContext(), BuyActivity.class);
        if (getContext() instanceof Activity) {
            ((Activity) getContext()).startActivityForResult(i, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
        } else {
            throw new RuntimeException("MopubSquaredBanner.onClick(): Context is not an activity, what's up with that?");
        }
    };
}
