/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.applovin.adview.AppLovinIncentivizedInterstitial;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdClickListener;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdRewardListener;
import com.applovin.sdk.AppLovinAdVideoPlaybackListener;
import com.applovin.sdk.AppLovinErrorCodes;
import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinPrivacySettings;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkSettings;
import com.mopub.common.DataKeys;
import com.mopub.common.LifecycleListener;
import com.mopub.common.MoPub;
import com.mopub.common.MoPubReward;
import com.mopub.common.logging.MoPubLog;

import java.util.HashMap;
import java.util.Map;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM_WITH_THROWABLE;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOULD_REWARD;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;

// MoPub Adapter
public class AppLovinRewardedVideo extends CustomEventRewardedVideo implements AppLovinAdLoadListener, AppLovinAdDisplayListener, AppLovinAdClickListener, AppLovinAdVideoPlaybackListener, AppLovinAdRewardListener {

    private static final String DEFAULT_ZONE = "e7efde5b87ec4bf6";//"Int_Rewarded";
    private static final String DEFAULT_TOKEN_ZONE = "e7efde5b87ec4bf6"; //"token";
    private static final String ZONE_ID_SERVER_EXTRAS_KEY = "zone_id";
    private static final String ADAPTER_NAME = AppLovinRewardedVideo.class.getSimpleName();

    // A map of Zone -> `AppLovinIncentivizedInterstitial` to be shared by instances of the custom event.
    // This prevents skipping of ads as this adapter will be re-created and preloaded (along with underlying `AppLovinIncentivizedInterstitial`)
    // on every ad load regardless if ad was actually displayed or not.
    private static final Map<String, AppLovinIncentivizedInterstitial> GLOBAL_INCENTIVIZED_INTERSTITIAL_ADS = new HashMap<String, AppLovinIncentivizedInterstitial>();

    private boolean initialized;

    private AppLovinSdk sdk;
    private AppLovinIncentivizedInterstitial incentivizedInterstitial;
    private Activity parentActivity;

    private boolean fullyWatched;
    private MoPubReward reward;

    private boolean isTokenEvent;
    private AppLovinAd tokenAd;
    private String serverExtrasZoneId = DEFAULT_ZONE;

    @NonNull
    private AppLovinAdapterConfiguration mAppLovinAdapterConfiguration;

    //
    // MoPub Custom Event Methods
    //

    public AppLovinRewardedVideo() {
        mAppLovinAdapterConfiguration = new AppLovinAdapterConfiguration();
    }

    @Override
    protected boolean checkAndInitializeSdk(@NonNull final Activity activity, @NonNull final Map<String, Object> localExtras, @NonNull final Map<String, String> serverExtras) throws Exception {

        // Pass the user consent from the MoPub SDK to AppLovin as per GDPR
        boolean canCollectPersonalInfo = MoPub.canCollectPersonalInformation();
        AppLovinPrivacySettings.setHasUserConsent(canCollectPersonalInfo, activity.getApplicationContext());

        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Initializing AppLovin rewarded video...");

        if (!initialized) {
            sdk = retrieveSdk(activity);

            if (sdk == null) {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "AppLovinSdk instance is null likely because " +
                        "no AppLovin SDK key is available. Failing ad request.");
                MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                        MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR.getIntCode(),
                        MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);

                MoPubRewardedVideoManager.onRewardedVideoLoadFailure(AppLovinRewardedVideo.this.
                        getClass(), getAdNetworkId(), MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);

                return false;
            }

            sdk.setMediationProvider(AppLovinMediationProvider.MOPUB);
            sdk.setPluginVersion(AppLovinAdapterConfiguration.APPLOVIN_PLUGIN_VERSION);

            initialized = true;

            return true;
        }
        return false;
    }

    @Override
    protected void loadWithSdkInitialized(@NonNull final Activity activity, @NonNull final Map<String, Object> localExtras, @NonNull final Map<String, String> serverExtras) throws Exception {

        parentActivity = activity;

        final String adMarkup = serverExtras.get(DataKeys.ADM_KEY);
        final boolean hasAdMarkup = !TextUtils.isEmpty(adMarkup);

        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Requesting AppLovin rewarded video with serverExtras: " + serverExtras
                + ", localExtras: " + localExtras + " and has ad markup: " + hasAdMarkup);

        mAppLovinAdapterConfiguration.setCachedInitializationParameters(activity, serverExtras);

        // Determine zone
        final String zoneId;
        if (hasAdMarkup) {
            zoneId = DEFAULT_TOKEN_ZONE;
        } else {
            serverExtrasZoneId = serverExtras.get(ZONE_ID_SERVER_EXTRAS_KEY);
            if (!TextUtils.isEmpty(serverExtrasZoneId)) {
                zoneId = serverExtrasZoneId;
            } else {
                zoneId = DEFAULT_ZONE;
            }
        }

        // Create incentivized ad based off of zone
        incentivizedInterstitial = createIncentivizedInterstitialAd(zoneId, sdk);

        // Use token API
        if (hasAdMarkup) {
            isTokenEvent = true;

            sdk.getAdService().loadNextAdForAdToken(adMarkup, this);
            MoPubLog.log(getAdNetworkId(), LOAD_ATTEMPTED, ADAPTER_NAME);
        }
        // Zone/regular ad load
        else {
            incentivizedInterstitial.preload(this);
            MoPubLog.log(getAdNetworkId(), LOAD_ATTEMPTED, ADAPTER_NAME);
        }
    }

    @Override
    protected void showVideo() {
        MoPubLog.log(SHOW_ATTEMPTED, ADAPTER_NAME);

        if (hasVideoAvailable()) {
            fullyWatched = false;
            reward = null;

            if (isTokenEvent) {
                incentivizedInterstitial.show(tokenAd, parentActivity, this, this, this, this);
            } else {
                incentivizedInterstitial.show(parentActivity, null, this, this, this, this);
            }
        } else {
            MoPubLog.log(SHOW_FAILED,
                    ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(),
                    MoPubErrorCode.NETWORK_NO_FILL);

            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Failed to show an AppLovin rewarded video before one was loaded");
            MoPubRewardedVideoManager.onRewardedVideoPlaybackError(getClass(), getAdNetworkId(), MoPubErrorCode.VIDEO_PLAYBACK_ERROR);
        }
    }

    @Override
    protected boolean hasVideoAvailable() {
        if (isTokenEvent) {
            return tokenAd != null;
        } else {
            return (incentivizedInterstitial != null && incentivizedInterstitial.isAdReadyToDisplay());
        }
    }

    @Override
    @Nullable
    protected LifecycleListener getLifecycleListener() {
        return null;
    }

    @Override
    @NonNull
    protected String getAdNetworkId() {
        return serverExtrasZoneId;
    }

    @Override
    protected void onInvalidate() {
    }

    //
    // Ad Load Listener
    //

    @Override
    public void adReceived(final AppLovinAd ad) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Rewarded video did load ad: " + ad.getAdIdNumber());

        if (isTokenEvent) {
            tokenAd = ad;
        }

        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    MoPubRewardedVideoManager.onRewardedVideoLoadSuccess(AppLovinRewardedVideo.this.getClass(), getAdNetworkId());

                    MoPubLog.log(LOAD_SUCCESS, ADAPTER_NAME);
                } catch (Throwable th) {
                    MoPubLog.log(CUSTOM_WITH_THROWABLE, "Unable to notify listener of " +
                            "successful ad load.", th);
                }
            }
        });
    }

    @Override
    public void failedToReceiveAd(final int errorCode) {

        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    MoPubRewardedVideoManager.onRewardedVideoLoadFailure(AppLovinRewardedVideo.this.
                            getClass(), getAdNetworkId(), toMoPubErrorCode(errorCode));

                    MoPubLog.log(LOAD_FAILED, ADAPTER_NAME,
                            toMoPubErrorCode(errorCode).getIntCode(),
                            toMoPubErrorCode(errorCode));
                } catch (Throwable th) {
                    MoPubLog.log(CUSTOM_WITH_THROWABLE, "Unable to notify listener of failure" +
                            " to receive ad.", th);
                }
            }
        });
    }

    //
    // Ad Display Listener
    //

    @Override
    public void adDisplayed(final AppLovinAd ad) {
        MoPubRewardedVideoManager.onRewardedVideoStarted(getClass(), getAdNetworkId());

        MoPubLog.log(SHOW_SUCCESS, ADAPTER_NAME);
    }

    @Override
    public void adHidden(final AppLovinAd ad) {

        if (fullyWatched && reward != null) {
            MoPubLog.log(CUSTOM, ADAPTER_NAME, "Rewarded: " + reward.getAmount() + " " + reward.getLabel());
            MoPubLog.log(SHOULD_REWARD, ADAPTER_NAME, reward.getAmount(), reward.getLabel());

            MoPubRewardedVideoManager.onRewardedVideoCompleted(getClass(), getAdNetworkId(), reward);
        }

        MoPubRewardedVideoManager.onRewardedVideoClosed(getClass(), getAdNetworkId());
    }

    //
    // Ad Click Listener
    //

    @Override
    public void adClicked(final AppLovinAd ad) {
        MoPubRewardedVideoManager.onRewardedVideoClicked(getClass(), getAdNetworkId());

        MoPubLog.log(CLICKED, ADAPTER_NAME);
    }

    //
    // Video Playback Listener
    //

    @Override
    public void videoPlaybackBegan(final AppLovinAd ad) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Rewarded video playback began");
    }

    @Override
    public void videoPlaybackEnded(final AppLovinAd ad, final double percentViewed, final boolean fullyWatched) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Rewarded video playback ended at playback percent: " + percentViewed);

        this.fullyWatched = fullyWatched;
    }

    //
    // Reward Listener
    //

    @Override
    public void userOverQuota(final AppLovinAd appLovinAd, final Map map) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Rewarded video validation request for ad did exceed quota with " +
                "response: " + map);
    }

    @Override
    public void validationRequestFailed(final AppLovinAd appLovinAd, final int errorCode) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Rewarded video validation request for ad failed with error " +
                "code: " + errorCode);
    }

    @Override
    public void userRewardRejected(final AppLovinAd appLovinAd, final Map map) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Rewarded video validation request was rejected with response: "
                + map);
    }

    @Override
    public void userDeclinedToViewAd(final AppLovinAd appLovinAd) {
        MoPubLog.log(CUSTOM, ADAPTER_NAME, "User declined to view rewarded video");
        MoPubRewardedVideoManager.onRewardedVideoClosed(getClass(), getAdNetworkId());
    }

    @Override
    public void userRewardVerified(final AppLovinAd appLovinAd, final Map map) {
        final String currency = (String) map.get("currency");
        final int amount = (int) Double.parseDouble((String) map.get("amount")); // AppLovin returns amount as double

        MoPubLog.log(CUSTOM, ADAPTER_NAME, "Verified " + amount + " " + currency);

        reward = MoPubReward.success(currency, amount);
    }

    //
    // Utility Methods
    //

    private static MoPubErrorCode toMoPubErrorCode(final int applovinErrorCode) {
        if (applovinErrorCode == AppLovinErrorCodes.NO_FILL) {
            return MoPubErrorCode.NETWORK_NO_FILL;
        } else if (applovinErrorCode == AppLovinErrorCodes.UNSPECIFIED_ERROR) {
            return MoPubErrorCode.UNSPECIFIED;
        } else if (applovinErrorCode == AppLovinErrorCodes.NO_NETWORK) {
            return MoPubErrorCode.NO_CONNECTION;
        } else if (applovinErrorCode == AppLovinErrorCodes.FETCH_AD_TIMEOUT) {
            return MoPubErrorCode.NETWORK_TIMEOUT;
        } else {
            return MoPubErrorCode.UNSPECIFIED;
        }
    }

    /**
     * Retrieves the appropriate instance of AppLovin's SDK from the SDK key. This check prioritizes
     * the SDK Key in the AndroidManifest, and only uses the one passed in to the AdapterConfiguration
     * if the former is not available.
     */
    private static AppLovinSdk retrieveSdk(final Context context) {

        if (!AppLovinAdapterConfiguration.androidManifestContainsValidSdkKey(context)) {
            final String sdkKey = AppLovinAdapterConfiguration.getSdkKey();

            return !TextUtils.isEmpty(sdkKey)
                    ? AppLovinSdk.getInstance(sdkKey, new AppLovinSdkSettings(), context)
                    : null;
        } else {
            return AppLovinSdk.getInstance(context);
        }
    }

    private static AppLovinIncentivizedInterstitial createIncentivizedInterstitialAd(final String zoneId, final AppLovinSdk sdk) {
        final AppLovinIncentivizedInterstitial incent;

        // Check if incentivized ad for zone already exists
        if (GLOBAL_INCENTIVIZED_INTERSTITIAL_ADS.containsKey(zoneId)) {
            incent = GLOBAL_INCENTIVIZED_INTERSTITIAL_ADS.get(zoneId);
        } else {
            // If this is a default or token Zone, create the incentivized ad normally
            if (DEFAULT_ZONE.equals(zoneId) || DEFAULT_TOKEN_ZONE.equals(zoneId)) {
                incent = AppLovinIncentivizedInterstitial.create(sdk);
            }
            // Otherwise, use the Zones API
            else {
                incent = AppLovinIncentivizedInterstitial.create(zoneId, sdk);
            }

            GLOBAL_INCENTIVIZED_INTERSTITIAL_ADS.put(zoneId, incent);
        }

        return incent;
    }
}
