/*
 *     Created by the FrostWire Android team
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

import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;

/**
 * Minimal offer manager used to coordinate the display of in-house support banners.
 * Third-party ad networks have been removed, so the class now acts as a central toggle
 * for enabling/disabling support promotions and for honoring legacy shutdown behaviour.
 */
public final class Offers {

    private static final Logger LOG = Logger.getLogger(Offers.class);

    /**
     * Maintained for backwards compatibility with existing debug tooling.
     */
    public static final boolean DEBUG_MODE = false;
    public static final String PLACEMENT_INTERSTITIAL_MAIN = "interstitial_main";

    private static boolean forcedDisabled = false;

    private Offers() {
    }

    public static void initAdNetworks(Activity activity) {
        LOG.info("Support offers initialized");
    }

    public static void stopAdNetworks(Context context) {
        LOG.info("Support offers stopped");
    }

    public static void showInterstitial(Activity activity,
                                        String placement,
                                        boolean shutdownAfterward,
                                        boolean dismissAfterward) {
        LOG.info("showInterstitial(placement=" + placement + ") invoked with in-house offers disabled");
        handlePostInterstitialFlow(activity, shutdownAfterward, dismissAfterward, null);
    }

    public static void showInterstitialOfferIfNecessary(Activity activity,
                                                        String placement,
                                                        boolean shutdownAfterwards,
                                                        boolean dismissAfterwards,
                                                        boolean ignoreStartedTransfers) {
        showInterstitial(activity, placement, shutdownAfterwards, dismissAfterwards);
    }

    public static boolean disabledAds() {
        return forcedDisabled;
    }

    public static void forceDisabledAds(Context context) {
        forcedDisabled = true;
        stopAdNetworks(context);
    }

    public static boolean removeAdsOffersEnabled() {
        return false;
    }

    private static void handlePostInterstitialFlow(Activity activity,
                                                   boolean shutdownAfterward,
                                                   boolean dismissAfterward,
                                                   Application fallbackContext) {
        if (activity != null) {
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
        } else if (shutdownAfterward && fallbackContext != null) {
            UIUtils.sendShutdownIntent(fallbackContext);
        }
    }
}
