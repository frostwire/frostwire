/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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
import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.ProductPaymentOptionsView;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.ThreadPool;
import com.mopub.mobileads.MoPubRewardedVideos;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.frostwire.android.util.Asyncs.async;

public final class Offers {
    private static final Logger LOG = Logger.getLogger(Offers.class);

    static final boolean DEBUG_MODE = false;
    static final ThreadPool THREAD_POOL = new ThreadPool("Offers", 1, 5, 1L, new LinkedBlockingQueue<>(), true);
    public static final String PLACEMENT_INTERSTITIAL_MAIN = "interstitial_main";
    private static Map<String, AdNetwork> AD_NETWORKS;
    public final static MoPubAdNetwork MOPUB = new MoPubAdNetwork();
    private final static AppLovinAdNetwork APP_LOVIN = AppLovinAdNetwork.getInstance();
    private final static RemoveAdsNetwork REMOVE_ADS = new RemoveAdsNetwork();
    private final static Long STARTUP_TIME = System.currentTimeMillis();
    private static long lastInitAdnetworksInvocationTimestamp = 0;
    private static boolean FORCED_DISABLED = false;
    private static boolean PAUSED;
    private static final ReentrantLock pausedCheckLock = new ReentrantLock();

    private Offers() {
    }

    public static void initAdNetworks(Activity activity) {
        if (FORCED_DISABLED) {
            LOG.info("Offers.initAdNetworks() aborted, FORCED_DISABLED");
            return;
        }
        if (stopAdNetworksIfPurchasedRemoveAds(activity)) {
            LOG.info("Offers.initAdNetworks() aborted, user paid for ad removal.");
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastInitAdnetworksInvocationTimestamp) < 5000) {
            LOG.info("Offers.initAdNetworks() aborted, too soon to reinitialize networks.");
            return;
        }
        lastInitAdnetworksInvocationTimestamp = now;
        for (AdNetwork adNetwork : getActiveAdNetworks()) {
            if (adNetwork != null) { // because of a typo on config file this can happen
                try {
                    adNetwork.initialize(activity);
                } catch (Throwable t) {
                    LOG.warn("initAdNetworks() " + adNetwork.getClass().getSimpleName() + " initialization failed", t);
                }
            }
        }
        LOG.info("Offers.initAdNetworks() success");
        async(Offers::checkIfPausedAsync);
    }

    private static Map<String, AdNetwork> getAllAdNetworks() {
        if (AD_NETWORKS == null) {
            AD_NETWORKS = new HashMap<>();
            AD_NETWORKS.put(MOPUB.getShortCode(), MOPUB);
            AD_NETWORKS.put(APP_LOVIN.getShortCode(), APP_LOVIN);
            AD_NETWORKS.put(REMOVE_ADS.getShortCode(), REMOVE_ADS);
        }
        return AD_NETWORKS;
    }

    public static void destroyMopubInterstitials() {
        MOPUB.destroyInterstitials();
    }

    public static void stopAdNetworks(Context context) {
        for (AdNetwork adNetwork : getActiveAdNetworks()) {
            if (adNetwork != null && adNetwork.started()) {
                try {
                    adNetwork.stop(context);
                } catch (Throwable ignored) {
                }
            }
        }
        LOG.info("Ad networks stopped");
    }

    /**
     * DO NOT CALL THIS DIRECTLY, DO SO ONLY FOR TESTING OR IF YOU REALLY
     * KNOW WHAT YOU ARE DOING. This method runs on the UI Thread entirely.
     * <p>
     * You should be calling instead Offers.showInterstitialOfferIfNecessary
     * it will perform all the logic checks necessary in the background and then invoke
     * showInterstitial() if the time is right.
     * <p>
     * Why is it `public` then?
     * This method has been kept public since we use it as an easter egg
     * when touching the SearchFragment title 5 times to trigger an interstitial
     * on demand, no questions asked
     */
    public static void showInterstitial(final Activity activity,
                                        String placement,
                                        final boolean shutdownAfterwards,
                                        final boolean dismissAfterwards) {
        if (Offers.disabledAds()) {
            LOG.info("Skipping interstitial ads display, Offers have been disabled");
        } else {
            for (AdNetwork adNetwork : getActiveAdNetworks()) {
                if (adNetwork != null && adNetwork.started()) {
                    LOG.info("showInterstitial: AdNetwork " + adNetwork.getClass().getSimpleName() + " started? " + adNetwork.started());
                    boolean interstitialShown = adNetwork.showInterstitial(activity, placement, shutdownAfterwards, dismissAfterwards);
                    if (interstitialShown) {
                        ConfigurationManager.instance().setLong(Constants.PREF_KEY_GUI_INTERSTITIAL_LAST_DISPLAY, System.currentTimeMillis());
                        LOG.info("showInterstitial: " + adNetwork.getClass().getSimpleName() + " interstitial shown");
                        return;
                    } else {
                        LOG.info("showInterstitial: " + adNetwork.getClass().getSimpleName() + " interstitial NOT shown");
                    }
                }
            }
        }
        if (dismissAfterwards) {
            activity.finish();
        }
        if (shutdownAfterwards) {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).shutdown();
            } else {
                UIUtils.sendShutdownIntent(activity);
            }
        }
        // otherwise it's up to the interstitial and its listener to dismiss or shutdown if necessary.
    }

    public static void showInterstitialOfferIfNecessary(Activity ctx, String placement,
                                                        final boolean shutdownAfterwards,
                                                        final boolean dismissAfterwards) {
        showInterstitialOfferIfNecessary(ctx, placement, shutdownAfterwards, dismissAfterwards, false);
    }

    static void pauseAdsAsync(int minutes) {
        LOG.info("pauseAdsAsync: pausing for " + minutes + " minutes");
        pausedCheckLock.lock();
        ConfigurationManager CM = ConfigurationManager.instance();
        CM.setInt(Constants.FW_REWARDED_VIDEO_MINUTES, minutes);
        CM.setLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP, System.currentTimeMillis());
        PAUSED = true;
        pausedCheckLock.unlock();
    }

    private static void unPauseAdsAsync() {
        pausedCheckLock.lock();
        ConfigurationManager CM = ConfigurationManager.instance();
        CM.setInt(Constants.FW_REWARDED_VIDEO_MINUTES, -1);
        CM.setLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP, -1);
        PAUSED = false;
        pausedCheckLock.unlock();
    }

    public static int getMinutesLeftPausedAsync() {
        pausedCheckLock.lock();
        ConfigurationManager CM = ConfigurationManager.instance();
        int rewarded_video_minutes = CM.getInt(Constants.FW_REWARDED_VIDEO_MINUTES, -1);
        if (rewarded_video_minutes == -1) {
            pausedCheckLock.unlock();
            return -1;
        }
        long pause_duration = rewarded_video_minutes * 60_000;
        long paused_timestamp = CM.getLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP);
        pausedCheckLock.unlock();
        if (paused_timestamp == -1) {
            return -1;
        }

        long time_on_pause = System.currentTimeMillis() - paused_timestamp;
        if (time_on_pause > pause_duration) {
            return 0;
        }

        return (int) ((pause_duration - time_on_pause) / 60_000);
    }

    private static void checkIfPausedAsync() {
        pausedCheckLock.lock();
        ConfigurationManager CM = ConfigurationManager.instance();
        int rewarded_video_minutes = CM.getInt(Constants.FW_REWARDED_VIDEO_MINUTES, -1);
        if (rewarded_video_minutes == -1) {
            PAUSED = false;
            pausedCheckLock.unlock();
            return;
        }
        long pause_duration = rewarded_video_minutes * 60_000;
        long paused_timestamp = CM.getLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP);
        if (paused_timestamp == -1) {
            PAUSED = false;
            pausedCheckLock.unlock();
            return;
        }

        long time_on_pause = System.currentTimeMillis() - paused_timestamp;
        PAUSED = time_on_pause < pause_duration;

        if (!PAUSED) {
            //LOG.info("checkIfPausedAsync: UnPausing Offers, Reward has expired");
            CM.setInt(Constants.FW_REWARDED_VIDEO_MINUTES, -1);
            CM.setLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP, -1);
            pausedCheckLock.unlock();
        } else {
            pausedCheckLock.unlock();
            int minutes_left = (int) ((pause_duration - time_on_pause) / 60_000);
            //LOG.info("checkIfPausedAsync: PAUSED (" + minutes_left + " minutes left)");
        }
    }

    private static class InterstitialLogicParams {
        final String placement;
        final boolean shutdownAfterwards;
        final boolean dismissAfterwards;
        final boolean ignoreStartedTransfers;

        InterstitialLogicParams(String p, boolean s, boolean d, boolean i) {
            placement = p;
            shutdownAfterwards = s;
            dismissAfterwards = d;
            ignoreStartedTransfers = i;
        }
    }

    public static void showRewardedVideo(BuyActivity activity) {
        if (MoPubRewardedVideos.hasRewardedVideo(MoPubAdNetwork.UNIT_ID_REWARDED_VIDEO)) {
            MoPubRewardedVideos.showRewardedVideo(MoPubAdNetwork.UNIT_ID_REWARDED_VIDEO);
            activity.finish();
            // maybe then exit the invoking BuyActivity, which should then be passed here.
        } else {
            UIUtils.showShortMessage(activity, R.string.looking_For_rewarded_video);
            async(Offers::keepTryingRewardedVideoAsync, Ref.weak(activity));
        }
    }

    private static void keepTryingRewardedVideoAsync(WeakReference<BuyActivity> activityRef) {
        if (!Ref.alive(activityRef)) {
            return;
        }
        int attempts = 5;
        while (attempts > 0 && Ref.alive(activityRef) && !MoPubRewardedVideos.hasRewardedVideo(MoPubAdNetwork.UNIT_ID_REWARDED_VIDEO)) {
            try {
                LOG.info("keepTryingRewardedVideoAsync: sleeping while ad loads... (attempts=" + attempts + ")");
                Thread.sleep(2500);
                if (Ref.alive(activityRef)) {
                    activityRef.get().runOnUiThread(() -> MoPubRewardedVideos.loadRewardedVideo(MoPubAdNetwork.UNIT_ID_REWARDED_VIDEO));
                }
                Thread.sleep(2500);
                attempts--;
            } catch (InterruptedException e) {
                return;
            }
        }
        if (!MoPubRewardedVideos.hasRewardedVideo(MoPubAdNetwork.UNIT_ID_REWARDED_VIDEO)) {
            async(Offers::unPauseAdsAsync);
            if (!Ref.alive(activityRef)) {
                return;
            }
            activityRef.get().runOnUiThread(() -> {
                if (!Ref.alive(activityRef)) {
                    return;
                }
                activityRef.get().stopProgressbars(ProductPaymentOptionsView.PayButtonType.REWARD_VIDEO);
                UIUtils.showShortMessage(activityRef.get(), R.string.no_reward_videos_available);
                // hopefully the existing listener, set in MoPubAdNetwork's initialization via MoPubAdNetwork::loadRewardedVideo works.
                MoPubRewardedVideos.loadRewardedVideo(MoPubAdNetwork.UNIT_ID_REWARDED_VIDEO);
                LOG.info("keepTryingRewardedVideoAsync() invoked MoPubRewardedVideos.loadRewardedVideo() once more");
            });
        } else if (Ref.alive(activityRef)) {
            activityRef.get().runOnUiThread(() -> {
                if (!Ref.alive(activityRef)) {
                    return;
                }
                MoPubRewardedVideos.showRewardedVideo(MoPubAdNetwork.UNIT_ID_REWARDED_VIDEO);
                activityRef.get().finish();
            });
        }
    }

    public static void showInterstitialOfferIfNecessary(Activity activity, String placement,
                                                        final boolean shutdownAfterwards,
                                                        final boolean dismissAfterwards,
                                                        final boolean ignoreStartedTransfers) {
        InterstitialLogicParams params = new InterstitialLogicParams(placement, shutdownAfterwards, dismissAfterwards, ignoreStartedTransfers);
        async(
                Offers::readyForAnotherInterstitialAsync, activity, params, // returns true if ready
                Offers::onReadyForAnotherInterstitialAsyncCallback); // shows offers on main thread if ready received
    }

    private static boolean readyForAnotherInterstitialAsync(@SuppressWarnings("unused") Activity activity, InterstitialLogicParams params) {
        ConfigurationManager CM = ConfigurationManager.instance();
        final int INTERSTITIAL_FIRST_DISPLAY_DELAY_IN_MINUTES = DEBUG_MODE ? 0 : CM.getInt(Constants.PREF_KEY_GUI_INTERSTITIAL_FIRST_DISPLAY_DELAY_IN_MINUTES);
        final long INTERSTITIAL_FIRST_DISPLAY_DELAY_IN_MS = TimeUnit.MINUTES.toMillis(INTERSTITIAL_FIRST_DISPLAY_DELAY_IN_MINUTES);
        boolean appStartedLongEnoughAgo = (System.currentTimeMillis() - Offers.STARTUP_TIME) > INTERSTITIAL_FIRST_DISPLAY_DELAY_IN_MS;
        if (!appStartedLongEnoughAgo || Offers.disabledAds()) {
            return false;
        }
        final boolean ignoreStartedTransfers = params.ignoreStartedTransfers;
        TransferManager TM = TransferManager.instance();
        final int INTERSTITIAL_OFFERS_TRANSFER_STARTS = DEBUG_MODE ? 1 : CM.getInt(Constants.PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS);
        final int INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES = CM.getInt(Constants.PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES);
        final long INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MS = DEBUG_MODE ? 10000 : TimeUnit.MINUTES.toMillis(INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES);
        long lastInterstitialShownTimestamp = CM.getLong(Constants.PREF_KEY_GUI_INTERSTITIAL_LAST_DISPLAY);
        long timeSinceLastOffer = System.currentTimeMillis() - lastInterstitialShownTimestamp;
        boolean itsBeenLongEnough = timeSinceLastOffer >= INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MS;
        boolean startedEnoughTransfers = ignoreStartedTransfers || TM.startedTransfers() >= INTERSTITIAL_OFFERS_TRANSFER_STARTS;
        boolean shouldDisplayFirstOne = lastInterstitialShownTimestamp == -1 && startedEnoughTransfers;
        boolean readyForInterstitial = shouldDisplayFirstOne || (itsBeenLongEnough && startedEnoughTransfers);
        if (readyForInterstitial && !ignoreStartedTransfers) {
            TM.resetStartedTransfers();
        }
        return readyForInterstitial;
    }

    private static void onReadyForAnotherInterstitialAsyncCallback(Activity activity, InterstitialLogicParams params, boolean readyForInterstitial) {
        if (readyForInterstitial) {
            Offers.showInterstitial(activity, params.placement, params.shutdownAfterwards, params.dismissAfterwards);
        }
    }

    public static boolean disabledAds() {
        if (PAUSED) {
            async(Offers::checkIfPausedAsync);
            return true;
        }
        PlayStore store;
        try {
            // try with a null context
            //noinspection ConstantConditions
            store = PlayStore.getInstance(null);
        } catch (Throwable e) {
            store = null;
            LOG.error(e.getMessage(), e);
        }
        return FORCED_DISABLED || (store != null && Products.disabledAds(store));
    }

    /**
     * Used for hard coded tests only
     */
    @SuppressWarnings("unused")
    public static void forceDisabledAds(Context context) {
        FORCED_DISABLED = true;
        if (lastInitAdnetworksInvocationTimestamp != 0) {
            stopAdNetworks(context);
        }
    }

    /**
     * @return true only for flavor basic or for plus_debug and we haven't paid for ad removals, or
     * it's plus supporting FrostWire with ads.
     * If it's been less than 2 seconds since the app started, we always return false as there's not
     * enough time to check if user has purchased anything
     */
    public static boolean removeAdsOffersEnabled() {
        long now = System.currentTimeMillis();
        if (now - Offers.STARTUP_TIME < 2000) {
            // not enough time to ask if user has bought products, better to avoid false positives for users that paid.
            return false;
        }
        // Coded so explicitly for clarity.
        boolean isBasic = Constants.IS_GOOGLE_PLAY_DISTRIBUTION;
        boolean isDevelopment = Constants.IS_BASIC_AND_DEBUG;
        boolean playStoreAvailable = PlayStore.available();
        boolean notDisabledAds = !Offers.disabledAds();
        return (isBasic || isDevelopment || playStoreAvailable) && notDisabledAds;
    }

    private static void tryBackToBackInterstitial(Activity activity) {
        if (!REMOVE_ADS.enabled() || !REMOVE_ADS.started()) {
            return;
        }
        if (UIUtils.diceRollPassesThreshold(ConfigurationManager.instance(), Constants.PREF_KEY_GUI_REMOVEADS_BACK_TO_BACK_THRESHOLD)) {
            REMOVE_ADS.showInterstitial(activity, null, false, false);
        }
    }

    private static boolean stopAdNetworksIfPurchasedRemoveAds(Context context) {
        boolean stopped = false;
        PlayStore playStore = PlayStore.getInstance(context);
        final Collection<Product> purchasedProducts = Products.listEnabled(playStore, Products.DISABLE_ADS_FEATURE);
        if (purchasedProducts != null && purchasedProducts.size() > 0) {
            Offers.stopAdNetworks(context);
            stopped = true;
            LOG.info("Turning off ads, user previously purchased AdRemoval");
        }
        return stopped;
    }

    /**
     * Also checks the preference values under Constants.PREF_KEY_GUI_OFFERS_WATERFALL and deactivates
     * the networks that have not been specified there.
     *
     * @return The Array of Active AdNetworks.
     */
    private static AdNetwork[] getActiveAdNetworks() {
        final ConfigurationManager CM = ConfigurationManager.instance();
        final Map<String, AdNetwork> allAdNetworks = getAllAdNetworks();
        final String[] waterfallShortcodes = CM.getStringArray(Constants.PREF_KEY_GUI_OFFERS_WATERFALL);
        if (waterfallShortcodes == null) {
            return new AdNetwork[]{};
        }
        final List<AdNetwork> activeAdNetworksList = new ArrayList<>(waterfallShortcodes.length);
        for (String shortCode : waterfallShortcodes) {
            if (allAdNetworks.containsKey(shortCode)) {
                final AdNetwork adNetwork = allAdNetworks.get(shortCode);
                if (adNetwork != null) {
                    adNetwork.enable(true);
                    activeAdNetworksList.add(adNetwork);
                }
            } else {
                LOG.warn("unknown ad network shortcode '" + shortCode + "'");
            }
        }
        // turn off all the networks not on activeAdNetworks if any.
        for (String shortCode : allAdNetworks.keySet()) {
            int shortCodeOffsetInWaterfall = getKeyOffset(shortCode, waterfallShortcodes);
            boolean networkInUse = shortCodeOffsetInWaterfall != -1;
            AdNetwork adNetwork = allAdNetworks.get(shortCode);
            // can be null if there's a typo or it's a new network unknown to this client
            if (adNetwork != null && !networkInUse) {
                adNetwork.enable(false);
            }
        }
        return activeAdNetworksList.toArray(new AdNetwork[0]);
    }

    private static int getKeyOffset(String key, String[] keys) {
        int i = 0;
        for (String k : keys) {
            if (k.equals(key)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public static class AdNetworkHelper {
        /**
         * Is the network enabled in the configuration?
         */
        public static boolean enabled(AdNetwork network) {
            if (network.isDebugOn()) {
                return true;
            }
            ConfigurationManager config;
            boolean enabled = false;
            try {
                boolean adsDisabled = Offers.disabledAds();
                config = ConfigurationManager.instance();
                enabled = config.getBoolean(network.getInUsePreferenceKey()) && !adsDisabled;
            } catch (Throwable e) {
                LOG.error(e.getMessage(), e);
            }
            return enabled;
        }

        /**
         * Mark the network enabled or disabled in the configuration
         */
        public static void enable(AdNetwork network, boolean enabled) {
            ConfigurationManager config = ConfigurationManager.instance();
            try {
                config.setBoolean(network.getInUsePreferenceKey(), enabled);
            } catch (Throwable e) {
                LOG.error(e.getMessage(), e);
            }
        }

        public static void dismissAndOrShutdownIfNecessary(final Activity activity,
                                                           final boolean finishAfterDismiss,
                                                           final boolean shutdownAfter,
                                                           final boolean tryBack2BackRemoveAdsOffer,
                                                           final Application fallbackContext) {
            LOG.info("dismissAndOrShutdownIfNecessary(finishAfterDismiss=" + finishAfterDismiss + ", shutdownAfter=" + shutdownAfter + ", tryBack2BackRemoveAdsOffer= " + tryBack2BackRemoveAdsOffer + ")");
            Engine.instance().hapticFeedback();
            // dismissAndOrShutdownIfNecessary is invoked on adNetwork listeners when interstitials are dismissed
            // if a user leaves an ad displayed without interacting with it for too long a second
            // ad could be displayed on MainActivity's onResume back to back.
            ConfigurationManager.instance().setLong(Constants.PREF_KEY_GUI_INTERSTITIAL_LAST_DISPLAY, System.currentTimeMillis());
            if (activity != null) {
                if (shutdownAfter) {
                    if (activity instanceof MainActivity) {
                        LOG.info("dismissAndOrShutdownIfNecessary: MainActivity.shutdown()");
                        ((MainActivity) activity).shutdown();
                    } else {
                        LOG.info("dismissAndOrShutdownIfNecessary: UIUtils.sendShutdownIntent(callerActivity)");
                        UIUtils.sendShutdownIntent(activity);
                    }
                    return;
                }
                if (finishAfterDismiss) {
                    if (activity instanceof MainActivity) {
                        activity.finish();
                    } else {
                        activity.finish();
                        UIUtils.sendGoHomeIntent(activity);
                    }
                }
                if (!finishAfterDismiss && tryBack2BackRemoveAdsOffer) {
                    LOG.info("dismissAndOrShutdownIfNecessary: Offers.tryBackToBackInterstitial(activityRef);");
                    Offers.tryBackToBackInterstitial(activity);
                }
            } else {
                if (shutdownAfter && fallbackContext != null) {
                    LOG.info("dismissAndOrShutdownIfNecessary: shutdown() [no activity ref]");
                    UIUtils.sendShutdownIntent(fallbackContext);
                }
            }
        }
    }
}
