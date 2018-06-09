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

import com.andrew.apollo.utils.MusicUtils;
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

    public static final String UNIT_ID_AUDIO_PLAYER = "c737d8a55b2e41189aa1532ae0520ad1";
    public static final String UNIT_ID_PREVIEW_PLAYER_VERTICAL = "a8be0cad4ad0419dbb19601aef3a18d2";
    public static final String UNIT_ID_PREVIEW_PLAYER_HORIZONTAL = "2fd0fafe3d3c4d668385a620caaa694e";
    public static final String UNIT_ID_SEARCH_HEADER = "be0b959f15994fd5b56c997f63530bd0";
    private boolean wasPlayingMusic;

    @Override
    public void initialize(Activity activity) {
        if (abortInitialize(activity)) {
            LOG.info("initialize() aborted");
            return;
        }
        initPlacementMappings(UIUtils.isTablet(activity.getResources()));

        // Note 1: Not performing this .initializeSdk(...) call for now.
        // It's only needed for personalized ads and rewarded videos which we don't have.
        // It was causing the FrostWire process to be relaunched after a shutdown.

        // Note 2: unsure how this works for many ads, and multiple networks.
        // for now just adding the main search banner seems to work for other
        // banner units
        //SdkConfiguration sdkConfiguration = new SdkConfiguration.Builder(UNIT_ID_SEARCH_HEADER).build();
        //MoPub.initializeSdk(activity, sdkConfiguration, null);
        start();
        loadNewInterstitial(activity);
    }

    private void initPlacementMappings(boolean isTablet) {
        placements = new HashMap<>();
        if (!isTablet) {
            placements.put(Offers.PLACEMENT_INTERSTITIAL_MAIN, "399a20d69bdc449a8e0ca171f82179c8");
        } else {
            placements.put(Offers.PLACEMENT_INTERSTITIAL_MAIN, "cebdbc56b37c4d31ba79e861d1cb0de4");
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
            wasPlayingMusic = MusicUtils.isPlaying();
            listener.wasPlayingMusic(wasPlayingMusic);
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
