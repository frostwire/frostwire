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

public class MopubSquaredBanner extends LinearLayout {
    private static Logger LOG = Logger.getLogger(MopubSquaredBanner.class);
    private ImageButton dismissBannerButton;
    private MoPubView moPubView;
    private ImageView fallbackBannerView;
    private TextView mAdvertisementText;
    private TextView removeAdsTextView;
    private long lastInitAlbumArtBanner;
    private boolean isHidden;

    public MopubSquaredBanner(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater != null) {
            try {
                inflater.inflate(R.layout.view_mopub_squared_banner, this, true);
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

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        dismissBannerButton = findViewById(R.id.mopub_squared_banner_dismiss_mopubview_button);
        dismissBannerButton.setOnClickListener(onDismissBannerOnClickListener);
        moPubView = findViewById(R.id.mopub_squared_banner_mopubview);
        fallbackBannerView = findViewById(R.id.mopub_squared_banner_fallback_imageview);
        mAdvertisementText = findViewById(R.id.mopub_squared_banner_advertisement_text);
        mAdvertisementText.setOnClickListener(onDismissBannerOnClickListener);
        removeAdsTextView = findViewById(R.id.mopub_squared_banner_remove_ads_text_link);
        removeAdsTextView.setClickable(true);
        removeAdsTextView.setOnClickListener(removeAdsTextViewOnClickListener);
        loadFallbackBanner();
        loadMoPubBanner();
    }

    private void loadFallbackBanner() {
        LOG.info("loadFallbackBanner");
        InHouseBannerFactory.loadAd(fallbackBannerView, InHouseBannerFactory.AdFormat.BIG_300x250);
        setBannerViewVisibility(moPubView, false);
        setBannerViewVisibility(fallbackBannerView, true);
    }

    private void loadMoPubBanner() {
        long timeSinceLastBannerInit = System.currentTimeMillis() - lastInitAlbumArtBanner;

        if (timeSinceLastBannerInit < 5000) {
            LOG.info("initAlbumArtBanner() aborted, too soon to attempt another banner load");
            return;
        }

        if (Offers.disabledAds()) {
            return;
        }

        if ((moPubView != null && moPubView.getVisibility() == View.VISIBLE) ||
                (fallbackBannerView != null && fallbackBannerView.getVisibility() == View.VISIBLE)) {
            // ad is already visible, leave as is
            return;
        }

        lastInitAlbumArtBanner = System.currentTimeMillis();

        if (moPubView != null && dismissBannerButton != null) {
            loadFallbackBanner();

            if (!Offers.MOPUB.started()) {
                return;
            }

            moPubView.setTesting(false);
            moPubView.setAutorefreshEnabled(true);
            moPubView.setAdUnitId(MoPubAdNetwork.UNIT_ID_AUDIO_PLAYER);
            moPubView.setBannerAdListener(moPubBannerListener);

            try {
                moPubView.loadAd();
            } catch (Throwable e) {
                LOG.warn("AudioPlayer Mopub banner could not be loaded", e);
                loadFallbackBanner();
                moPubView.destroy();
            }
        }
    }

    private void setBannerViewVisibility(View bannerView, boolean visible) {
        if (bannerView != null && dismissBannerButton != null) {
            isHidden = !visible;
            int adVisibility = visible ? View.VISIBLE : View.GONE;
            bannerView.setVisibility(adVisibility);
            dismissBannerButton.setVisibility(adVisibility);
            mAdvertisementText.setVisibility(adVisibility);
            if (removeAdsTextView != null) {
                removeAdsTextView.setVisibility(Offers.removeAdsOffersEnabled() && visible ?
                        View.VISIBLE : View.GONE);
            }
        }
    }

    public boolean isHidden() {
        return isHidden;
    }

    private final MoPubView.BannerAdListener moPubBannerListener = new MoPubView.BannerAdListener() {
        @Override
        public void onBannerLoaded(MoPubView banner) {
            LOG.info("onBannerLoaded()");
            setBannerViewVisibility(fallbackBannerView, false);
            setBannerViewVisibility(moPubView, true);
            PrebidManager.getInstance(getContext().getApplicationContext()).onBannerLoaded(getContext(), banner, PrebidManager.Placement.AUDIO_PLAYER_BANNER_300_250);
            isHidden = false;
        }

        @Override
        public void onBannerFailed(MoPubView banner, MoPubErrorCode errorCode) {
            LOG.info("onBannerFailed");
            loadFallbackBanner();
            PrebidManager.getInstance(getContext().getApplicationContext()).onBannerFailed(getContext(), banner, PrebidManager.Placement.AUDIO_PLAYER_BANNER_300_250, errorCode);
            banner.destroy();
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
            setBannerViewVisibility(moPubView, false);
        }
    };

    private final OnClickListener onDismissBannerOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            setBannerViewVisibility(moPubView, false);
            setBannerViewVisibility(fallbackBannerView, false);
            isHidden = true;
        }
    };

    private final OnClickListener removeAdsTextViewOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            setBannerViewVisibility(moPubView, false);
            setBannerViewVisibility(fallbackBannerView, false);
            PlayStore.getInstance().endAsync();
            Intent i = new Intent(getContext(), BuyActivity.class);
            if (getContext() instanceof Activity) {
                ((Activity) getContext()).startActivityForResult(i, BuyActivity.PURCHASE_SUCCESSFUL_RESULT_CODE);
            } else {
                throw new RuntimeException("MopubSquaredBanner.onClick(): Context is not an activity, what's up with that?");
            }
        }
    };
}
