/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android;
// I wanted this to be in com.frostwire.android.offers.AppLovinCustomEventBanner but it wasn't working.
// instructions were very specific to have this be <PACKAGE_NAME>.AppLovinCustomEventBanner
// therefore the file was renamed and moved here to be com.frostwire.android.AppLovinCustomEventBanner

import android.app.Activity;
import android.content.Context;

import com.applovin.adview.AppLovinAdView;
import com.applovin.adview.AppLovinAdViewEventListener;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinErrorCodes;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkSettings;
import com.applovin.sdk.AppLovinSdkUtils;
import com.frostwire.util.Logger;
import com.mopub.mobileads.CustomEventBanner;
import com.mopub.mobileads.MoPubErrorCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * This is the MoPub Custom Banner adapter for AppLovin.
 * We have the interstitial adapter for MoPub on the standard location com.mopub.mobileads.AppLovinInterstitialAdapter
 *
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 9/27/17.
 */


public class AppLovinCustomEventBanner extends CustomEventBanner {
    private static final Logger LOG = Logger.getLogger(AppLovinCustomEventBanner.class);

    private static final int BANNER_STANDARD_HEIGHT         = 50;
    private static final int BANNER_HEIGHT_OFFSET_TOLERANCE = 10;
    private static final int LEADER_STANDARD_HEIGHT         = 90;
    private static final int LEADER_HEIGHT_OFFSET_TOLERANCE = 16;

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
            final AppLovinSdk sdk = retrieveSdk(serverExtras, context);
            sdk.setPluginVersion("MoPub-2.1.3");
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
            if (AppLovinSdk.VERSION_CODE >= 730) {
                adView.setAdViewEventListener((AppLovinAdViewEventListener) AppLovinAdViewEventListenerProxy.newInstance(customEventBannerListener));
            }
            final AppLovinAdLoadListener adLoadListener = new AppLovinAdLoadListener() {
                @Override
                public void adReceived(final AppLovinAd ad) {
                    // Ensure logic is ran on main queue
                    AppLovinSdkUtils.runOnUiThread(() -> {
                        adView.renderAd(ad);
                        LOG.debug("Successfully loaded banner ad");
                        customEventBannerListener.onBannerLoaded(adView);
                    });
                }

                @Override
                public void failedToReceiveAd(final int errorCode) {
                    // Ensure logic is ran on main queue
                    AppLovinSdkUtils.runOnUiThread(() -> {
                        LOG.debug("Failed to load banner ad with code: " + errorCode);
                        customEventBannerListener.onBannerFailed(toMoPubErrorCode(errorCode));
                    });
                }
            };
            // Zones support is available on AppLovin SDK 7.5.0 and higher
            final String zoneId;
            if (AppLovinSdk.VERSION_CODE >= 750 && serverExtras != null && serverExtras.containsKey("zone_id")) {
                zoneId = serverExtras.get("zone_id");
            } else {
                zoneId = null;
            }
            if (zoneId != null && zoneId.length() > 0) {
                loadNextAd(sdk, zoneId, adLoadListener, customEventBannerListener);
            } else {
                sdk.getAdService().loadNextAd(adSize, adLoadListener);
            }
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
                // Assume fluid width, and check for height with offset tolerance
                final int bannerOffset = Math.abs( BANNER_STANDARD_HEIGHT - height );
                final int leaderOffset = Math.abs( LEADER_STANDARD_HEIGHT - height );
                if (bannerOffset <= BANNER_HEIGHT_OFFSET_TOLERANCE) {
                    return AppLovinAdSize.BANNER;
                } else if (leaderOffset <= LEADER_HEIGHT_OFFSET_TOLERANCE) {
                    return AppLovinAdSize.LEADER;
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

    private void loadNextAd(final AppLovinSdk sdk, final String zoneId, final AppLovinAdLoadListener adLoadListener, final CustomEventBannerListener customEventBannerListener) {
        // Dynamically load an ad for a given zone without breaking backwards compatibility for publishers on older SDKs
        try {
            final Method method = sdk.getAdService().getClass().getMethod("loadNextAdForZoneId", String.class, AppLovinAdLoadListener.class);
            method.invoke(sdk.getAdService(), zoneId, adLoadListener);
        } catch (Throwable th) {
            LOG.error("Unable to load ad for zone: " + zoneId + "...");
            customEventBannerListener.onBannerFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
        }
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

    /**
     * Dynamic proxy class for AppLovin's AppLovinAdViewEventListener. Used to keep compilation compatibility if publisher is on a version of the SDK before the listener was introduced (< 7.3.0).
     */
    private static final class AppLovinAdViewEventListenerProxy
            implements InvocationHandler {
        private final CustomEventBannerListener customEventBannerListener;

        private static Object newInstance(final CustomEventBannerListener customEventBannerListener) {
            return Proxy.newProxyInstance(AppLovinAdViewEventListener.class.getClassLoader(),
                    new Class[]{AppLovinAdViewEventListener.class},
                    new AppLovinAdViewEventListenerProxy(customEventBannerListener));
        }

        private AppLovinAdViewEventListenerProxy(final CustomEventBannerListener customEventBannerListener) {
            this.customEventBannerListener = customEventBannerListener;
        }

        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            final String methodName = method.getName();
            if ("adOpenedFullscreen".equals(methodName)) {
                LOG.debug("Banner opened fullscreen");
                customEventBannerListener.onBannerExpanded();
            } else if ("adClosedFullscreen".equals(methodName)) {
                LOG.debug("Banner closed fullscreen");
                customEventBannerListener.onBannerCollapsed();
            } else if ("adLeftApplication".equals(methodName)) {
                // We will fire onLeaveApplication() in the adClicked() callback
                LOG.debug("Banner left application");
            } else if ("adFailedToDisplay".equals(methodName)) {
            }
            return null;
        }
    }

    /**
     * Retrieves the appropriate instance of AppLovin's SDK from the SDK key given in the server parameters, or Android Manifest.
     */
    static AppLovinSdk retrieveSdk(final Map<String, String> serverExtras, final Context context) {
        final String sdkKey = serverExtras != null ? serverExtras.get("sdk_key") : null;
        final AppLovinSdk sdk;
        if (sdkKey != null && sdkKey.length() > 0) {
            sdk = AppLovinSdk.getInstance(sdkKey, new AppLovinSdkSettings(), context);
        } else {
            sdk = AppLovinSdk.getInstance(context);
        }
        return sdk;
    }

}
