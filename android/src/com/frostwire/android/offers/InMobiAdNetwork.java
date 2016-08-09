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
import com.inmobi.ads.InMobiInterstitial;
import com.inmobi.sdk.InMobiSdk;

import java.lang.ref.WeakReference;

class InMobiAdNetwork implements AdNetwork {

    private static final Logger LOG = Logger.getLogger(InMobiAdNetwork.class);
    private static final boolean DEBUG_MODE = Offers.DEBUG_MODE;

    private InMobiInterstitialListener inmobiListener;
    private InMobiInterstitial inmobiInterstitial;
    private boolean started = false;
    private final long INTERSTITIAL_PLACEMENT_ID = 1431974497868150L;

    InMobiAdNetwork() {
    }

    public void initialize(final Activity activity) {
        if (!enabled()) {
            if (!started()) {
                LOG.info("InMobi initialize(): aborted. not enabled.");
            } else {
                // initialize can be called multiple times, we may have to stop
                // this network if we started it using a default value.
                stop(activity);
            }
            return;
        }

        if (!started) {
            try {
                // this initialize call is very expensive, this is why we should be invoked in a thread.
                LOG.info("InMobi.initialize()...");
                InMobiSdk.init(activity, Constants.INMOBI_INTERSTITIAL_PROPERTY_ID);
                if (DEBUG_MODE) {
                    InMobiSdk.setLogLevel(InMobiSdk.LogLevel.DEBUG);
                }
                LOG.info("InMobi.initialized.");
                started = true;
                LOG.info("Load InmobiInterstitial.");
                loadNewInterstitial(activity);
            } catch (Throwable t) {
                t.printStackTrace();
                started = false;
            }
        }
    }

    @Override
    public void stop(Context context) {
        started = false;
        LOG.info("stopped");
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
    public boolean showInterstitial(final WeakReference<? extends Activity> activityWeakReference,
                                    boolean shutdownActivityAfterwards,
                                    boolean dismissActivityAfterward) {
        if (!started || !enabled() || inmobiInterstitial == null || inmobiListener == null) {
            return false;
        }

        inmobiListener.shutdownAppAfter(shutdownActivityAfterwards);
        inmobiListener.dismissActivityAfterwards(dismissActivityAfterward);

        if (inmobiInterstitial.isReady()) {
//            LOG.info("inmobiInterstitial.isReady()");
            try {
                inmobiInterstitial.show();
                return true;
            } catch (Throwable e) {
                LOG.error("InMobi Interstitial failed on .show()!", e);
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public void loadNewInterstitial(final Activity activity) {
        if (!started) {
            return; //not ready
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    inmobiListener = new InMobiInterstitialListener(activity);
                    inmobiInterstitial = new InMobiInterstitial(activity, INTERSTITIAL_PLACEMENT_ID, inmobiListener);
                    inmobiInterstitial.load();
                } catch (Throwable t) {
                    LOG.warn("InmobiAdNetwork.loadInterstitial() failed", t);
                    // don't crash, keep going.
                    // possible android.util.AndroidRuntimeException: android.content.pm.PackageManager$NameNotFoundException: com.google.android.webview
                }
            }
        });
    }

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_INMOBI;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_INMOBI;
    }

    @Override
    public boolean isDebugOn() {
        return DEBUG_MODE;
    }
}
