/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
import android.app.Application;

import com.andrew.apollo.utils.MusicUtils;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxInterstitialAd;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class AppLovinMaxInterstitialAdapter implements InterstitialListener, MaxAdListener {
    private static final Logger LOG = Logger.getLogger(AppLovinMaxInterstitialAdapter.class);
    private int retryAttempt;
    private MaxInterstitialAd interstitialAd;
    private final WeakReference<? extends Activity> activityRef;
    private final Application app;
    private final AppLovinAdNetwork appLovinAdNetwork;


    private boolean finishAfterDismiss = false;
    private boolean shutdownAfter = false;
    private boolean wasPlayingMusic = false;


    AppLovinMaxInterstitialAdapter(AppLovinAdNetwork appLovinAdNetwork, Activity parentActivity) {
        this.activityRef = Ref.weak(parentActivity);
        this.appLovinAdNetwork = appLovinAdNetwork;
        this.app = parentActivity.getApplication();
        createInterstitialAd();
    }

    @Override
    public boolean isAdReadyToDisplay() {
        return interstitialAd.isReady();
    }

    @Override
    public boolean isVideoAd() {
        return false;
    }

    @Override
    public boolean show(Activity activity, String placement) {
        if (interstitialAd.isReady()) {
            interstitialAd.showAd();
            return true;
        }
        return false;
    }

    @Override
    public void shutdownAppAfter(boolean shutdown) {
        shutdownAfter = shutdown;
    }

    @Override
    public void dismissActivityAfterwards(boolean dismiss) {
        finishAfterDismiss = dismiss;
    }

    @Override
    public void wasPlayingMusic(boolean wasPlayingMusic) {
        this.wasPlayingMusic = wasPlayingMusic;
    }

    @Override
    public void onAdLoaded(MaxAd ad) {
        retryAttempt = 0;
    }

    @Override
    public void onAdDisplayed(MaxAd ad) {
        resumeMusicPlaybackIfNeeded();
    }

    @Override
    public void onAdHidden(MaxAd ad) {
        Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(activityRef.get(), finishAfterDismiss, shutdownAfter, true, app);
        interstitialAd.loadAd();
        resumeMusicPlaybackIfNeeded();
    }

    @Override
    public void onAdClicked(MaxAd ad) {
        LOG.info("adClicked: interstitial clicked.", true);
    }

    @Override
    public void onAdLoadFailed(String adUnitId, MaxError error) {
        // Interstitial ad failed to load
        // AppLovin recommends that you retry with exponentially higher delays up to a maximum delay (in this case 64 seconds)
        retryAttempt++;
        long delayMillis = TimeUnit.SECONDS.toMillis((long) Math.pow(2, Math.min(6, retryAttempt)));
        SystemUtils.postToUIThread(() -> interstitialAd.loadAd(), delayMillis);
    }

    @Override
    public void onAdDisplayFailed(MaxAd ad, MaxError error) {
        // Interstitial ad failed to display. AppLovin recommends that you load the next ad.
        interstitialAd.loadAd();
    }

    private void createInterstitialAd() {
        if (!Ref.alive(activityRef)) {
            LOG.error("createInterstitialAd failed. Lost reference to activity. Not creating interstitial");
            return;
        }
        interstitialAd = new MaxInterstitialAd(FWBannerView.UNIT_ID_INTERSTITIAL_MOBILE, activityRef.get());
        interstitialAd.setListener(this);

        // Load the first ad
        interstitialAd.loadAd();
    }

    private void resumeMusicPlaybackIfNeeded() {
        if (wasPlayingMusic && !shutdownAfter && !MusicUtils.isPlaying()) {
            LOG.info("resumeMusicPlaybackIfNeeded(): wasPlaying and not shutting down, resuming player playback");
            MusicUtils.play();
        }
    }
}
