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
    private static final Logger LOG = Logger.getLogger(Offers.class);
    private boolean loaded;
    private boolean interstitialShowSuccess;
    private WeakReference<? extends Activity> activityRef;
    private InterstitialAd ad = null;
    private final Application app;
    private CountDownLatch showSuccessLatch;
    private boolean afterBehaviorConfigured;
    private boolean shutdownAfter;
    private boolean finishAfterDismiss;

    MobFoxInterstitialListener(Activity activity) {
        activityRef = Ref.weak(activity);
        app = activity.getApplication();
    }

    @Override
    public boolean isAdReadyToDisplay() {
        return ad != null && loaded;
    }

    @Override
    public boolean isVideoAd() {
        return false;
    }

    @Override
    public boolean show(WeakReference<? extends Activity> activityWeakReference) {

        if (ad != null && Ref.alive(activityWeakReference)) {
            try {
                this.activityRef = activityWeakReference;
                showSuccessLatch = new CountDownLatch(1);
                ad.show();
                showSuccessLatch.await();//2, TimeUnit.SECONDS);
                showSuccessLatch = null;
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
                ad = null;
                interstitialShowSuccess = false;
                afterBehaviorConfigured = false;
                loaded = false;
            }
        }
        LOG.info("show() -> success? " + interstitialShowSuccess);
        return interstitialShowSuccess;
    }

    @Override
    public void shutdownAppAfter(boolean shutdown) {
        shutdownAfter = shutdown;
        afterBehaviorConfigured = true;
    }

    @Override
    public void dismissActivityAfterwards(boolean dismiss) {
        finishAfterDismiss = dismiss;
        afterBehaviorConfigured = true;
    }

    @Override
    public void onInterstitialLoaded(InterstitialAd interstitialAd) {
        LOG.info("onInterstitialLoaded");
        loaded = true;
        ad = interstitialAd;
        ad.setListener(this);
        afterBehaviorConfigured = false;
    }

    @Override
    public void onInterstitialFailed(InterstitialAd interstitialAd, Exception e) {
        LOG.info("onInterstitialFailed");
        interstitialShowSuccess = false;
        if (showSuccessLatch != null && showSuccessLatch.getCount() > 0) {
            showSuccessLatch.countDown();
        }
        loaded = false;
        ad = null;
        // TODO: Reload logic.
    }

    @Override
    public void onInterstitialClosed(InterstitialAd interstitialAd) {
        LOG.info("onInterstitialClosed");
        Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(activityRef, finishAfterDismiss, shutdownAfter, true, app);
        loaded = false;
        afterBehaviorConfigured = false;
    }

    @Override
    public void onInterstitialFinished() {
        LOG.info("onInterstitialFinished");
        Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(activityRef, finishAfterDismiss, shutdownAfter, true, app);
        loaded = false;
        afterBehaviorConfigured = false;
    }

    @Override
    public void onInterstitialClicked(InterstitialAd interstitialAd) {
        loaded = false;
        LOG.info("onInterstitialClicked");
    }

    @Override
    public void onInterstitialShown(InterstitialAd interstitialAd) {
        interstitialShowSuccess = true;
        if (showSuccessLatch != null) {
            showSuccessLatch.countDown();
        }
        loaded = false;
    }

    boolean isAfterBehaviorConfigured() {
        return afterBehaviorConfigured;
    }
}
