/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android;
// I wanted this to be in com.frostwire.android.offers.AppLovinCustomEventBanner but it wasn't working.
// instructions were very specific to have this be <PACKAGE_NAME>.AppLovinCustomEventBanner
// therefore the file was renamed and moved here to be com.frostwire.android.AppLovinCustomEventBanner

import android.app.Activity;
import android.content.Context;

import com.applovin.adview.AppLovinAdView;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinErrorCodes;
import com.applovin.sdk.AppLovinSdk;
import com.frostwire.util.Logger;
import com.mopub.mobileads.CustomEventBanner;
import com.mopub.mobileads.MoPubErrorCode;

import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * This is the MoPub Custom Banner adapter for AppLovin.
 * We have the interstitial adapter for MoPub on the standard location com.mopub.mobileads.AppLovinInterstitialAdapter
 *
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 9/27/17.
 */


public class AppLovinCustomEventBanner extends CustomEventBanner {
    private static final Logger LOG = Logger.getLogger(AppLovinCustomEventBanner.class);
    private static final String AD_WIDTH_KEY = "com_mopub_ad_width";
    private static final String AD_HEIGHT_KEY = "com_mopub_ad_height";
    //
    // MoPub Custom Event Methods
    //

    @Override
    protected void loadBanner(final Context context, final CustomEventBannerListener customEventBannerListener, final Map<String, Object> localExtras, final Map<String, String> serverExtras) {
        // SDK versions BELOW 7.1.0 require a instance of an Activity to be passed in as the context
        if (AppLovinSdk.VERSION_CODE < 710 && !(context instanceof Activity)) {
            LOG.error("Unable to request AppLovin banner. Invalid context provided.");
            customEventBannerListener.onBannerFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            return;
        }
        LOG.debug("Requesting AppLovin banner with localExtras: " + localExtras);
        final AppLovinAdSize adSize = appLovinAdSizeFromServerExtras(localExtras);
        if (adSize != null) {
            final AppLovinSdk sdk = AppLovinSdk.getInstance(context);
            sdk.setPluginVersion("MoPub-2.0");
            final AppLovinAdView adView = createAdView(adSize, context, customEventBannerListener);
            adView.setAdLoadListener(new AppLovinAdLoadListener() {
                @Override
                public void adReceived(final AppLovinAd ad) {
                    LOG.debug("Successfully loaded banner ad");
                    customEventBannerListener.onBannerLoaded(adView);
                }

                @Override
                public void failedToReceiveAd(final int errorCode) {
                    LOG.error("Failed to load banner ad with code: " + errorCode);
                    customEventBannerListener.onBannerFailed(toMoPubErrorCode(errorCode));
                }
            });
            adView.setAdDisplayListener(new AppLovinAdDisplayListener() {
                @Override
                public void adDisplayed(final AppLovinAd ad) {
                    LOG.debug("Banner displayed");
                }

                @Override
                public void adHidden(final AppLovinAd ad) {
                    LOG.debug("Banner dismissed");
                }
            });
            adView.setAdClickListener(ad -> {
                LOG.debug("Banner clicked");
                customEventBannerListener.onBannerClicked();
                customEventBannerListener.onLeaveApplication();
            });
            adView.loadNextAd();
        } else {
            LOG.error("Unable to request AppLovin banner");
            customEventBannerListener.onBannerFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
        }
    }

    @Override
    protected void onInvalidate() {
    }
    //
    // Utility Methods
    //

    private AppLovinAdSize appLovinAdSizeFromServerExtras(final Map<String, Object> serverExtras) {
        // Handle trivial case
        if (serverExtras == null || serverExtras.isEmpty()) {
            LOG.error("No serverExtras provided");
            return null;
        }
        try {
            final int width = (Integer) serverExtras.get(AD_WIDTH_KEY);
            final int height = (Integer) serverExtras.get(AD_HEIGHT_KEY);
            // We have valid dimensions
            if (width > 0 && height > 0) {
                LOG.debug("Valid width (" + width + ") and height (" + height + ") provided");
                // Use the smallest AppLovinAdSize that will properly contain the adView
                if (height <= AppLovinAdSize.BANNER.getHeight()) {
                    return AppLovinAdSize.BANNER;
                } else if (height <= AppLovinAdSize.MREC.getHeight()) {
                    return AppLovinAdSize.MREC;
                } else {
                    LOG.error("Provided dimensions does not meet the dimensions required of banner or mrec ads");
                }
            } else {
                LOG.error("Invalid width (" + width + ") and height (" + height + ") provided");
            }
        } catch (Throwable th) {
            LOG.error("Encountered error while parsing width and height from serverExtras", th);
        }
        return null;
    }
    //
    // Utility Methods
    //

    private AppLovinAdView createAdView(final AppLovinAdSize size, final Context parentContext, final CustomEventBannerListener customEventBannerListener) {
        AppLovinAdView adView = null;
        try {
            // AppLovin SDK < 7.1.0 uses an Activity, as opposed to Context in >= 7.1.0
            final Class<?> contextClass = (AppLovinSdk.VERSION_CODE < 710) ? Activity.class : Context.class;
            final Constructor<?> constructor = AppLovinAdView.class.getConstructor(AppLovinAdSize.class, contextClass);
            adView = (AppLovinAdView) constructor.newInstance(size, parentContext);
        } catch (Throwable th) {
            LOG.error("Unable to get create AppLovinAdView.", th);
            customEventBannerListener.onBannerFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
        }
        return adView;
    }

    private static MoPubErrorCode toMoPubErrorCode(final int applovinErrorCode) {
        if (applovinErrorCode == AppLovinErrorCodes.NO_FILL) {
            return MoPubErrorCode.NETWORK_NO_FILL;
        } else if (applovinErrorCode == AppLovinErrorCodes.UNSPECIFIED_ERROR) {
            return MoPubErrorCode.NETWORK_INVALID_STATE;
        } else if (applovinErrorCode == AppLovinErrorCodes.NO_NETWORK) {
            return MoPubErrorCode.NO_CONNECTION;
        } else if (applovinErrorCode == AppLovinErrorCodes.FETCH_AD_TIMEOUT) {
            return MoPubErrorCode.NETWORK_TIMEOUT;
        } else {
            return MoPubErrorCode.UNSPECIFIED;
        }
    }
}
