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
import android.app.Application;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.mobfox.sdk.interstitialads.InterstitialAd;
import com.mobfox.sdk.interstitialads.InterstitialAdListener;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;

/**
 * Created on 8/9/16.
 * @author gubatron
 * @author aldenml
 */
final class MobFoxInterstitialListener implements InterstitialListener, InterstitialAdListener {
    private static final Logger LOG = Logger.getLogger(MobFoxInterstitialListener.class);
    private boolean ready;
    private boolean interstitialShowSuccess;
    private WeakReference<? extends Activity> activityRef;
    private InterstitialAd ad = null;
    private final Application app;
    private CountDownLatch showSuccessLatch;
    private boolean shutdownAfter;
    private boolean finishAfterDismiss;

    MobFoxInterstitialListener(Activity activity) {
        activityRef = Ref.weak(activity);
        app = activity.getApplication();
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
    public boolean show(WeakReference<? extends Activity> activityWeakReference) {
        boolean result = false;
        if (ad != null && Ref.alive(activityWeakReference)) {
            try {
                this.activityRef = activityWeakReference;

                showSuccessLatch = new CountDownLatch(1);
                ad.show();
                // TODO: fine tune this maximum wait to the minimum possible.
                showSuccessLatch.wait(2000);
                result = interstitialShowSuccess;
            } catch (Throwable t) {
                result = false;
            }
        }
        return result;
    }

    @Override
    public void shutdownAppAfter(boolean shutdown) {
        shutdownAfter = shutdown;
    }

    @Override
    public void dismissActivityAfterwards(boolean dismiss) {
        finishAfterDismiss = dismiss;
    }

    @Override
    public void onInterstitialLoaded(InterstitialAd interstitialAd) {
        LOG.info("onInterstitialLoaded");
        ready = true;
        interstitialShowSuccess = true;
        ad = interstitialAd;
    }

    @Override
    public void onInterstitialFailed(InterstitialAd interstitialAd, Exception e) {
        LOG.info("onInterstitialFailed");
        interstitialShowSuccess = false;
        if (showSuccessLatch != null && showSuccessLatch.getCount() > 0) {
            showSuccessLatch.countDown();
        }
        ready = false;
        ad = null;
        // TODO: Reload logic.
    }

    @Override
    public void onInterstitialClosed(InterstitialAd interstitialAd) {
        LOG.info("onInterstitialClosed");
        ready = false;
        Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(activityRef, finishAfterDismiss, shutdownAfter, true, app);
        // TODO: Reload logic.
    }

    @Override
    public void onInterstitialFinished() {
        LOG.info("onInterstitialFinished");
        ready = false;
        //TODO: decide to have this onInterstitialClosed or here.
        //Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(activityRef, finishAfterDismiss, shutdownAfter, true, app);
    }

    @Override
    public void onInterstitialClicked(InterstitialAd interstitialAd) {
        ready = false;
        LOG.info("onInterstitialClicked");
    }

    @Override
    public void onInterstitialShown(InterstitialAd interstitialAd) {
        interstitialShowSuccess = true;
        if (showSuccessLatch != null) {
            showSuccessLatch.countDown();
        }
        ready = false;
    }
}
