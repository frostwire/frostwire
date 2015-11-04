/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(TM). All rights reserved.
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


package com.frostwire.android.gui.adnetworks;


import android.app.Activity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;
import com.inmobi.monetization.IMErrorCode;
import com.inmobi.monetization.IMInterstitial;
import com.inmobi.monetization.IMInterstitialListener;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class InMobiListener implements InterstitialListener, IMInterstitialListener {
    private final Logger LOG = Logger.getLogger(IMInterstitialListener.class);
    private final WeakReference<Activity> activityRef;
    private boolean shutdownAfterDismiss = false;
    private boolean finishAfterDismiss = false;
    private boolean ready;

    public InMobiListener(Activity hostActivity) {
        activityRef = new WeakReference<Activity>(hostActivity);
    }

    @Override
    public void onDismissInterstitialScreen(IMInterstitial imInterstitial) {
        Activity callerActivity = Ref.alive(activityRef) ? activityRef.get() : null;

        if (shutdownAfterDismiss) {
            // Finish through MainActivity caller
            if (callerActivity != null && callerActivity instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) callerActivity;
                mainActivity.shutdown();
            } else if (callerActivity != null) {
                //MIGHT DO: there's a way to shutdown, by sending a shutdown-<GUID> enabled Intent to MainActivity.
                LOG.warn("Couldn't shutdown because listener didn't have a MainActivity reference, had another kind (" + callerActivity.getClass().getName() + ")");
            }
        }

        if (finishAfterDismiss) {
            try {
                if (callerActivity != null) {
                    callerActivity.finish();
                }
            } catch (Throwable e) {
                // meh, activity was a goner already, shutdown was true most likely.
            }
        }

        imInterstitial.loadInterstitial();
    }

    @Override
    public void onInterstitialFailed(IMInterstitial imInterstitial, IMErrorCode imErrorCode) {
        ready = false;
    }

    @Override
    public void onInterstitialLoaded(IMInterstitial imInterstitial) {
        ready = true;
    }

    @Override
    public void onShowInterstitialScreen(IMInterstitial imInterstitial) {
    }

    @Override
    public void onInterstitialInteraction(IMInterstitial imInterstitial, Map<String, String> map) {
    }

    @Override
    public void onLeaveApplication(IMInterstitial imInterstitial) {
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
}