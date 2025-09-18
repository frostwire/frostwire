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
import android.content.Intent;

import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.util.Logger;

/**
 * Created on 7/14/16.
 *
 * @author aldenml
 * @author gubatron
 */
class RemoveAdsNetwork extends AbstractAdNetwork {

    private static final Logger LOG = Logger.getLogger(RemoveAdsNetwork.class);

    RemoveAdsNetwork() {
    }

    @Override
    public void initialize(Activity activity) {
        if (shouldWeAbortInitialize(activity)) {
            return;
        }

        if (enabled()) {
            start();
        }
        if (!started()) {
            LOG.info("RemoveAds initialize(): aborted. not enabled.");
            if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
                LOG.info("RemoveAds initialize(): not available for plus.");
            }
            stop(activity);
        }
    }


    @Override
    public boolean enabled() {
        boolean enabled = false;
        try {
            //noinspection SimplifiableIfStatement (done like this on purpose for readability)
            if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
                enabled = true;
            } else {
                enabled = super.enabled();
            }
        } catch (Throwable e) {
            LOG.error("RemoveAds::enabled(): error", e);
        }
        return enabled;
    }

    @Override
    public boolean showInterstitial(Activity activity,
                                    String placement,
                                    boolean shutdownActivityAfterwards,
                                    boolean dismissActivityAfterward) {
        if (started() && enabled() && activity != null) {
            Intent intent = new Intent(activity, BuyActivity.class);
            intent.putExtra(BuyActivity.INTERSTITIAL_MODE, true);
            intent.putExtra("shutdownActivityAfterwards", shutdownActivityAfterwards);
            intent.putExtra("dismissActivityAfterward", dismissActivityAfterward);
            activity.startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    public void loadNewInterstitial(Activity activity) {
        // do nothing
    }

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_REMOVEADS;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_REMOVEADS;
    }

    @Override
    public boolean isDebugOn() {
        return false;
    }
}
