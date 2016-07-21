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
import com.andrew.apollo.utils.MusicUtils;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinSdk;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.logging.Logger;

import java.lang.ref.WeakReference;

public class AppLovinAdNetwork implements AdNetwork {

    private static final Logger LOG = Logger.getLogger(AppLovinAdNetwork.class);
    private static final boolean DEBUG_MODE = Offers.DEBUG_MODE;

    private AppLovinInterstitialAdapter interstitialAdapter = null;
    private boolean started = false;

    public AppLovinAdNetwork() {
    }

    public void initialize(final Activity activity) {
        if (!enabled()) {
            LOG.info("AppLovin initialize(): aborted. not enabled.");
            return;
        }

        Offers.THREAD_POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!started) {
                        final Context applicationContext = activity.getApplicationContext();
                        AppLovinSdk.initializeSdk(applicationContext);
                        if (DEBUG_MODE) {
                            AppLovinSdk.getInstance(applicationContext).getSettings().setVerboseLogging(true);
                        }
                        loadNewInterstitial(activity);
                        started = true;
                    }
                } catch (Throwable e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public void stop(Context context) {
        started = false;
        LOG.info("stopped");
    }

    public void loadNewInterstitial(Activity activity) {
        interstitialAdapter = new AppLovinInterstitialAdapter(activity, this);
        AppLovinSdk.getInstance(activity).getAdService().loadNextAd(AppLovinAdSize.INTERSTITIAL, interstitialAdapter);
    }

    public boolean started() {
        return started;
    }

    public boolean enabled() {
        if (DEBUG_MODE) {
            return true;
        }

        ConfigurationManager config;
        boolean enabled = false;
        try {
            config = ConfigurationManager.instance();
            enabled = config.getBoolean(Constants.PREF_KEY_GUI_USE_APPLOVIN);
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }
        return enabled;
    }

    public boolean showInterstitial(final WeakReference<Activity> activityWeakReference,
                                    final boolean shutdownAfterwards,
                                    final boolean dismissAfterward) {
        if (enabled() && started) {
            interstitialAdapter.shutdownAppAfter(shutdownAfterwards);
            interstitialAdapter.dismissActivityAfterwards(dismissAfterward);
            try {
                if (interstitialAdapter.isVideoAd() && MusicUtils.isPlaying()) {
                    return false;
                }
                return interstitialAdapter.isAdReadyToDisplay() && interstitialAdapter.show(activityWeakReference);
            } catch (Throwable e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return false;
        }
    }
}
