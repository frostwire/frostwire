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
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;
import com.frostwire.util.ThreadPool;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class Offers {

    private static final Logger LOG = Logger.getLogger(Offers.class);
    static final ThreadPool THREAD_POOL = new ThreadPool("SearchManager", 1, 5, 1L, new LinkedBlockingQueue<Runnable>(), true);
    public static boolean MOBILE_CORE_NATIVE_ADS_READY = false;
    private final static AppLovinAdNetwork APP_LOVIN = new AppLovinAdNetwork();
    //private final static MobileCoreAdNetwork MOBILE_CORE = new MobileCoreAdNetwork();
    private final static InMobiAdNetwork IN_MOBI = new InMobiAdNetwork();
    private static List<AdNetwork> AD_NETWORKS;

    public static void initAdNetworks(Activity activity) {
        AD_NETWORKS = Arrays.asList(new AdNetwork[]{APP_LOVIN, IN_MOBI}); //, MOBILE_CORE});
        for (AdNetwork adNetwork : AD_NETWORKS) {
            adNetwork.initialize(activity);
        }
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
        } catch (Throwable t) {
        }
        return isFreeAppsEnabled;
    }

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
        final WeakReference<Activity> activityRef = Ref.weak(activity);
        for (AdNetwork adNetwork : AD_NETWORKS) {
            if (!interstitialShown && adNetwork.started()) {
                interstitialShown = adNetwork.showInterstitial(activityRef, shutdownAfterwards, dismissAfterwards);
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
}