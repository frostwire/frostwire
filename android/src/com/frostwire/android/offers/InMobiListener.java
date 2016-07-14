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
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;
import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiInterstitial;

import java.lang.ref.WeakReference;
import java.util.Map;

class InMobiListener implements InterstitialListener, InMobiInterstitial.InterstitialAdListener {
    private final Logger LOG = Logger.getLogger(InMobiInterstitial.InterstitialAdListener.class);
    private final WeakReference<Activity> activityRef;
    private final Application app;
    private boolean shutdownAfterDismiss = false;
    private boolean finishAfterDismiss = false;
    private boolean ready;
    private static final int MAX_INTERSTITIAL_LOAD_RETRIES = 5;
    private static int INTERSTITIAL_RETRIES_LEFT = MAX_INTERSTITIAL_LOAD_RETRIES;
    private static int INTERSTITIAL_RELOAD_WAIT_IN_SECS = 20;

    InMobiListener(Activity hostActivity) {
        activityRef = new WeakReference<>(hostActivity);
        this.app = hostActivity.getApplication();
    }

    @Override
    public void onAdDismissed(InMobiInterstitial imInterstitial) {
        //LOG.info("InMobiListener.onAdDismissed");
        wrapItUp(imInterstitial);
    }

    @Override
    public void onAdLoadFailed(final InMobiInterstitial imInterstitial, InMobiAdRequestStatus imErrorCode) {
        ready = false;
        LOG.info("InMobiListener.onAdLoadFailed - errorCode: " + imErrorCode.getStatusCode() + " - " + imErrorCode.getMessage());
        reloadInterstitialLater(imInterstitial, INTERSTITIAL_RELOAD_WAIT_IN_SECS);
    }

    @Override
    public void onAdLoadSucceeded(InMobiInterstitial imInterstitial) {
        ready = true;
        INTERSTITIAL_RETRIES_LEFT = MAX_INTERSTITIAL_LOAD_RETRIES;
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
    public boolean isAdReadyToDisplay() {
        return ready;
    }

    @Override
    public boolean isVideoAd() {
        return false;
    }

    @Override
    public boolean show(WeakReference<Activity> activityWeakReference) {
        //unused.
        return false;
    }

    @Override
    public void shutdownAppAfter(boolean shutdown) {
        shutdownAfterDismiss = shutdown;
    }

    @Override
    public void dismissActivityAfterwards(boolean dismiss) {
        finishAfterDismiss = dismiss;
    }

    private void ifShutdownAfterDismiss(Activity callerActivity) {
        if (shutdownAfterDismiss) {
            // Finish through MainActivity caller
            if (callerActivity != null && callerActivity instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) callerActivity;
                mainActivity.shutdown();
            } else {
                Intent i = new Intent(app, MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("shutdown-" + ConfigurationManager.instance().getUUIDString(), true);
                app.startActivity(i);
            }
        }
    }

    private void ifFinishAfterDismiss(Activity callerActivity) {
        if (finishAfterDismiss) {
            try {
                if (callerActivity != null) {
                    callerActivity.finish();
                }
            } catch (Throwable e) {
                // meh, activity was a goner already, shutdown was true most likely.
            }
        }
    }

    private void reloadInterstitialLater(final InMobiInterstitial imInterstitial, final int secondsLater) {
        if (INTERSTITIAL_RETRIES_LEFT < 1) {
            //LOG.info("Not reloading interstitial anymore, exhausted.");
            return;
        }
        INTERSTITIAL_RETRIES_LEFT--;
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new InterstitialReloader(imInterstitial), secondsLater*1000);
    }

    private void wrapItUp(InMobiInterstitial imInterstitial) {
        Activity callerActivity = Ref.alive(activityRef) ? activityRef.get() : null;
        ifShutdownAfterDismiss(callerActivity);
        ifFinishAfterDismiss(callerActivity);
        if (!shutdownAfterDismiss) {
            reloadInterstitialLater(imInterstitial, INTERSTITIAL_RELOAD_WAIT_IN_SECS);
        }
    }

    private static class InterstitialReloader implements Runnable {
        private static Logger LOG = Logger.getLogger(InterstitialReloader.class);
        private final WeakReference<InMobiInterstitial> interstitialRef;

        InterstitialReloader(InMobiInterstitial interstitial) {
            interstitialRef = Ref.weak(interstitial);
        }

        @Override
        public void run() {
            if (!Ref.alive(interstitialRef)) {
                LOG.info("Can't reload interstitial, lost reference to interstitial. aborting.");
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