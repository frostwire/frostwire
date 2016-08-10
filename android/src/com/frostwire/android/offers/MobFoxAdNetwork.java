/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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
import com.frostwire.android.core.Constants;
import com.frostwire.util.Logger;
import com.mobfox.sdk.interstitialads.InterstitialAd;

import java.lang.ref.WeakReference;

/**
 * Created on 8/8/16.
 * @author gubatron
 * @author aldenml
 */
final class MobFoxAdNetwork implements AdNetwork {
    private static final Logger LOG = Logger.getLogger(MobFoxAdNetwork.class);
    private static final boolean DEBUG_MODE = Offers.DEBUG_MODE;

    private boolean started = false;
    private MobFoxInterstitialListener interstitialAdListener = null;
    private InterstitialAd interstitialAd;

    // these guys want you to handle permission handling, so MainActivity will need
    // this reference to invoke onPermissionsGranted on the ad.
    InterstitialAd getInterstitialAd() {
        return interstitialAd;
    }

    @Override
    public void initialize(Activity activity) {
        if (!enabled()) {
            if (!started()) {
                LOG.info("MobFoxAdNetwork initialize(): aborted. not enabled.");
            } else {
                // initialize can be called multiple times, we may have to stop
                // this network if we started it using a default value.
                stop(activity);
            }
            return;
        }
        loadNewInterstitial(activity);
        started = true;
    }

    @Override
    public void stop(Context context) {
        started = false;
        interstitialAd = null;
        interstitialAdListener = null;
    }

    @Override
    public void enable(boolean enabled) {
        Offers.AdNetworkHelper.enable(this, enabled);
    }

    @Override
    public boolean enabled() {
        return Offers.AdNetworkHelper.enabled(this);
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public boolean showInterstitial(WeakReference<? extends Activity> activityRef, boolean shutdownActivityAfterwards, boolean dismissActivityAfterward) {
        if (enabled() && started) {

            if (interstitialAdListener == null) {
                LOG.warn("showInterstitial() aborted. interstitial ad listener wasn't created yet, check your logic.");
                return false;
            }

            interstitialAdListener.shutdownAppAfter(shutdownActivityAfterwards);
            interstitialAdListener.dismissActivityAfterwards(dismissActivityAfterward);
            try {
                return interstitialAdListener.isAdReadyToDisplay() && interstitialAdListener.show(activityRef);
            } catch (Throwable e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public void loadNewInterstitial(Activity activity) {
        LOG.info("loadNewInterstitial");
        interstitialAdListener = new MobFoxInterstitialListener(activity);
        interstitialAd = new InterstitialAd(activity);
        interstitialAd.setInventoryHash(Constants.MOBFOX_INVENTORY_HASH);
        interstitialAd.setListener(interstitialAdListener);

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                interstitialAd.load();
            }
        });
    }

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_MOBFOX;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_MOBFOX;
    }

    @Override
    public boolean isDebugOn() {
        return DEBUG_MODE;
    }
}
