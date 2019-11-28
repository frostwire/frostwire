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

import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.util.Logger;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubInterstitial;

/**
 * Created on 11/12/16.
 * @author aldenml
 * @author gubatron
 */
public class MoPubInterstitialListener implements InterstitialListener, MoPubInterstitial.InterstitialAdListener {

    private static final Logger LOG = Logger.getLogger(MoPubInterstitialListener.class);
    private final MoPubAdNetwork mopubAdNetwork;
    private final String placement;
    private MoPubInterstitial interstitial;
    private boolean shutDownAfter = false;
    private boolean finishAfterDismiss = false;
    private boolean wasPlayingMusic;

    public MoPubInterstitialListener(AdNetwork adNetwork, String placement) {
        mopubAdNetwork = (MoPubAdNetwork) adNetwork;
        this.placement = placement;
    }

    @Override
    public boolean isAdReadyToDisplay() {
        return interstitial != null && interstitial.isReady();
    }

    @Override
    public boolean isVideoAd() {
        // let's assume it's always a video, no method tells us if it is on MoPub's API.
        return interstitial != null;
    }

    @Override
    public boolean show(Activity activity, String placement) {
        LOG.info("MoPubInterstitialListener.show(): wasPlayingMusic=" + wasPlayingMusic);
        boolean shown = isAdReadyToDisplay() && interstitial.show();
        if (shown && wasPlayingMusic) {
            MusicUtils.pause();
        }
        return shown;
    }

    @Override
    public void shutdownAppAfter(boolean shutdown) {
        shutDownAfter = shutdown;
    }

    @Override
    public void dismissActivityAfterwards(boolean dismiss) {
        finishAfterDismiss = dismiss;
    }

    @Override
    public void wasPlayingMusic(boolean wasPlayingMusic) {
        this.wasPlayingMusic = wasPlayingMusic;
    }
    // MoPubInterstitial.InterstitialAdListener methods.

    @Override
    public void onInterstitialLoaded(MoPubInterstitial interstitial) {
        this.interstitial = interstitial;
    }

    @Override
    public void onInterstitialFailed(MoPubInterstitial interstitial, MoPubErrorCode errorCode) {
        // ad failed to load, excellent place to load more ads.
        this.interstitial = null;
//        LOG.warn("MoPub onInterstitialFailed - errorCode: " + errorCode.toString());
        mopubAdNetwork.loadMoPubInterstitial(interstitial.getActivity(), placement);
    }

    @Override
    public void onInterstitialShown(MoPubInterstitial interstitial) {
//        LOG.info("MoPub onInterstitialShown - " + interstitial.toString());
        if (wasPlayingMusic && !shutDownAfter && !MusicUtils.isPlaying()) {
            MusicUtils.play();
        }
    }

    @Override
    public void onInterstitialClicked(MoPubInterstitial interstitial) {
        //LOG.info("onInterstitialClicked - " + interstitial.toString());
    }

    @Override
    public void onInterstitialDismissed(MoPubInterstitial interstitial) {
//        LOG.info("onInterstitialDismissed");
        Activity activity = interstitial.getActivity();

        if (interstitial != null) {
            try {
                interstitial.destroy();
            } catch (Throwable t) {
                LOG.warn("MoPubInterstitial.onInterstitialDismissed() - could not destroy dismissed interstitial", t);
            }
        }

        Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(activity, finishAfterDismiss, shutDownAfter, true, activity.getApplication());
        if (!shutDownAfter && !finishAfterDismiss) {
            mopubAdNetwork.loadMoPubInterstitial(activity, placement);
        }
    }
}
