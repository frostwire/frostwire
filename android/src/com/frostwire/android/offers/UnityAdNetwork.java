package com.frostwire.android.offers;

import android.app.Activity;

import com.frostwire.android.core.Constants;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.unity3d.ads.IUnityAdsListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.UnityAds;

import java.lang.ref.WeakReference;

import static com.frostwire.android.util.Asyncs.async;

public class UnityAdNetwork extends AbstractAdNetwork {
    private static final Logger LOG = Logger.getLogger(UnityAdNetwork.class);
    private static final String INTERSTITIAL_PLACEMENT_ID = "Interstitial_All";
    private UnityAdsListener unityAdsListener;

    @Override
    public void initialize(Activity activity) {
        if (abortInitialize(activity)) {
            return;
        }
        final String GAME_ID = "3351589";
        unityAdsListener = new UnityAdsListener(this);
        UnityAds.addListener(unityAdsListener);
        UnityAds.initialize(activity.getApplicationContext(), GAME_ID, isDebugOn());
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
            unityAdsListener.behaviorAfterInterstitialClosed(activity, shutdownAfterward, dismissActivityAfterward);
            //TODO: Stop OVERRIDING THE PLACEMENT ID, interstitial_main (From Offers.PLACEMENT_INTERSTITIAL_MAIN) does not exist in Unity now
            UnityAds.show(activity, INTERSTITIAL_PLACEMENT_ID);
            return true;
        }
        LOG.info("UnityAdNetwork.showInterstitial(): started=" + started() + ", interstitialReady=" + unityAdsListener.isInterstitialReady());
        return false;
    }

    @Override
    public void loadNewInterstitial(Activity activity) {
        if (started()) {
            LOG.info("UnityAdNetwork.loadNewInterstitial() loading " + INTERSTITIAL_PLACEMENT_ID);
            UnityAds.load(INTERSTITIAL_PLACEMENT_ID, new IUnityAdsLoadListener() {
                @Override
                public void onUnityAdsAdLoaded(String placementId) {
                    unityAdsListener.onUnityAdsReady(placementId);
                }

                @Override
                public void onUnityAdsFailedToLoad(String placementId) {
                    unityAdsListener.onUnityAdsFailedToLoad();
                }
            });
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

    private static class UnityAdsListener implements IUnityAdsListener {
        WeakReference<Activity> lastActivity;
        WeakReference<UnityAdNetwork> adNetwork;
        private boolean interstitialReady = false;
        private boolean shutdownAfterward;
        private boolean dismissActivityAfterward;
        private boolean dismissBuyActivityAfterReward;

        UnityAdsListener(UnityAdNetwork adNetwork) {
            this.adNetwork = Ref.weak(adNetwork);
        }

        boolean isInterstitialReady() {
            return interstitialReady;
        }

        public void onUnityAdsFailedToLoad() {
            interstitialReady = false;
        }

        @Override
        public void onUnityAdsReady(String placementId) {
            LOG.info("UnityAdNetwork.onUnityAdsReady() " + placementId + " ready!");
            if (INTERSTITIAL_PLACEMENT_ID.equals(placementId)) {
                LOG.info("UnityAdNetwork.onUnityAdsReady() interstitial ready");
                interstitialReady = true;
            }
        }

        @Override
        public void onUnityAdsStart(String placementId) {
            if (Ref.alive(adNetwork)) {
                LOG.info("UnityAdNetwork.onUnityAdsStart() placementId=" + placementId);
                adNetwork.get().start();
            }
        }

        @Override
        public void onUnityAdsFinish(String placementId, UnityAds.FinishState result) {
            // Called when interstitial is closed. Result can be ERRORED, SKIPPED, or COMPLETED,
            // we'll just use the expected activity behavior (boolean shutdownActivityAfterward, boolean dismissActivityAfterward)
            if (INTERSTITIAL_PLACEMENT_ID.equals(placementId)) {
                LOG.info("UnityAdNetwork.onUnityAdsFinish() " + INTERSTITIAL_PLACEMENT_ID + " closed");
                if (Ref.alive(lastActivity)) {
                    LOG.info("UnityAdNetwork.onUnityAdsFinish() " + INTERSTITIAL_PLACEMENT_ID + " closed. Last Activity reference still here, calling Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(...)");
                    Offers.AdNetworkHelper.dismissAndOrShutdownIfNecessary(
                            lastActivity.get(),
                            dismissActivityAfterward,
                            shutdownAfterward,
                            true,
                            lastActivity.get().getApplication());
                    if (!shutdownAfterward && !dismissActivityAfterward && Ref.alive(adNetwork)) {
                        LOG.info("UnityAdNetwork.onUnityAdsFinish() loading another new interstitial");
                        adNetwork.get().loadNewInterstitial(lastActivity.get());
                    }
                }
            } else if ("rewardedVideo".equals(placementId)) {
                LOG.info("UnityAdNetwork.onUnityAdsFinish() placementId = " + placementId);
                if (result == UnityAds.FinishState.COMPLETED) {
                    dismissBuyActivityAfterReward = true;
                    async(Offers::pauseAdsAsync, Constants.MIN_REWARD_AD_FREE_MINUTES);
                }
            }
        }

        @Override
        public void onUnityAdsError(UnityAds.UnityAdsError error, String message) {
            if (error != null && message != null) {
                LOG.error("UnityAdNetwork::IUnityAdsListener::onUnityAdsError: " + message + " (" + error.name() + ")");
            }
            if (error == UnityAds.UnityAdsError.NOT_INITIALIZED ||
                    error == UnityAds.UnityAdsError.INITIALIZE_FAILED ||
                    error == UnityAds.UnityAdsError.INIT_SANITY_CHECK_FAIL) {
                interstitialReady = false;

                if ((error == UnityAds.UnityAdsError.INITIALIZE_FAILED ||
                        error == UnityAds.UnityAdsError.INIT_SANITY_CHECK_FAIL) &&
                        Ref.alive(adNetwork)) {
                    adNetwork.get().stop(null);
                }
            }
        }

        void behaviorAfterInterstitialClosed(Activity activity, boolean shutdownAfterward, boolean dismissActivityAfterward) {
            lastActivity = Ref.weak(activity);
            this.shutdownAfterward = shutdownAfterward;
            this.dismissActivityAfterward = dismissActivityAfterward;
            if (dismissBuyActivityAfterReward && activity != null) {
                dismissBuyActivityAfterReward = false;
                activity.finish();
            }
        }
    }
}