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
import android.content.Context;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.ThreadPool;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class Offers {

    private static final Logger LOG = Logger.getLogger(Offers.class);
    static final boolean DEBUG_MODE = false;

    private Offers() {
    }

    static final ThreadPool THREAD_POOL = new ThreadPool("Offers", 1, 5, 1L, new LinkedBlockingQueue<Runnable>(), true);
    private static long lastInterstitialShownTimestamp = -1;

    private final static AppLovinAdNetwork APP_LOVIN = new AppLovinAdNetwork();
    private final static InMobiAdNetwork IN_MOBI = new InMobiAdNetwork();
    private final static RemoveAdsNetwork REMOVE_ADS = new RemoveAdsNetwork();
    private static Map<String,AdNetwork> AD_NETWORKS;

    public static void initAdNetworks(Activity activity) {
        for (AdNetwork adNetwork : getActiveAdNetworks()) {
            adNetwork.initialize(activity);
        }

        stopAdNetworksIfPurchasedRemoveAds(activity);
    }

    private static Map<String, AdNetwork> getAllAdNetworks() {
        if (AD_NETWORKS == null) {
            AD_NETWORKS = new HashMap<>();
            AD_NETWORKS.put(Constants.AD_NETWORK_SHORTCODE_APPLOVIN, APP_LOVIN);
            AD_NETWORKS.put(Constants.AD_NETWORK_SHORTCODE_INMOBI, IN_MOBI);
            AD_NETWORKS.put(Constants.AD_NETWORK_SHORTCODE_REMOVEADS, REMOVE_ADS);
        }
        return AD_NETWORKS;
    }

    public static void stopAdNetworks(Context context) {
        for (AdNetwork adNetwork : getActiveAdNetworks()) {
            if (adNetwork.started()) {
                adNetwork.stop(context);
            }
        }
        LOG.info("Ad networks stopped");
    }

    public static void showInterstitial(final Activity activity,
                                        final boolean shutdownAfterwards,
                                        final boolean dismissAfterwards) {

        boolean interstitialShown = false;

        if (Products.disabledAds(PlayStore.getInstance())) {
            LOG.info("Skipping interstitial ads display, PlayStore reports no ads");
        } else if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION && !ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_SUPPORT_FROSTWIRE)) {
            LOG.info("Skipping interstitial ads display, Plus instance not supporting FrostWire development");
        } else {
            final WeakReference<Activity> activityRef = Ref.weak(activity);
            for (AdNetwork adNetwork : getActiveAdNetworks()) {
                LOG.info("showInterstitial: AdNetwork " + adNetwork.getClass().getSimpleName() + " started? " + adNetwork.started());
                if (!interstitialShown && adNetwork.started()) {
                    interstitialShown = adNetwork.showInterstitial(activityRef, shutdownAfterwards, dismissAfterwards);
                }
            }
        }
        if (!interstitialShown) {
            if (dismissAfterwards) {
                activity.finish();
            }
            if (shutdownAfterwards && activity instanceof MainActivity) {
                ((MainActivity) activity).shutdown();
            }
        } // otherwise it's up to the interstitial and its listener to dismiss or shutdown if necessary.
    }

    public static void showInterstitialOfferIfNecessary(Activity ctx) {
        TransferManager TM = TransferManager.instance();
        int startedTransfers = TM.incrementStartedTransfers();
        ConfigurationManager CM = ConfigurationManager.instance();
        final int INTERSTITIAL_OFFERS_TRANSFER_STARTS = DEBUG_MODE ? 1 : CM.getInt(Constants.PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS);
        final int INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES = CM.getInt(Constants.PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES);
        final long INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MS = DEBUG_MODE ? 10000 : TimeUnit.MINUTES.toMillis(INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES);

        long timeSinceLastOffer = System.currentTimeMillis() - Offers.lastInterstitialShownTimestamp;
        boolean itsBeenLongEnough = timeSinceLastOffer >= INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MS;
        boolean startedEnoughTransfers = startedTransfers >= INTERSTITIAL_OFFERS_TRANSFER_STARTS;
        boolean shouldDisplayFirstOne = (Offers.lastInterstitialShownTimestamp == -1 && startedEnoughTransfers);

        if (shouldDisplayFirstOne || (itsBeenLongEnough && startedEnoughTransfers)) {
            Offers.showInterstitial(ctx, false, false);
            TM.resetStartedTransfers();
            Offers.lastInterstitialShownTimestamp = System.currentTimeMillis();
        }
    }

    private static void stopAdNetworksIfPurchasedRemoveAds(Context context) {
        final ConfigurationManager CM = ConfigurationManager.instance();
        final PlayStore playStore = PlayStore.getInstance();
        final Collection<Product> purchasedProducts = Products.listEnabled(playStore, Products.DISABLE_ADS_FEATURE);
        if (purchasedProducts != null && purchasedProducts.size() > 0) {
            CM.setBoolean(Constants.PREF_KEY_GUI_SUPPORT_FROSTWIRE, false);
            Offers.stopAdNetworks(context);
            LOG.info("Turning off ads, user previously purchased AdRemoval");
        }
    }

    /**
     * Checks the preference values under Constants.PREF_KEY_GUI_OFFERS_WATERFALL to deactivate
     * the networks that have not been specified there.
     * @return The Array of Active AdNetworks.
     */
    public static AdNetwork[] getActiveAdNetworks() {
        final ConfigurationManager CM = ConfigurationManager.instance();
        final Map<String, AdNetwork> allAdNetworks = getAllAdNetworks();
        final Set<String> waterfallShortcodes = CM.getStringSet(Constants.PREF_KEY_GUI_OFFERS_WATERFALL);

        final AdNetwork[] activeAdNetworks = new AdNetwork[waterfallShortcodes.size()];

        for (String shortCode : allAdNetworks.keySet()) {
            boolean networkInUse = waterfallShortcodes.contains(shortCode);
            AdNetwork network = allAdNetworks.get(shortCode);

            // turn on/off the in use preference for that network.
            CM.setBoolean(network.getInUsePreferenceKey(), networkInUse);

            if (networkInUse) {
                activeAdNetworks[getKeyOffset(shortCode, waterfallShortcodes)]=network;
            }
        }

        int i=0;

        for (String code : waterfallShortcodes) {
            LOG.info("waterfall[" + i + "] " + " = " + code);
            i++;
        }

        i=0;
        for (AdNetwork network : activeAdNetworks) {
            LOG.info("activeAdNetworks[" + i + "] " + " = " + network);
            i++;
        }

        return activeAdNetworks;
    }

    private static int getKeyOffset(String key, Set<String> keySet) {
        if (!keySet.contains(key)) {
            return -1;
        }

        int i=0;
        for (String k : keySet) {
            if (k.equals(key)) {
                System.out.println("Offers.getKeyOffset(): " + k + " in index " + i);
                return i;
            }
            i++;
        }

        return i;
    }
}