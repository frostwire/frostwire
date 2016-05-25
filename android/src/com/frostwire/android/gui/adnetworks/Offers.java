/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(TM). All rights reserved.
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

package com.frostwire.android.gui.adnetworks;

import android.app.Activity;
import android.content.Context;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.offers.PlayStore;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.ThreadPool;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Offers {

    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(Offers.class);

    static final ThreadPool THREAD_POOL = new ThreadPool("SearchManager", 1, 5, 1L, new LinkedBlockingQueue<Runnable>(), true);
    private static long lastInterstitialShownTimestamp = -1;
    static boolean MOBILE_CORE_NATIVE_ADS_READY = false;
    private final static AppLovinAdNetwork APP_LOVIN = new AppLovinAdNetwork();
    //private final static MobileCoreAdNetwork MOBILE_CORE = new MobileCoreAdNetwork();
    private final static InMobiAdNetwork IN_MOBI = new InMobiAdNetwork();
    private static List<AdNetwork> AD_NETWORKS;

    public static void initAdNetworks(Activity activity) {
        AD_NETWORKS = Arrays.asList(APP_LOVIN, IN_MOBI); //, MOBILE_CORE});
        for (AdNetwork adNetwork : AD_NETWORKS) {
            adNetwork.initialize(activity);
        }

//        AD_NETWORKS = new LinkedList<>();
//        AD_NETWORKS.add(APP_LOVIN);
//        APP_LOVIN.initialize(activity);

//        AD_NETWORKS = new LinkedList<>();
//        AD_NETWORKS.add(IN_MOBI);
//        IN_MOBI.initialize(activity);
    }

    public static void stopAdNetworks(Context context) {
        for (AdNetwork adNetwork : AD_NETWORKS) {
            if (adNetwork.started()) {
                adNetwork.stop(context);
            }
        }
    }

    public static boolean isFreeAppsEnabled() {
        ConfigurationManager config;
        boolean isFreeAppsEnabled = false;
        try {
            config = ConfigurationManager.instance();
            isFreeAppsEnabled = (config.getBoolean(Constants.PREF_KEY_GUI_SUPPORT_FROSTWIRE) && config.getBoolean(Constants.PREF_KEY_GUI_USE_MOBILE_CORE)) && Constants.IS_GOOGLE_PLAY_DISTRIBUTION;
        } catch (Throwable ignored) {
        }
        return isFreeAppsEnabled;
    }

    @SuppressWarnings("UnusedParameters")
    public static void onFreeAppsClick(Context context) {
        /**
        if (isFreeAppsEnabled() && MOBILE_CORE.started() && MOBILE_CORE.isDirectToMarketReady()) {
            try {
                LOG.debug("onFreeAppsClick");
                MOBILE_CORE.directToMarket((Activity) context);
            } catch (Throwable t) {
                LOG.error("can't show app wall", t);
                t.printStackTrace();
            }
        }
         */
    }

    public static void showInterstitial(final Activity activity,
                                        final boolean shutdownAfterwards,
                                        final boolean dismissAfterwards) {

        boolean interstitialShown = false;

        if (!PlayStore.getInstance().showAds()) {
            LOG.info("Skipping interstitial ads display, PlayStore reports no ads");
        } else {
            final WeakReference<Activity> activityRef = Ref.weak(activity);
            for (AdNetwork adNetwork : AD_NETWORKS) {
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
        }
    }

    public static void showInterstitialOfferIfNecessary(Activity ctx) {
        TransferManager TM = TransferManager.instance();
        int startedTransfers = TM.incrementStartedTransfers();
        ConfigurationManager CM = ConfigurationManager.instance();
        final int INTERSTITIAL_OFFERS_TRANSFER_STARTS = CM.getInt(Constants.PREF_KEY_GUI_INTERSTITIAL_OFFERS_TRANSFER_STARTS);
        final int INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES = CM.getInt(Constants.PREF_KEY_GUI_INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES);
        final long INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MS = TimeUnit.MINUTES.toMillis(INTERSTITIAL_TRANSFER_OFFERS_TIMEOUT_IN_MINUTES);

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
}
