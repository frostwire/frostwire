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

import static com.frostwire.android.offers.Offers.DEBUG_MODE;
import static com.frostwire.android.util.Asyncs.async;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.andrew.apollo.utils.MusicUtils;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkSettings;
import com.frostwire.android.core.Constants;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class AppLovinAdNetwork extends AbstractAdNetwork {

    private static final Logger LOG = Logger.getLogger(AppLovinAdNetwork.class);
    private static AppLovinAdNetwork APP_LOVIN_ADNETWORK = null;
    private AppLovinMaxInterstitialAdapter interstitialAdapter = null;

    private AppLovinAdNetwork() {
    }

    @Override
    public void initialize(final Activity activity) {
        if (shouldWeAbortInitialize(activity)) {
            return;
        }
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            try {
                if (!started()) {
                    Context appContext = SystemUtils.getApplicationContext();
                    AppLovinSdk.initializeSdk(appContext);
                    AppLovinSdk sdk = AppLovinSdk.getInstance(appContext);
                    sdk.setMediationProvider(AppLovinMediationProvider.MAX);
                    AppLovinSdkSettings sdkSettings = sdk.getSettings();
                    sdkSettings.setMuted(!DEBUG_MODE);
                    sdkSettings.setVerboseLogging(DEBUG_MODE);
                    start();
                    LOG.info("AppLovin initialized. AppLovinAdNetwork.DEBUG_MODE=" + DEBUG_MODE);
                }
            } catch (Throwable e) {
                LOG.error(e.getMessage(), e);
            }
        });
    }

    @Override
    public void loadNewInterstitial(Activity activity) {
        interstitialAdapter = new AppLovinMaxInterstitialAdapter(activity);
    }

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_APPLOVIN;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_APPLOVIN;
    }

    @Override
    public boolean isDebugOn() {
        return DEBUG_MODE;
    }


    @Override
    public boolean showInterstitial(Activity activity,
                                    String placement,
                                    final boolean shutdownAfterwards,
                                    final boolean dismissAfterward) {
        boolean result = false;
        if (enabled() && started()) {
            // make sure video ads are always muted, it's very annoying (regardless of playback status)
            AppLovinSdk.getInstance(activity).getSettings().setMuted(true);
            if (interstitialAdapter == null) {
                loadNewInterstitial(activity);
            }
            interstitialAdapter.shutdownAppAfter(shutdownAfterwards);
            interstitialAdapter.dismissActivityAfterwards(dismissAfterward);
            try {
                interstitialAdapter.wasPlayingMusic(MusicUtils.isPlaying());
                result = interstitialAdapter.isAdReadyToDisplay() &&
                        // do not show applovin interstitials on exit
                        (!shutdownAfterwards || !interstitialAdapter.isVideoAd()) &&
                        interstitialAdapter.show(activity, placement);
            } catch (Throwable e) {
                e.printStackTrace();
                result = false;
            }
        }
        return result;
    }

    public static AppLovinAdNetwork getInstance() {
        if (APP_LOVIN_ADNETWORK == null) {
            APP_LOVIN_ADNETWORK = new AppLovinAdNetwork();
        }
        return APP_LOVIN_ADNETWORK;
    }

    public MaxRewardedAd loadRewardedVideo(WeakReference<AppCompatActivity> activityRef) {
        if (Ref.alive(activityRef)) {
            try {
                MaxRewardedAd rewardedAd = MaxRewardedAd.getInstance(FWBannerView.UNIT_ID_REWARDED_AD, activityRef.get());
                rewardedAd.setListener(new RewardedAdListener(rewardedAd));
                rewardedAd.loadAd();
                return rewardedAd;
            } catch (Throwable e) {
                LOG.error(e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    private static class RewardedAdListener implements MaxRewardedAdListener {

        private static final Logger LOG = Logger.getLogger(RewardedAdListener.class);
        private int retryAttempt;
        private boolean wasPlayingMusic;
        private final MaxRewardedAd rewardedAd;

        public RewardedAdListener(MaxRewardedAd rewardedAd) {
            this.rewardedAd = rewardedAd;
        }

        @Override
        public void onRewardedVideoStarted(MaxAd ad) {
            LOG.info("onRewardedVideoStarted() started reward Ad playback");
            wasPlayingMusic = MusicUtils.isPlaying();

            if (wasPlayingMusic) {
                MusicUtils.pause();
            }
        }

        @Override
        public void onRewardedVideoCompleted(MaxAd ad) {
            LOG.info("onRewardedVideoCompleted: adUnitId=" + ad.getAdUnitId());
            async(Offers::pauseAdsAsync, Constants.MIN_REWARD_AD_FREE_MINUTES);
            if (wasPlayingMusic) {
                MusicUtils.play();
            }
        }

        @Override
        public void onUserRewarded(MaxAd ad, MaxReward reward) {
            async(Offers::pauseAdsAsync, Constants.MIN_REWARD_AD_FREE_MINUTES);
        }

        @Override
        public void onAdLoaded(MaxAd ad) {
            retryAttempt = 0;
        }

        @Override
        public void onAdDisplayed(MaxAd ad) {

        }

        @Override
        public void onAdHidden(MaxAd ad) {
            // rewarded ad is hidden. Pre-load the next ad
            rewardedAd.loadAd();
        }

        @Override
        public void onAdClicked(MaxAd ad) {
            if (wasPlayingMusic) {
                MusicUtils.play();
            }
        }

        @Override
        public void onAdLoadFailed(String adUnitId, MaxError error) {
            // Rewarded ad failed to load
            // We recommend retrying with exponentially higher delays up to a maximum delay (in this case 64 seconds)
            retryAttempt++;
            long delayMillis = TimeUnit.SECONDS.toMillis((long) Math.pow(2, Math.min(6, retryAttempt)));
            new Handler().postDelayed(rewardedAd::loadAd, delayMillis);
        }

        @Override
        public void onAdDisplayFailed(MaxAd ad, MaxError error) {
            // Rewarded ad failed to display. We recommend loading the next ad
            rewardedAd.loadAd();
            if (wasPlayingMusic) {
                MusicUtils.play();
            }
        }
    }
}
