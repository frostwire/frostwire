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
import com.applovin.adview.AppLovinInterstitialAd;
import com.applovin.adview.AppLovinInterstitialAdDialog;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinSdk;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

public class AppLovinInterstitialAdapter implements InterstitialListener, AppLovinAdDisplayListener, AppLovinAdLoadListener {
    private static final Logger LOG = Logger.getLogger(AppLovinInterstitialAdapter.class);
    private WeakReference<Activity> activityRef;
    private final Application app;
    private AppLovinAdNetwork appLovinAdNetwork;
    private AppLovinAd ad;

    private boolean dismissAfter = false;
    private boolean shutdownAfter = false;
    private boolean isVideoAd = false;

    public AppLovinInterstitialAdapter(Activity parentActivity, AppLovinAdNetwork appLovinAdNetwork) {
        this.activityRef = Ref.weak(parentActivity);
        this.appLovinAdNetwork = appLovinAdNetwork;

        this.app = parentActivity.getApplication();
    }

    public boolean isAdReadyToDisplay() {
        return ad != null && Ref.alive(activityRef) && AppLovinInterstitialAd.isAdReadyToDisplay(activityRef.get());
    }

    @Override
    public boolean isVideoAd() {
        return isVideoAd;
    }

    public boolean show(WeakReference<Activity> activityWeakReference) {
        boolean result = false;
        if (ad != null && Ref.alive(activityWeakReference)) {
            try {
                this.activityRef = activityWeakReference;
                final AppLovinInterstitialAdDialog adDialog = AppLovinInterstitialAd.create(AppLovinSdk.getInstance(activityRef.get()), activityRef.get());

                if (adDialog.isShowing()) {
                    // this could happens because a previous ad failed to be properly dismissed
                    // since the code is obfuscated there is no realistic possibility to detect where
                    // the error is, then it needs to be discussed with the provider or change
                    // our usage patter of the framework.
                    LOG.warn("Review the applovin ad framework");
                    adDialog.dismiss();
                    return false;
                }

                adDialog.setAdDisplayListener(this);
                adDialog.showAndRender(ad);
                result = true;
            } catch (Throwable t) {
                result = false;
            }
        }
        return result;
    }

    public void shutdownAppAfter(boolean shutdown) {
        shutdownAfter = shutdown;
    }

    public void dismissActivityAfterwards(boolean dismiss) {
        dismissAfter = dismiss;
    }

    @Override
    public void adDisplayed(AppLovinAd appLovinAd) {
        // Free the ad, load a new one.
        if (appLovinAd != null) {
            ad = null;

            if (Ref.alive(activityRef)) {
                Offers.THREAD_POOL.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            appLovinAdNetwork.loadNewInterstitial(activityRef.get());
                        } catch (Throwable e) {
                            LOG.error(e.getMessage(), e);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void adHidden(AppLovinAd appLovinAd) {
        if (Ref.alive(activityRef)) {
            Activity callerActivity = activityRef.get();

            if (dismissAfter) {
                callerActivity.finish();
            }
            if (shutdownAfter) {
                if (callerActivity instanceof MainActivity) {
                    ((MainActivity) callerActivity).shutdown();
                }
            }
        } else {
            if (shutdownAfter) {
                Intent i = new Intent(app, MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("shutdown-" + ConfigurationManager.instance().getUUIDString(), true);
                app.startActivity(i);
            }
        }
    }

    @Override
    public void adReceived(AppLovinAd appLovinAd) {
        if (appLovinAd != null) {
            ad = appLovinAd;
            isVideoAd = appLovinAd.isVideoAd();
        }
    }

    @Override
    public void failedToReceiveAd(int i) {
        LOG.warn("failed to receive ad (" + i + ")");
    }
}
