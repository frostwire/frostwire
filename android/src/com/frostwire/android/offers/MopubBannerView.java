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
import android.support.annotation.Nullable;
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

    private static Logger LOG = Logger.getLogger(MopubBannerView.class);
    private ImageButton dismissBannerButton;
    private MoPubView moPubView;
    private ImageView fallbackBannerView;
    private TextView mAdvertisementText;
    private TextView removeAdsTextView;
    private OnBannerLoadedListener onBannerLoadedListener;
    private OnBannerDismissedListener onBannerDismissedListener;
    private long lastInitAlbumArtBanner;
    private boolean isHidden;
    private boolean isLoaded;
    private boolean showFallbackBannerOnDismiss;

    public MopubBannerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
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
        onBannerDismissedListener = null;
        onBannerLoadedListener = null;
        showFallbackBannerOnDismiss = true;
    }

    public void setShowFallbackBannerOnDismiss(boolean showFallbackOnDismiss) {
        showFallbackBannerOnDismiss = showFallbackOnDismiss;
    }

    public void setOnBannerLoadedListener(OnBannerLoadedListener onBannerLoadedListener) {
        this.onBannerLoadedListener = onBannerLoadedListener;
    }

    public void setOnBannerDismissedListener(OnBannerDismissedListener onBannerDismissedListener) {
        this.onBannerDismissedListener = onBannerDismissedListener;
    }

    @Override
    protected void onFinishInflate() {
        dismissBannerButton = findViewById(R.id.mopub_banner_dismiss_mopubview_button);
        dismissBannerButton.setOnClickListener(onDismissBannerOnClickListener);
        dismissBannerButton.setClickable(true);
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
            loadFallbackBanner(adUnitId);

            if (onBannerLoadedListener != null) {
                try {
                    onBannerLoadedListener.dispatch();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }

            if (!Offers.MOPUB.started()) {
                LOG.warn("MopubSquaredBanner.loadMoPubBanner() abort moPubView loading, MOPUB not started");
                return;
            }

            moPubView.setTesting(false);
            moPubView.setAutorefreshEnabled(true);
            moPubView.setAdUnitId(adUnitId);
            moPubView.setBannerAdListener(moPubBannerListener);

            try {
                moPubView.loadAd();
            } catch (Throwable e) {
                LOG.warn("MopubSquaredBanner banner could not be loaded", e);
                loadFallbackBanner(adUnitId);
                moPubView.destroy();
            }
        }
    }

    private void loadFallbackBanner(final String adUnitId) {
        InHouseBannerFactory.AdFormat adFormat;
        if (MoPubAdNetwork.UNIT_ID_AUDIO_PLAYER.equals(adUnitId) ||
                MoPubAdNetwork.UNIT_ID_PREVIEW_PLAYER_HORIZONTAL.equals(adUnitId)) {
            adFormat = InHouseBannerFactory.AdFormat.BIG_300x250;
        } else if (MoPubAdNetwork.UNIT_ID_SEARCH_HEADER.equals(adUnitId) ||
                MoPubAdNetwork.UNIT_ID_PREVIEW_PLAYER_VERTICAL.equals(adUnitId)) {
            adFormat = InHouseBannerFactory.AdFormat.SMALL_320x50;
        } else {
            throw new IllegalArgumentException("MopubBannerView.loadFallbackBanner() - invalid/unknown adUnitId <" + adUnitId  + ">");
        }
        InHouseBannerFactory.loadAd(fallbackBannerView, adFormat);
        setVisible(Visibility.FALLBACK, true);
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
            setControlsVisibility(View.VISIBLE);
            setBannerViewVisibility(moPubView, visible);
            setBannerViewVisibility(fallbackBannerView, !visible);
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
            LOG.info("onBannerLoaded()");
            setVisible(Visibility.MOPUB, true);
            PrebidManager.getInstance(getContext().getApplicationContext()).onBannerLoaded(getContext(), banner, PrebidManager.Placement.AUDIO_PLAYER_BANNER_300_250);
            isLoaded = true;
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
            LOG.info("onBannerFailed");
            loadFallbackBanner(banner.getAdUnitId());
            PrebidManager.getInstance(getContext().getApplicationContext()).onBannerFailed(getContext(), banner, PrebidManager.Placement.AUDIO_PLAYER_BANNER_300_250, errorCode);
            banner.destroy();
            isLoaded = false;
        }

        @Override
        public void onBannerClicked(MoPubView banner) {
            LOG.info("onBannerClicked: " + banner);
        }

        @Override
        public void onBannerExpanded(MoPubView banner) {
        }

        @Override
        public void onBannerCollapsed(MoPubView banner) {
            LOG.info("onBannerCollapsed");
            setVisible(Visibility.ALL, false);
            isLoaded = false;
        }
    };

    private final OnClickListener onDismissBannerOnClickListener = view -> {
        if (moPubView.getVisibility() == View.VISIBLE && showFallbackBannerOnDismiss) {
            setVisible(Visibility.FALLBACK, true);
        } else {
            setVisible(Visibility.ALL, false);
            if (onBannerDismissedListener != null) {
                try {
                    onBannerDismissedListener.dispatch();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    };

    private final OnClickListener removeAdsTextViewOnClickListener = view -> {
        setVisible(Visibility.ALL, false);
        PlayStore.getInstance().endAsync();
        Intent i = new Intent(getContext(), BuyActivity.class);
        if (getContext() instanceof Activity) {
            ((Activity) getContext()).startActivityForResult(i, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
        } else {
            throw new RuntimeException("MopubSquaredBanner.onClick(): Context is not an activity, what's up with that?");
        }
    };
}
