/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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
    private final MaxInterstitialAd interstitialAd;
    private final WeakReference<? extends Activity> activityRef;
    private final Application app;

    private boolean finishAfterDismiss = false;
    private boolean shutdownAfter = false;
    private boolean wasPlayingMusic = false;

    AppLovinMaxInterstitialAdapter(Activity parentActivity) {
        this.activityRef = Ref.weak(parentActivity);
        this.app = parentActivity.getApplication();
        interstitialAd = new MaxInterstitialAd(FWBannerView.UNIT_ID_INTERSTITIAL_MOBILE, activityRef.get());
        interstitialAd.setListener(this);
        try {
            interstitialAd.loadAd();
        } catch (Throwable ignored) {
        }
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
        try {
            interstitialAd.loadAd();
        } catch (Throwable ignored) {
        }
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
        SystemUtils.postToUIThreadDelayed(() -> {
            try {
                interstitialAd.loadAd();
            } catch (Throwable ignored) {
            }
        }, delayMillis);
    }

    @Override
    public void onAdDisplayFailed(MaxAd ad, MaxError error) {
        // Interstitial ad failed to display. AppLovin recommends that you load the next ad.
        try {
            interstitialAd.loadAd();
        } catch (Throwable ignored) {
        }
    }

    private void resumeMusicPlaybackIfNeeded() {
        if (wasPlayingMusic && !shutdownAfter && !MusicUtils.isPlaying()) {
            LOG.info("resumeMusicPlaybackIfNeeded(): wasPlaying and not shutting down, resuming player playback");
            MusicUtils.play();
        }
    }
}
