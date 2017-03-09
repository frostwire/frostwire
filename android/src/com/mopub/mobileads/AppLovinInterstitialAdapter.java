/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;

import com.applovin.adview.AppLovinInterstitialAd;
import com.applovin.adview.AppLovinInterstitialAdDialog;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdClickListener;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinSdk;
import com.frostwire.util.Logger;
import com.mopub.mobileads.CustomEventInterstitial;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.mopub.mobileads.MoPubErrorCode;

import java.util.Map;

// MoPub Adapter
public final class AppLovinInterstitialAdapter extends CustomEventInterstitial implements AppLovinAdLoadListener {
    private static Logger LOG = Logger.getLogger(AppLovinInterstitialAdapter.class);
    private CustomEventInterstitial.CustomEventInterstitialListener mInterstitialListener;
    private Activity parentActivity;
    private AppLovinSdk sdk;
    private AppLovinAd lastReceived;

    /*
     * Abstract methods from CustomEventInterstitial
     */
    @Override
    public void loadInterstitial(Context context, CustomEventInterstitial.CustomEventInterstitialListener interstitialListener, Map<String, Object> localExtras, Map<String, String> serverExtras) {
        mInterstitialListener = interstitialListener;
        if (context instanceof Activity) {
            parentActivity = (Activity) context;
        } else {
            mInterstitialListener.onInterstitialFailed(MoPubErrorCode.INTERNAL_ERROR);
            return;
        }
        LOG.info("Request received for new interstitial.");
        sdk = AppLovinSdk.getInstance(context);
        sdk.getAdService().loadNextAd(AppLovinAdSize.INTERSTITIAL, this);
    }

    @Override
    public void showInterstitial() {
        final AppLovinAd adToRender = lastReceived;
        if (adToRender != null) {
            LOG.info("Showing MoPub-AppLovin interstitial ad");
            parentActivity.runOnUiThread(new Runnable() {
                public void run() {
                    AppLovinInterstitialAdDialog inter = AppLovinInterstitialAd.create(sdk, parentActivity);
                    inter.setAdClickListener(new AppLovinAdClickListener() {
                        @Override
                        public void adClicked(AppLovinAd appLovinAd) {
                            mInterstitialListener.onLeaveApplication();
                        }
                    });
                    inter.setAdDisplayListener(new AppLovinAdDisplayListener() {
                        @Override
                        public void adDisplayed(AppLovinAd appLovinAd) {
                            mInterstitialListener.onInterstitialShown();
                        }

                        @Override
                        public void adHidden(AppLovinAd appLovinAd) {
                            mInterstitialListener.onInterstitialDismissed();
                        }
                    });
                    inter.showAndRender(adToRender);
                }
            });
        }
    }

    @Override
    public void onInvalidate() {
    }

    @Override
    public void adReceived(AppLovinAd ad) {
        LOG.info("AppLovin interstitial loaded successfully through MoPub's Adapter");
        lastReceived = ad;
        parentActivity.runOnUiThread(new Runnable() {
            public void run() {
                mInterstitialListener.onInterstitialLoaded();
            }
        });
    }

    @Override
    public void failedToReceiveAd(final int errorCode) {
        parentActivity.runOnUiThread(new Runnable() {
            public void run() {
                if (errorCode == 204) {
                    mInterstitialListener.onInterstitialFailed(MoPubErrorCode.NO_FILL);
                } else if (errorCode >= 500) {
                    mInterstitialListener.onInterstitialFailed(MoPubErrorCode.SERVER_ERROR);
                } else if (errorCode < 0) {
                    mInterstitialListener.onInterstitialFailed(MoPubErrorCode.INTERNAL_ERROR);
                } else {
                    mInterstitialListener.onInterstitialFailed(MoPubErrorCode.UNSPECIFIED);
                }
            }
        });
    }
}
