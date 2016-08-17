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
import java.util.concurrent.TimeUnit;

/**
 * Created on 8/9/16.
 * @author gubatron
 * @author aldenml
 */
final class MobFoxInterstitialListener implements InterstitialListener, InterstitialAdListener {
    private static final Logger LOG = Logger.getLogger(MobFoxInterstitialListener.class);
    private boolean loaded;
    private boolean interstitialShowSuccess;
    private WeakReference<? extends Activity> activityRef;
    private InterstitialAd ad = null;
    private final MobFoxAdNetwork adNetwork;
    private final Application app;
    private CountDownLatch showSuccessLatch;
    private boolean afterBehaviorConfigured;
    private boolean shutdownAfter;
    private boolean finishAfterDismiss;

    MobFoxInterstitialListener(MobFoxAdNetwork adNetwork, Application application) {
        this.adNetwork = adNetwork;
        app = application;
        reset();
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
    public boolean show(Activity activity) {

        if (ad != null && activity != null) {
            try {
                this.activityRef = Ref.weak(activity);
                showSuccessLatch = new CountDownLatch(1);
                ad.show();
                showSuccessLatch.await(3, TimeUnit.SECONDS);
                showSuccessLatch = null;
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
                reset();
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
        reset();
        loaded = true;
        ad = interstitialAd;
        ad.setListener(this);
        adNetwork.resetReloadTasks();
    }

    @Override
    public void onInterstitialClosed(InterstitialAd interstitialAd) {
        LOG.info("onInterstitialClosed -> wrapItUp() MobFoxInterstitialListener@" + hashCode() + " on ad@" + interstitialAd.hashCode() +  "(==@"+ad.hashCode());
        wrapItUp();
    }

    @Override
    public void onInterstitialFinished() {
        // This method never gets invoked
        // LOG.info("onInterstitialFinished -> wrapItUp() MobFoxInterstitialListener@" + hashCode());
        // wrapItUp();
    }

    @Override
    public void onInterstitialClicked(InterstitialAd interstitialAd) {
        LOG.info("onInterstitialClicked MobFoxInterstitialListener@" + hashCode());
        reset();
    }

    @Override
    public void onInterstitialShown(InterstitialAd interstitialAd) {
        LOG.info("onInterstitialShown MobFoxInterstitialListener@" + hashCode());
        reset(false); // keep the ad reference
        interstitialShowSuccess = true;
        if (showSuccessLatch != null) {
            showSuccessLatch.countDown();
        }
    }

    @Override
    public void onInterstitialFailed(InterstitialAd interstitialAd, Exception e) {
        LOG.info("onInterstitialFailed MobFoxInterstitialListener@" + hashCode());
        reset(); // this does interstitialShowSuccess = false;
        if (showSuccessLatch != null && showSuccessLatch.getCount() > 0) {
            showSuccessLatch.countDown();
        }
        if (!shutdownAfter && Ref.alive(activityRef)) {
            adNetwork.reloadInterstitial(activityRef.get());
        }
    }

    boolean isAfterBehaviorConfigured() {
        return afterBehaviorConfigured;
    }

    private void reset() {
        reset(true);
    }

    private void reset(boolean resetAd) {
        if (resetAd) {
            ad = null;
        }
        loaded = false;
        afterBehaviorConfigured = false;
        interstitialShowSuccess = false;
    }

    private void wrapItUp() {
        reset();
        Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(activityRef, finishAfterDismiss, shutdownAfter, !shutdownAfter, app);
        if (!shutdownAfter && Ref.alive(activityRef)) {
            adNetwork.reloadInterstitial(activityRef.get());
        }
    }
}
