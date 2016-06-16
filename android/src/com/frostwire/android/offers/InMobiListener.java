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
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;
import com.inmobi.ads.InMobiAdRequestStatus;
import com.inmobi.ads.InMobiInterstitial;

import java.lang.ref.WeakReference;
import java.util.Map;

public class InMobiListener implements InterstitialListener, InMobiInterstitial.InterstitialAdListener {
    private final Logger LOG = Logger.getLogger(InMobiInterstitial.InterstitialAdListener.class);
    private final WeakReference<Activity> activityRef;
    private final Application app;
    private boolean shutdownAfterDismiss = false;
    private boolean finishAfterDismiss = false;
    private boolean ready;

    public InMobiListener(Activity hostActivity) {
        activityRef = new WeakReference<Activity>(hostActivity);
        this.app = hostActivity.getApplication();
    }

    @Override
    public void onAdDismissed(InMobiInterstitial imInterstitial) {
        Activity callerActivity = Ref.alive(activityRef) ? activityRef.get() : null;

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

        if (finishAfterDismiss) {
            try {
                if (callerActivity != null) {
                    callerActivity.finish();
                }
            } catch (Throwable e) {
                // meh, activity was a goner already, shutdown was true most likely.
            }
        }

        imInterstitial.load();
    }

    @Override
    public void onAdLoadFailed(InMobiInterstitial imInterstitial, InMobiAdRequestStatus imErrorCode) {
        ready = false;
    }

    @Override
    public void onAdLoadSucceeded(InMobiInterstitial imInterstitial) {
        ready = true;
    }

    @Override
    public void onAdDisplayed(InMobiInterstitial imInterstitial) {
    }

    @Override
    public void onAdInteraction(InMobiInterstitial imInterstitial, Map<Object, Object> map) {
    }

    @Override
    public void onUserLeftApplication(InMobiInterstitial imInterstitial) {
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
}
