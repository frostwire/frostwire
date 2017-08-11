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
import android.os.Looper;

import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.mopub.mobileads.MoPubInterstitial;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created on Nov/8/16 (2016 US election day)
 *
 * @author aldenml
 * @author gubatron
 */

public class MoPubAdNetwork extends AbstractAdNetwork {
    private static final Logger LOG = Logger.getLogger(MoPubAdNetwork.class);
    private static final boolean DEBUG_MODE = Offers.DEBUG_MODE;
    private Map<String,String> placements;
    private Map<String, MoPubInterstitial> interstitials;

    @Override
    public void initialize(Activity activity) {
        if (abortInitialize(activity)) {
            return;
        }
        initPlacementMappings(activity);
        start();
        loadNewInterstitial(activity);
    }

    private void initPlacementMappings(Activity activity) {
        boolean isTablet = UIUtils.isTablet(activity);
        placements = new HashMap<>();

        if (!isTablet) {
            placements.put(Offers.PLACEMENT_INTERSTITIAL_EXIT, "399a20d69bdc449a8e0ca171f82179c8");
        } else {
            placements.put(Offers.PLACEMENT_INTERSTITIAL_EXIT, "cebdbc56b37c4d31ba79e861d1cb0de4");
        }
    }

    @Override
    public boolean showInterstitial(Activity activity, String placement, boolean shutdownActivityAfterwards, boolean dismissActivityAfterward) {
        if (interstitials == null || interstitials.isEmpty()) {
            return false;
        }
        MoPubInterstitial interstitial = interstitials.get(placement);
        MoPubInterstitialListener listener = (MoPubInterstitialListener) interstitial.getInterstitialAdListener();
        if (listener != null) {
            listener.shutdownAppAfter(shutdownActivityAfterwards);
            listener.dismissActivityAfterwards(dismissActivityAfterward);
        }
        return listener != null && interstitial.isReady() && interstitial.show();
    }

    @Override
    public void loadNewInterstitial(final Activity activity) {
        if (!started() || !enabled()) {
            LOG.info("loadNewInterstitial() aborted. Network not started or not enabled");
            return; //not ready
        }
        if (placements.isEmpty()) {
            LOG.warn("check your logic, can't call loadNewInterstitial() before initialize()");
            return;
        }
        interstitials = new HashMap<>();
        Set<String> placementKeys = placements.keySet();
        for (String placement : placementKeys) {
            loadMoPubInterstitial(activity, placement);
        }
    }

    public void loadMoPubInterstitial(final Activity activity, final String placement) {
        if (activity == null) {
            LOG.info("Aborted loading interstitial ("+placement+"), no Activity");
            return;
        }
        if (!started() || !enabled()) {
            LOG.info("loadMoPubInterstitial(placement="+placement+") aborted. Network not started or not enabled");
            return;
        }
        LoadMoPubInterstitialRunnable runnable = new LoadMoPubInterstitialRunnable(this, activity, placement);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Engine.instance().getThreadPool().submit(runnable);
        } else {
            runnable.run();
        }
    }

    private final static class LoadMoPubInterstitialRunnable implements Runnable {
        private final WeakReference<Activity> activityRef;
        private final MoPubAdNetwork moPubAdNetwork;
        private final String placement;

        LoadMoPubInterstitialRunnable(MoPubAdNetwork moPubAdNetwork, Activity activity, String placement) {
            this.moPubAdNetwork = moPubAdNetwork;
            activityRef = Ref.weak(activity);
            this.placement = placement;
        }

        @Override
        public void run() {
            if (!Ref.alive(activityRef)) {
                return;
            }
            Activity activity = activityRef.get();
            final MoPubInterstitial moPubInterstitial = new MoPubInterstitial(activity, moPubAdNetwork.placements.get(placement));
            MoPubInterstitialListener moPubListener = new MoPubInterstitialListener(moPubAdNetwork, placement);
            moPubInterstitial.setInterstitialAdListener(moPubListener);
            moPubAdNetwork.interstitials.put(placement, moPubInterstitial);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    LOG.info("Loading " + placement + " interstitial");
                    try {
                        moPubInterstitial.load();
                    } catch (Throwable e) {
                        LOG.warn("Mopub Interstitial couldn't be loaded", e);
                    }
                }
            });
        }
    }

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_MOPUB;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_MOPUB;
    }

    @Override
    public boolean isDebugOn() {
        return DEBUG_MODE;
    }

    @Override
    public void stop(Context context) {
        super.stop(context);
        if (placements == null || interstitials == null || placements.isEmpty() || interstitials.isEmpty()) {
            return;
        }
        Set<String> placementKeys = placements.keySet();
        for (String key : placementKeys) {
            MoPubInterstitial interstitial = interstitials.get(key);
            if (interstitial != null) {
                interstitial.destroy();
            }
        }
    }
}
