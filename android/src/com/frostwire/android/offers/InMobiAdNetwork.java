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

import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.inmobi.ads.InMobiInterstitial;
import com.inmobi.sdk.InMobiSdk;

import java.util.concurrent.atomic.AtomicBoolean;

class InMobiAdNetwork extends AbstractAdNetwork {

    private static final Logger LOG = Logger.getLogger(InMobiAdNetwork.class);
    private static final boolean DEBUG_MODE = Offers.DEBUG_MODE;

    private InMobiInterstitialListener inmobiListener;
    private InMobiInterstitial inmobiInterstitial;

    // as it was before
    //private final String INMOBI_INTERSTITIAL_PROPERTY_ID = "c1e6be702d614523b725af8b86f99e8f";
    //private final long INTERSTITIAL_PLACEMENT_ID = 1431974497868150L;

    // as it looks in the integration docs now with the auto generated IDs.
    private final String INMOBI_INTERSTITIAL_PROPERTY_ID = "49c2c20fd5354ab6b3f9418e25ccc351";
    private final long INTERSTITIAL_PLACEMENT_ID = 1471550843414L;

    private final AtomicBoolean loadingInterstitial;

    InMobiAdNetwork() {
        loadingInterstitial = new AtomicBoolean(false);
    }

    public void initialize(final Activity activity) {
        if (abortInitialize(activity)) {
            return;
        }
        if (!started()) {
            try {
                Runnable sdkInitializer = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            LOG.info("InMobiSdk.init...");
                            InMobiSdk.init(activity, INMOBI_INTERSTITIAL_PROPERTY_ID);
                            if (DEBUG_MODE) {
                                InMobiSdk.setLogLevel(InMobiSdk.LogLevel.DEBUG);
                            }
                            LOG.info("InMobiSdk.initialized.");
                            start();
                            loadNewInterstitial(activity);
                        } catch (Throwable t) {
                            LOG.error("InMobiAdNetwork.initialize() failed", t);
                            finishedLoadingInterstitial();
                        }
                    }
                };
                activity.runOnUiThread(sdkInitializer);
            } catch (Throwable t) {
                t.printStackTrace();
                stop(activity);
            }
        }
    }

    public void startedLoadingInterstitial() {
        //LOG.info("startedLoadingInterstitial");
        loadingInterstitial.set(true);
    }

    public void finishedLoadingInterstitial() {
        //LOG.info("finishedLoadingInterstitial");
        loadingInterstitial.set(false);
    }

    public boolean loadingInterstitial() {
        return loadingInterstitial.get();
    }

    @Override
    public boolean showInterstitial(Activity activity,
                                    String placement,
                                    boolean shutdownActivityAfterwards,
                                    boolean dismissActivityAfterward) {
        if (!started() || !enabled() || inmobiInterstitial == null || inmobiListener == null) {
            return false;
        }
        inmobiListener.shutdownAppAfter(shutdownActivityAfterwards);
        inmobiListener.dismissActivityAfterwards(dismissActivityAfterward);
        if (inmobiInterstitial.isReady()) {
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
    public void loadNewInterstitial(final Activity activity) {
        if (!started() || !enabled()) {
            LOG.warn("InMobiAdNetwork.loadNewInsterstitial() aborted. started = " + started() + "; enabled = "  + enabled());
            return; //not ready
        }
        if (loadingInterstitial()) {
            LOG.warn("InMobiAdNetwork.loadNewInsterstitial() aborted. Still busy loading an interstitial");
            return;
        }
        if (!UIUtils.inUIThread()) {
            LOG.warn("InMobiAdNetwork.loadNewInsterstitial() aborted. Not being invoked from UI thread");
            return;
        }
        startedLoadingInterstitial();
        try {
            inmobiListener = new InMobiInterstitialListener(InMobiAdNetwork.this, activity);
            inmobiInterstitial = new InMobiInterstitial(activity, INTERSTITIAL_PLACEMENT_ID, inmobiListener);
            LOG.info("InMobiAdNetwork.loadNewInterstitial() -> interstitial.load()!!!!");
            inmobiInterstitial.load(); // finishedLoadingInterstitial() will be called from listener.
        } catch (Throwable t) {
            finishedLoadingInterstitial();
            LOG.error("InMobiAdNetwork.loadInterstitial() failed", t);
            // don't crash, keep going.
            // possible android.util.AndroidRuntimeException: android.content.pm.PackageManager$NameNotFoundException: com.google.android.webview
        }
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
