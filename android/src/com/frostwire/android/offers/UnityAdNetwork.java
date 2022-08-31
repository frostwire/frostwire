/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2022, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.offers;

import static com.frostwire.android.util.Asyncs.async;

import android.app.Activity;

import com.frostwire.android.core.Constants;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;

import java.lang.ref.WeakReference;

public class UnityAdNetwork extends AbstractAdNetwork {
    private static final Logger LOG = Logger.getLogger(UnityAdNetwork.class);
    private static final String INTERSTITIAL_PLACEMENT_ID = "Interstitial_All";
    private static final String REWARDED_VIDEO_PLACEMENT_ID = "rewardedVideo";
    private UnityAdsListener unityAdsListener;

    @Override
    public void initialize(Activity activity) {
        if (shouldWeAbortInitialize(activity)) {
            return;
        }
        final String GAME_ID = "3351589";
        unityAdsListener = new UnityAdsListener(this);
        UnityAds.initialize(SystemUtils.getApplicationContext(), GAME_ID, isDebugOn(), unityAdsListener);
        start();
    }

    @Override
    public void enable(boolean enabled) {
        Offers.AdNetworkHelper.enable(this, enabled);
    }

    @Override
    public boolean enabled() {
        return Offers.AdNetworkHelper.enabled(this);
    }

    @Override
    public boolean showInterstitial(Activity activity, String placement, boolean shutdownAfterward, boolean dismissActivityAfterward) {
        if (started() && unityAdsListener.isInterstitialReady()) {
            LOG.info("UnityAdNetwork.showInterstitial(): about to show interstitial, shutdownActivityAfterward=" + shutdownAfterward + ", dismissActivityAfterward=" + dismissActivityAfterward);
            unityAdsListener.updateBehaviorAfterInterstitialClosed(activity, shutdownAfterward, dismissActivityAfterward);
            UnityAds.show(activity, INTERSTITIAL_PLACEMENT_ID, unityAdsListener);
            return true;
        }
        LOG.info("UnityAdNetwork.showInterstitial(): started=" + started() + ", interstitialReady=" + unityAdsListener.isInterstitialReady());
        return false;
    }

    @Override
    public void loadNewInterstitial(Activity activity) {
        if (started()) {
            LOG.info("UnityAdNetwork.loadNewInterstitial() loading " + INTERSTITIAL_PLACEMENT_ID);
            UnityAds.load(INTERSTITIAL_PLACEMENT_ID, unityAdsListener);
        }
    }

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_UNITY;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_UNITY;
    }

    @Override
    public boolean isDebugOn() {
        return Offers.DEBUG_MODE;
    }

    private static class UnityAdsListener implements
            IUnityAdsInitializationListener, // Unity Ads as a library initialization listener
            IUnityAdsLoadListener,  // Unity Interstitials Load Listener methods
            IUnityAdsShowListener // Interstitials Show Listener methods
    {
        WeakReference<Activity> lastActivity;
        WeakReference<UnityAdNetwork> adNetwork;
        private boolean interstitialReady = false;
        private boolean shutdownAfterward;
        private boolean dismissActivityAfterward;

        UnityAdsListener(UnityAdNetwork adNetwork) {
            this.adNetwork = Ref.weak(adNetwork);
        }

        boolean isInterstitialReady() {
            return interstitialReady;
        }

        // IUnityAdsInitializationListener methods

        @Override
        public void onInitializationComplete() {
            if (Ref.alive(adNetwork)) {
                adNetwork.get().start();
                interstitialReady = false;
            }
        }

        @Override
        public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
            if (error != null && message != null) {
                LOG.error("UnityAdNetwork::IUnityAdsListener::onUnityAdsError: " + message + " (" + error.name() + ")");
            }
            interstitialReady = false;
            adNetwork.get().stop(null);
        }

        // Unity Interstitials Load Listener methods (IUnityAdsLoadListener)

        @Override
        public void onUnityAdsAdLoaded(String placementId) {
            if (INTERSTITIAL_PLACEMENT_ID.equals(placementId)) {
                interstitialReady = true;
            }
        }

        @Override
        public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
            if (INTERSTITIAL_PLACEMENT_ID.equals(placementId)) {
                interstitialReady = false;
            }
        }

        // IUnityAdsShowListener Show Listener methods

        @Override
        public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {

        }

        @Override
        public void onUnityAdsShowStart(String placementId) {

        }

        @Override
        public void onUnityAdsShowClick(String placementId) {

        }

        @Override
        public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
            if (REWARDED_VIDEO_PLACEMENT_ID.equals(placementId) && state.equals(UnityAds.UnityAdsShowCompletionState.COMPLETED)) {
                LOG.info("UnityAdNetwork.onUnityAdsShowComplete() placementId = " + placementId);
                async(Offers::pauseAdsAsync, Constants.MIN_REWARD_AD_FREE_MINUTES);
                if (Ref.alive(lastActivity)) {
                    lastActivity.get().finish();
                }
            } else if (INTERSTITIAL_PLACEMENT_ID.equals(placementId)) {
                // Interstitial can be SKIPPED or COMPLETED (state)
                LOG.info("UnityAdNetwork.onUnityAdsShowComplete() " + INTERSTITIAL_PLACEMENT_ID + " " + state.name());
                if (Ref.alive(lastActivity)) {
                    LOG.info("UnityAdNetwork.onUnityAdsShowComplete() " + INTERSTITIAL_PLACEMENT_ID + " closed. Last Activity reference still here, calling Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(...)");
                    Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(
                            lastActivity.get(),
                            dismissActivityAfterward,
                            shutdownAfterward,
                            true,
                            lastActivity.get().getApplication());
                    if (!shutdownAfterward && !dismissActivityAfterward && Ref.alive(adNetwork)) {
                        LOG.info("UnityAdNetwork.onUnityAdsShowComplete() loading another new interstitial");
                        adNetwork.get().loadNewInterstitial(lastActivity.get());
                    }
                }
            }
        }

        void updateBehaviorAfterInterstitialClosed(Activity activity, boolean shutdownAfterward, boolean dismissActivityAfterward) {
            lastActivity = Ref.weak(activity);
            this.shutdownAfterward = shutdownAfterward;
            this.dismissActivityAfterward = dismissActivityAfterward;
        }
    }
}