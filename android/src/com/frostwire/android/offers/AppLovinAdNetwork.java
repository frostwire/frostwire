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

import static com.frostwire.android.offers.Offers.DEBUG_MODE;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.andrew.apollo.utils.MusicUtils;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.ads.MaxRewardedAd;
import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.applovin.sdk.AppLovinSdkSettings;
import com.applovin.sdk.AppLovinTermsAndPrivacyPolicyFlowSettings;
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
                    final String APPLOVIN_SDK_KEY = "PDAf5nX3UvzDFSGe52hI1kez_GTHC4GcIQGOCpRghOuyr9axCGDD_sB-6kFJpWw5yBU8_wphJhd8rD32UHGT8R";
                    AppLovinSdkInitializationConfiguration config = AppLovinSdkInitializationConfiguration.builder(APPLOVIN_SDK_KEY, appContext)
                            .setMediationProvider(AppLovinMediationProvider.MAX)
                            .build();
                    AppLovinSdk sdk = AppLovinSdk.getInstance(appContext);
                    AppLovinSdkSettings sdkSettings = sdk.getSettings();
                    AppLovinTermsAndPrivacyPolicyFlowSettings termsAndPrivacyPolicyFlowSettings = sdkSettings.getTermsAndPrivacyPolicyFlowSettings();
                    termsAndPrivacyPolicyFlowSettings.setEnabled(true);
                    termsAndPrivacyPolicyFlowSettings.setPrivacyPolicyUri(Uri.parse("https://www.frostwire.com/privacy"));
                    termsAndPrivacyPolicyFlowSettings.setTermsOfServiceUri(Uri.parse("https://www.frostwire.com/terms"));
                    sdkSettings.setMuted(!DEBUG_MODE);
                    sdkSettings.setVerboseLogging(DEBUG_MODE);


                    sdk.initialize(config, appLovinSdkConfiguration -> {
                        LOG.info("AppLovin initialized. AppLovinAdNetwork.DEBUG_MODE=" + DEBUG_MODE);
                        start();
                    });
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
                LOG.error("AppLovinAdNetwork.showInterstitial() failed", e);
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
        public void onUserRewarded(MaxAd ad, @NonNull MaxReward reward) {
            LOG.info("onUserRewarded: adUnitId=" + ad.getAdUnitId());
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> Offers.pauseAdsAsync(Constants.MIN_REWARD_AD_FREE_MINUTES));
            if (wasPlayingMusic) {
                MusicUtils.play();
            }
        }

        @Override
        public void onAdLoaded(@NonNull MaxAd ad) {
            retryAttempt = 0;
        }

        @Override
        public void onAdDisplayed(@NonNull MaxAd ad) {
            LOG.info("onRewardedVideoStarted() started reward Ad playback");
            wasPlayingMusic = MusicUtils.isPlaying();

            if (wasPlayingMusic) {
                MusicUtils.pause();
            }
        }

        @Override
        public void onAdHidden(@NonNull MaxAd ad) {
            // rewarded ad is hidden. Pre-load the next ad
            rewardedAd.loadAd();
        }

        @Override
        public void onAdClicked(@NonNull MaxAd ad) {
            if (wasPlayingMusic) {
                MusicUtils.play();
            }
        }

        @Override
        public void onAdLoadFailed(@NonNull String adUnitId, @NonNull MaxError error) {
            // Rewarded ad failed to load
            // We recommend retrying with exponentially higher delays up to a maximum delay (in this case 64 seconds)
            retryAttempt++;
            long delayMillis = TimeUnit.SECONDS.toMillis((long) Math.pow(2, Math.min(6, retryAttempt)));
            new Handler().postDelayed(rewardedAd::loadAd, delayMillis);
        }

        @Override
        public void onAdDisplayFailed(@NonNull MaxAd ad, @NonNull MaxError error) {
            // Rewarded ad failed to display. We recommend loading the next ad
            rewardedAd.loadAd();
            if (wasPlayingMusic) {
                MusicUtils.play();
            }
        }
    }
}
