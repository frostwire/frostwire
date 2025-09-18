/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.offers;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import com.applovin.mediation.ads.MaxRewardedAd;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.ProductPaymentOptionsView;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class Offers {
    private static final Logger LOG = Logger.getLogger(Offers.class);

    /**
     * Turn this on to enable Test Ad Units
     */
    static final boolean DEBUG_MODE = false;

    public static final String PLACEMENT_INTERSTITIAL_MAIN = "interstitial_main";
    private static Map<String, AdNetwork> AD_NETWORKS;
    private final static AppLovinAdNetwork APP_LOVIN = AppLovinAdNetwork.getInstance();
    private final static RemoveAdsNetwork REMOVE_ADS = new RemoveAdsNetwork();
    private final static Long STARTUP_TIME = System.currentTimeMillis();
    private static long lastInitAdNetworksInvocationTimestamp = 0;
    private static boolean FORCED_DISABLED = false;
    private static boolean PAUSED;
    private static final ReentrantLock pausedCheckLock = new ReentrantLock();
    private static MaxRewardedAd rewardedAd;

    private Offers() {
    }

    public static void initAdNetworks(Activity activity) {
        if (FORCED_DISABLED) {
            LOG.info("Offers.initAdNetworks() aborted, FORCED_DISABLED");
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastInitAdNetworksInvocationTimestamp) < 5000) {
            LOG.info("Offers.initAdNetworks() aborted, too soon to reinitialize networks.");
            return;
        }
        lastInitAdNetworksInvocationTimestamp = now;
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
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, Offers::checkIfPausedAsync);
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
                                        final boolean shutdownAfterward,
                                        final boolean dismissAfterward) {
        if (Offers.disabledAds()) {
            LOG.info("Skipping interstitial ads display, Offers have been disabled");
        } else {
            for (AdNetwork adNetwork : getActiveAdNetworks()) {
                if (adNetwork != null && adNetwork.started()) {
                    LOG.info("showInterstitial: AdNetwork " + adNetwork.getClass().getSimpleName() + " started? " + adNetwork.started());
                    boolean interstitialShown = adNetwork.showInterstitial(activity, placement, shutdownAfterward, dismissAfterward);
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
        if (dismissAfterward) {
            activity.finish();
        }
        if (shutdownAfterward) {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).shutdown();
            } else {
                UIUtils.sendShutdownIntent(activity);
            }
        }
        // otherwise it's up to the interstitial and its listener to dismiss or shutdown if necessary.
    }

    static void pauseAdsAsync(int minutes) {
        SystemUtils.ensureBackgroundThreadOrCrash("Offers::pauseAdsAsync");
        LOG.info("pauseAdsAsync: pausing for " + minutes + " minutes");
        pausedCheckLock.lock();
        ConfigurationManager CM = ConfigurationManager.instance();
        CM.setInt(Constants.FW_REWARDED_VIDEO_MINUTES, minutes);
        CM.setLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP, System.currentTimeMillis());
        PAUSED = true;
        pausedCheckLock.unlock();
    }

    public static boolean adsPausedAsync() {
        SystemUtils.ensureBackgroundThreadOrCrash("Offers::adsPausedAsync");
        final ConfigurationManager CM = ConfigurationManager.instance();
        final int rewarded_video_minutes = CM.getInt(Constants.FW_REWARDED_VIDEO_MINUTES, -1);
        final long paused_timestamp = CM.getLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP, -1);
        if (rewarded_video_minutes == -1 || paused_timestamp == -1) {
            return false;
        }
        final long pause_duration = rewarded_video_minutes * 60_000L;
        final long time_on_pause = System.currentTimeMillis() - paused_timestamp;
        LOG.info("adsPausedAsync(): " + (time_on_pause < pause_duration));
        return time_on_pause < pause_duration;
    }

    public static void unPauseAdsAsync() {
        SystemUtils.ensureBackgroundThreadOrCrash("Offers::unPauseAdsAsync");
        pausedCheckLock.lock();
        ConfigurationManager CM = ConfigurationManager.instance();
        CM.setInt(Constants.FW_REWARDED_VIDEO_MINUTES, -1);
        CM.setLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP, -1);
        PAUSED = false;
        pausedCheckLock.unlock();
    }

    public static int getMinutesLeftPausedAsync() {
        SystemUtils.ensureBackgroundThreadOrCrash("Offers::getMinutesLeftPausedAsync");
        ConfigurationManager CM = ConfigurationManager.instance();
        int rewarded_video_minutes = CM.getInt(Constants.FW_REWARDED_VIDEO_MINUTES, -1);
        if (rewarded_video_minutes == -1) {
            return -1;
        }
        long pause_duration = rewarded_video_minutes * 60_000L;
        long paused_timestamp = CM.getLong(Constants.FW_REWARDED_VIDEO_LAST_PLAYBACK_TIMESTAMP);
        if (paused_timestamp == -1) {
            return -1;
        }
        long time_on_pause = System.currentTimeMillis() - paused_timestamp;
        if (time_on_pause > pause_duration) {
            return 0;
        }
        return (int) ((pause_duration - time_on_pause) / 60_000);
    }

    private static Map<String, AdNetwork> getAllAdNetworks() {
        if (AD_NETWORKS == null) {
            AD_NETWORKS = new HashMap<>();
            AD_NETWORKS.put(APP_LOVIN.getShortCode(), APP_LOVIN);
            AD_NETWORKS.put(REMOVE_ADS.getShortCode(), REMOVE_ADS);
        }
        return AD_NETWORKS;
    }

    private static void checkIfPausedAsync() {
        SystemUtils.ensureBackgroundThreadOrCrash("Offers::checkIfPausedAsync");
        pausedCheckLock.lock();
        ConfigurationManager CM = ConfigurationManager.instance();
        int rewarded_video_minutes = CM.getInt(Constants.FW_REWARDED_VIDEO_MINUTES, -1);
        if (rewarded_video_minutes == -1) {
            PAUSED = false;
            pausedCheckLock.unlock();
            return;
        }
        long pause_duration = rewarded_video_minutes * 60_000L;
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
        }
        pausedCheckLock.unlock();
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

    public static void preLoadRewardedVideoAsync(WeakReference<AppCompatActivity> activityRef) {
        SystemUtils.ensureBackgroundThreadOrCrash("Offers::preLoadRewardedVideoAsync");
        rewardedAd = AppLovinAdNetwork.getInstance().loadRewardedVideo(activityRef);
    }

    public static void showRewardedVideo(BuyActivity activity) {
        if (rewardedAd != null && rewardedAd.isReady()) {
            rewardedAd.showAd();
            activity.finish();
        } else {
            UIUtils.showShortMessage(activity, R.string.looking_For_rewarded_video);
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> keepTryingRewardedAdAsync(Ref.weak(activity)));
        }
    }

    private static void keepTryingRewardedAdAsync(WeakReference<BuyActivity> activityRef) {
        if (!Ref.alive(activityRef)) {
            return;
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            LOG.error("keepTryingRewardedAdAsync() " + e.getMessage(), e);
        }
        if (rewardedAd != null && !rewardedAd.isReady()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, Offers::unPauseAdsAsync);
            activityRef.get().runOnUiThread(() -> {
                if (!Ref.alive(activityRef)) {
                    return;
                }
                try {
                    activityRef.get().stopProgressbars(ProductPaymentOptionsView.PayButtonType.REWARD_VIDEO);
                    UIUtils.showShortMessage(activityRef.get(), R.string.no_reward_videos_available);
                } catch (Throwable t) {
                    if (BuildConfig.DEBUG) {
                        throw t;
                    }
                    LOG.error("keepTryingRewardedAdAsync() " + t.getMessage(), t);
                } finally {
                    Ref.free(activityRef);
                }
            });
        } else if (Ref.alive(activityRef)) {
            activityRef.get().runOnUiThread(() -> {
                if (!Ref.alive(activityRef)) {
                    return;
                }
                try {
                    showRewardedVideo(activityRef.get());
                } catch (Throwable t) {
                    if (BuildConfig.DEBUG) {
                        throw t;
                    }
                    LOG.error("keepTryingRewardedAdAsync() " + t.getMessage(), t);
                } finally {
                    Ref.free(activityRef);
                }
            });
        }
    }

    public static void showInterstitialOfferIfNecessary(Activity activity, String placement,
                                                        final boolean shutdownAfterwards,
                                                        final boolean dismissAfterwards,
                                                        final boolean ignoreStartedTransfers) {
        InterstitialLogicParams params = new InterstitialLogicParams(placement, shutdownAfterwards, dismissAfterwards, ignoreStartedTransfers);
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () ->
                {
                    final boolean ready = readyForAnotherInterstitialAsync(params);
                    SystemUtils.postToUIThread(() -> Offers.onReadyForAnotherInterstitialAsyncCallback(activity, params, ready));
                }
        );
    }

    private static boolean readyForAnotherInterstitialAsync(InterstitialLogicParams params) {
        try {
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
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
            return false;
        }
    }

    private static void onReadyForAnotherInterstitialAsyncCallback(Activity activity, InterstitialLogicParams params, boolean readyForInterstitial) {
        if (readyForInterstitial) {
            try {
                Offers.showInterstitial(activity, params.placement, params.shutdownAfterwards, params.dismissAfterwards);
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
            }
        } else {
            // honor shutdownAfterwards and dismissAfterwards
            if (params.dismissAfterwards) {
                activity.finish();
            }
            if (params.shutdownAfterwards) {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).shutdown();
                } else {
                    UIUtils.sendShutdownIntent(activity);
                }
            }
        }
    }

    /**
     * @return true if the user has paused ads by viewing rewarded ad video
     */
    public static boolean disabledAds() {
        if (PAUSED) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, Offers::unPauseAdsAsync);
            return true;
        }
        return FORCED_DISABLED;
    }

    /**
     * Used for hard coded tests only
     */
    @SuppressWarnings("unused")
    public static void forceDisabledAds(Context context) {
        FORCED_DISABLED = true;
        if (lastInitAdNetworksInvocationTimestamp != 0) {
            stopAdNetworks(context);
        }
    }

    /**
     * Determines if remove ads offers are enabled.
     *
     * @return false if the app has been running for less than 2 seconds to ensure accurate user purchase verification,
     * otherwise true if ads are not disabled.
     */
    public static boolean removeAdsOffersEnabled() {
        long now = System.currentTimeMillis();
        if (now - Offers.STARTUP_TIME < 2000) {
            // not enough time to ask if user has bought products, better to avoid false positives for users that paid.
            return false;
        }
        return !Offers.disabledAds();
    }

    /**
     * Attempts to show a back-to-back interstitial ad.
     * This method checks if the remove ads feature is enabled and started. If so, it proceeds to determine if an
     * interstitial ad should be shown based on a threshold. If the conditions are met, an interstitial ad is shown.
     *
     * @param activity the activity context in which to show the interstitial ad.
     */
    private static void tryBackToBackInterstitial(Activity activity) {
        if (!REMOVE_ADS.enabled() || !REMOVE_ADS.started()) {
            return;
        }
        if (UIUtils.diceRollPassesThreshold(ConfigurationManager.instance(), Constants.PREF_KEY_GUI_REMOVEADS_BACK_TO_BACK_THRESHOLD)) {
            REMOVE_ADS.showInterstitial(activity, null, false, false);
        }
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
