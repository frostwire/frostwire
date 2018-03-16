/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.offers;

import android.app.Activity;
import android.content.Context;

import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.mopub.mobileads.MoPubInterstitial;

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
        initPlacementMappings(UIUtils.isTablet(activity.getResources()));
        start();
        loadNewInterstitial(activity);
    }

    private void initPlacementMappings(boolean isTablet) {
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
        LOG.info("Loading " + placement + " interstitial");
        try {
            final MoPubInterstitial moPubInterstitial = new MoPubInterstitial(activity, placements.get(placement));
            MoPubInterstitialListener moPubListener = new MoPubInterstitialListener(this, placement);
            moPubInterstitial.setInterstitialAdListener(moPubListener);
            interstitials.put(placement, moPubInterstitial);
            moPubInterstitial.load();
        } catch (Throwable e) {
            LOG.warn("Mopub Interstitial couldn't be loaded", e);
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
