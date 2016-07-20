/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.android.offers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * Created on 7/14/16.
 * @author aldenml
 * @author gubatron
 */
class RemoveAdsNetwork implements AdNetwork {
    private boolean started;
    @SuppressWarnings("unused")
    private final boolean DEBUG_MODE;
    private static final Logger LOGGER = Logger.getLogger(RemoveAdsNetwork.class);

    RemoveAdsNetwork(boolean debugMode) {
        DEBUG_MODE = debugMode;
    }

    @Override
    public void initialize(Activity activity) {
        if (!(started = enabled())) {
            LOGGER.info("RemoveAds initialize(): aborted. not enabled.");
            if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
                LOGGER.info("RemoveAds initialize(): not available for plus.");
            }
            started = false;
            return;
        }
    }

    @Override
    public void stop(Context context) {
        started = false;
    }

    @Override
    public boolean enabled() {
        ConfigurationManager config;
        boolean enabled = false;
        try {
            if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION || Products.disabledAds(PlayStore.getInstance())) {
                enabled = false;
            } else {
                config = ConfigurationManager.instance();
                LOGGER.info("config use removeAds -> " + config.getBoolean(Constants.PREF_KEY_GUI_USE_REMOVEADS));
                enabled = config.getBoolean(Constants.PREF_KEY_GUI_USE_REMOVEADS);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        LOGGER.info("enabled() -> " + enabled);
        return enabled;
    }

    @Override
    public boolean started() {
        LOGGER.info("started() -> " + started);
        return started;
    }

    @Override
    public boolean showInterstitial(WeakReference<Activity> activityRef, boolean shutdownActivityAfterwards, boolean dismissActivityAfterward) {
        if (started() && enabled() && Ref.alive(activityRef)) {
            Intent intent = new Intent(activityRef.get(), BuyActivity.class);
            intent.putExtra(BuyActivity.INTERSTITIAL_MODE, true);
            intent.putExtra("shutdownActivityAfterwards", shutdownActivityAfterwards);
            intent.putExtra("dismissActivityAfterward", dismissActivityAfterward);
            activityRef.get().startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    public void loadNewInterstitial(Activity activity) {
        // do nothing
    }
}
