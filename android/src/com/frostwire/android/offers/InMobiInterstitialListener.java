/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.offers;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiInterstitial;

import java.lang.ref.WeakReference;
import java.util.Map;

class InMobiInterstitialListener implements InterstitialListener, InMobiInterstitial.InterstitialAdListener2 {
    private static final Logger LOG = Logger.getLogger(InMobiInterstitialListener.class);
    private final WeakReference<? extends Activity> activityRef;
    private final Application app;
    private final InMobiAdNetwork adNetwork;
    private boolean shutDownAfter = false;
    private boolean finishAfterDismiss = false;
    private boolean ready;
    private static final int MAX_INTERSTITIAL_LOAD_RETRIES = 5;
    private static int INTERSTITIAL_RETRIES_LEFT = MAX_INTERSTITIAL_LOAD_RETRIES;
    private static int INTERSTITIAL_RELOAD_WAIT_IN_SECS = 60;

    InMobiInterstitialListener(InMobiAdNetwork adNetwork, Activity hostActivity) {
        activityRef = new WeakReference<>(hostActivity);
        this.adNetwork = adNetwork;
        this.app = hostActivity.getApplication();
    }

    @Override
    public void onAdDismissed(InMobiInterstitial imInterstitial) {
        wrapItUp(imInterstitial);
    }

    @Override
    public void onAdLoadFailed(final InMobiInterstitial imInterstitial, InMobiAdRequestStatus imErrorCode) {
        ready = false;
        LOG.info("InMobiListener.onAdLoadFailed - errorCode: " + imErrorCode.getStatusCode() + " - " + imErrorCode.getMessage());
        adNetwork.finishedLoadingInterstitial();
        reloadInterstitialLater(imInterstitial, INTERSTITIAL_RELOAD_WAIT_IN_SECS);
    }

    @Override
    public void onAdReceived(InMobiInterstitial inMobiInterstitial) {
        LOG.info("InMobiListener.onAdReceived");
        adNetwork.finishedLoadingInterstitial();
    }

    @Override
    public void onAdLoadSucceeded(InMobiInterstitial imInterstitial) {
        ready = true;
        INTERSTITIAL_RETRIES_LEFT = MAX_INTERSTITIAL_LOAD_RETRIES;
        adNetwork.finishedLoadingInterstitial();
        LOG.info("InMobiListener.onAdLoadSucceeded");
    }

    @Override
    public void onAdDisplayed(InMobiInterstitial imInterstitial) {
        //LOG.info("InMobiListener.onAdDisplayed");
    }

    @Override
    public void onAdInteraction(InMobiInterstitial imInterstitial, Map<Object, Object> map) {
        //LOG.info("InMobiListener.onAdInteraction! map size ("+(map != null ? map.size() : -1)+")");
    }

    @Override
    public void onUserLeftApplication(InMobiInterstitial imInterstitial) {
        //LOG.info("InMobiListener.onUserLeftApplication!");
        wrapItUp(imInterstitial);
    }

    @Override
    public void onAdRewardActionCompleted(InMobiInterstitial inMobiInterstitial, Map<Object, Object> map) {
    }

    @Override
    public void onAdDisplayFailed(InMobiInterstitial inMobiInterstitial) {

    }

    @Override
    public void onAdWillDisplay(InMobiInterstitial inMobiInterstitial) {

    }

    @Override
    public boolean isAdReadyToDisplay() {
        return ready;
    }

    @Override
    public boolean isVideoAd() {
        return false;
    }

    @Override
    public boolean show(Activity activity, String placement) {
        //unused
        return false;
    }

    @Override
    public void shutdownAppAfter(boolean shutdown) {
        shutDownAfter = shutdown;
    }

    @Override
    public void dismissActivityAfterwards(boolean dismiss) {
        finishAfterDismiss = dismiss;
    }

    private void reloadInterstitialLater(final InMobiInterstitial imInterstitial, final int secondsLater) {
        if (INTERSTITIAL_RETRIES_LEFT < 1) {
            //LOG.info("Not reloading interstitial anymore, exhausted.");
            return;
        }
        INTERSTITIAL_RETRIES_LEFT--;
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new InterstitialReloader(adNetwork, imInterstitial), secondsLater*1000);
    }

    private void wrapItUp(InMobiInterstitial imInterstitial) {
        Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(adNetwork, activityRef.get(), finishAfterDismiss, shutDownAfter, true, app);
        if (!shutDownAfter) {
            reloadInterstitialLater(imInterstitial, INTERSTITIAL_RELOAD_WAIT_IN_SECS);
        }
    }

    private static class InterstitialReloader implements Runnable {
        private final WeakReference<InMobiAdNetwork> adNetworkRef;
        private final WeakReference<InMobiInterstitial> interstitialRef;

        InterstitialReloader(InMobiAdNetwork adNetwork, InMobiInterstitial interstitial) {
            adNetworkRef = Ref.weak(adNetwork);
            interstitialRef = Ref.weak(interstitial);
        }

        @Override
        public void run() {
            if (!Ref.alive(adNetworkRef)) {
                LOG.info("Can't reload interstitial, lost reference to ad network. aborting.");
                return;
            }
            if (!Ref.alive(interstitialRef)) {
                LOG.info("Can't reload interstitial, lost reference to interstitial. aborting.");
                return;
            }
            InMobiAdNetwork adNetwork = adNetworkRef.get();

            if (adNetwork.loadingInterstitial()) {
                LOG.info("Aborted interstitial reload, InMobiAdNetwork still busy loading interstitial");
                return;
            }

            InMobiInterstitial imInterstitial = interstitialRef.get();
            try {
                LOG.info("Reloading ads (Attempts left: " + INTERSTITIAL_RETRIES_LEFT + ")");
                if (imInterstitial != null) {
                    try {
                        imInterstitial.load();
                    } catch (Throwable t) {
                        LOG.info("InMobiListener.onAdLoadFailed reload failed", t);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
