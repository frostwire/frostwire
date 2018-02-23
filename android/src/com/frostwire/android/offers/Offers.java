/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;
import com.frostwire.util.ThreadPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class Offers {
    private static final Logger LOG = Logger.getLogger(Offers.class);

    static final boolean DEBUG_MODE = false;
    static final ThreadPool THREAD_POOL = new ThreadPool("Offers", 1, 5, 1L, new LinkedBlockingQueue<>(), true);
    public static final String PLACEMENT_INTERSTITIAL_EXIT = "interstitial_exit";
    private static Map<String,AdNetwork> AD_NETWORKS;
    private final static MoPubAdNetwork MOPUB = new MoPubAdNetwork();
    private final static AppLovinAdNetwork APP_LOVIN = AppLovinAdNetwork.getInstance();
    private final static RemoveAdsNetwork REMOVE_ADS = new RemoveAdsNetwork();
    private final static Long STARTUP_TIME = System.currentTimeMillis();
    private static long lastInitAdnetworksInvocationTimestamp = 0;
    private static boolean FORCED_DISABLED = false;

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
                adNetwork.initialize(activity);
            }
        }
        PrebidManager.getInstance(activity.getApplicationContext());
        LOG.info("Offers.initAdNetworks() sucess");
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (AD_NETWORKS == null) {
            return;
        }
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

    public static void destroyMopubInterstitials(Context context) {
        MOPUB.stop(context);
    }

    public static void stopAdNetworks(Context context) {
        for (AdNetwork adNetwork : getActiveAdNetworks()) {
            if (adNetwork != null && adNetwork.started()) {
                try {
                    adNetwork.stop(context);
                } catch (Throwable ignored) {}
            }
        }
        LOG.info("Ad networks stopped");
    }

    public static void showInterstitial(final Activity activity,
                                        String placement,
                                        final boolean shutdownAfterwards,
                                        final boolean dismissAfterwards) {
        boolean interstitialShown = false;

        if (Offers.disabledAds()) {
            LOG.info("Skipping interstitial ads display, PlayStore reports no ads");
        } else {
            for (AdNetwork adNetwork : getActiveAdNetworks()) {
                if (!interstitialShown && adNetwork != null && adNetwork.started()) {
                    LOG.info("showInterstitial: AdNetwork " + adNetwork.getClass().getSimpleName() + " started? " + adNetwork.started());
                    interstitialShown = adNetwork.showInterstitial(activity, placement, shutdownAfterwards, dismissAfterwards);
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

        if (!interstitialShown) {
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
        } // otherwise it's up to the interstitial and its listener to dismiss or shutdown if necessary.
    }

    public static void showInterstitialOfferIfNecessary(Activity ctx, String placement,
                                                        final boolean shutdownAfterwards,
                                                        final boolean dismissAfterwards) {
        showInterstitialOfferIfNecessary(ctx, placement, shutdownAfterwards, dismissAfterwards, false);
    }

    public static void showInterstitialOfferIfNecessary(Activity ctx, String placement,
                                                        final boolean shutdownAfterwards,
                                                        final boolean dismissAfterwards,
                                                        final boolean ignoreStartedTransfers) {
        TransferManager TM = TransferManager.instance();
        int startedTransfers = TM.incrementStartedTransfers();
        ConfigurationManager CM = ConfigurationManager.instance();
        final int INTERSTITIAL_OFFERS_TRANSFER_STARTS = DEBUG_MODE ? 1 : CM.getInt(Constants.PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS);
        final int INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES = CM.getInt(Constants.PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES);
        final long INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MS = DEBUG_MODE ? 10000 : TimeUnit.MINUTES.toMillis(INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES);

        long lastInterstitialShownTimestamp = CM.getLong(Constants.PREF_KEY_GUI_INTERSTITIAL_LAST_DISPLAY);
        long timeSinceLastOffer = System.currentTimeMillis() - lastInterstitialShownTimestamp;
        boolean itsBeenLongEnough = timeSinceLastOffer >= INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MS;
        boolean startedEnoughTransfers = startedTransfers >= INTERSTITIAL_OFFERS_TRANSFER_STARTS;

        if (ignoreStartedTransfers) {
            startedEnoughTransfers = true;
        }

        boolean shouldDisplayFirstOne = (lastInterstitialShownTimestamp == -1 && startedEnoughTransfers);

        if (shouldDisplayFirstOne || (itsBeenLongEnough && startedEnoughTransfers)) {
            Offers.showInterstitial(ctx, placement, shutdownAfterwards, dismissAfterwards);
            TM.resetStartedTransfers();
        }
    }

    public static boolean disabledAds() {
        return FORCED_DISABLED || Products.disabledAds(PlayStore.getInstance());
    }

    public static void forceDisabledAds(Context context) {
        FORCED_DISABLED = true;
        if (lastInitAdnetworksInvocationTimestamp != 0) {
            stopAdNetworks(context);
        }
    }

    /**
     * @return true only for flavor basic or for plus_debug and we haven't paid for ad removals, or
     * it's plus supporting frostwire with ads.
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
        boolean notDisabledAds = !Offers.disabledAds();
        return (isBasic || isDevelopment) && notDisabledAds;
    }

    private static void tryBackToBackInterstitial(Activity activity) {
        if (REMOVE_ADS == null || !REMOVE_ADS.enabled() || !REMOVE_ADS.started()) {
            return;
        }

        if (UIUtils.diceRollPassesThreshold(ConfigurationManager.instance(), Constants.PREF_KEY_GUI_REMOVEADS_BACK_TO_BACK_THRESHOLD)) {
            REMOVE_ADS.showInterstitial(activity, null, false, false);
        }
    }

    private static boolean stopAdNetworksIfPurchasedRemoveAds(Context context) {
        //final ConfigurationManager CM = ConfigurationManager.instance();
        boolean stopped = false;
        final PlayStore playStore = PlayStore.getInstance();
        final Collection<Product> purchasedProducts = Products.listEnabled(playStore, Products.DISABLE_ADS_FEATURE);
        if (purchasedProducts != null && purchasedProducts.size() > 0) {
            //CM.setBoolean(Constants.PREF_KEY_GUI_SUPPORT_FROSTWIRE, false);
            Offers.stopAdNetworks(context);
            stopped = true;
            LOG.info("Turning off ads, user previously purchased AdRemoval");
        }
        return stopped;
    }

    /**
     * Also checks the preference values under Constants.PREF_KEY_GUI_OFFERS_WATERFALL and deactivates
     * the networks that have not been specified there.
     * @return The Array of Active AdNetworks.
     */
    private static AdNetwork[] getActiveAdNetworks() {
        final ConfigurationManager CM = ConfigurationManager.instance();
        final Map<String, AdNetwork> allAdNetworks = getAllAdNetworks();
        final String[] waterfallShortcodes = CM.getStringArray(Constants.PREF_KEY_GUI_OFFERS_WATERFALL);

        if (waterfallShortcodes == null) {
            return new AdNetwork[] {};
        }

        final List<AdNetwork> activeAdNetworksList = new ArrayList<>(waterfallShortcodes.length);

        for (String shortCode : waterfallShortcodes) {
            if (allAdNetworks.containsKey(shortCode)) {
                final AdNetwork adNetwork = allAdNetworks.get(shortCode);
                adNetwork.enable(true);
                activeAdNetworksList.add(adNetwork);
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
        int i=0;
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

        public static void dismissAndOrShutdownIfNecessary(final AdNetwork adNetwork,
                                                           final Activity activity,
                                                           final boolean finishAfterDismiss,
                                                           final boolean shutdownAfter,
                                                           final boolean tryBack2BackRemoveAdsOffer,
                                                           final Application fallbackContext) {
            LOG.info("dismissAndOrShutdownIfNecessary(finishAfterDismiss=" + finishAfterDismiss + ", shutdownAfter=" + shutdownAfter + ", tryBack2BackRemoveAdsOffer= " + tryBack2BackRemoveAdsOffer + ")");
            Engine.instance().getVibrator().hapticFeedback();

            // dismissAndOrShutdownIfNecessary is invoked on adNetwork listeners when interstitials are dismissed
            // if a user leaves an ad displayed without interacting with it for too long a second
            // ad could be displayed on MainActivity's onResume back to back.
            ConfigurationManager.instance().setLong(Constants.PREF_KEY_GUI_INTERSTITIAL_LAST_DISPLAY, System.currentTimeMillis());

            if (activity != null) {
                if (shutdownAfter) {
                    if (adNetwork != null) {
                        adNetwork.stop(activity);
                    }

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
                    if (adNetwork != null) {
                        adNetwork.stop(activity);
                    }

                    if (activity instanceof MainActivity) {
                        activity.finish();
                    } else {
                        activity.finish();
                        UIUtils.sendGoHomeIntent(activity);
                    }
                }

                if (!finishAfterDismiss && !shutdownAfter && tryBack2BackRemoveAdsOffer) {
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
