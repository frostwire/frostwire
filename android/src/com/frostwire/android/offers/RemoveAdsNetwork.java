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
import android.content.Intent;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.BuyActivity;
import com.frostwire.logging.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * Created on 7/14/16.
 *
 * @author aldenml
 * @author gubatron
 */
class RemoveAdsNetwork implements AdNetwork {

    private static final Logger LOG = Logger.getLogger(RemoveAdsNetwork.class);

    private boolean started;

    RemoveAdsNetwork() {
    }

    @Override
    public void initialize(Activity activity) {
        if (!(started = enabled())) {
            LOG.info("RemoveAds initialize(): aborted. not enabled.");
            if (!Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
                LOG.info("RemoveAds initialize(): not available for plus.");
            }
            started = false;
        }
    }

    @Override
    public void stop(Context context) {
        started = false;
        LOG.info("stopped");
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
                LOG.info("config use removeAds -> " + config.getBoolean(getInUsePreferenceKey()));
                enabled = config.getBoolean(getInUsePreferenceKey());
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        LOG.info("enabled() -> " + enabled);
        return enabled;
    }

    @Override
    public boolean started() {
        LOG.info("started() -> " + started);
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

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_REMOVEADS;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_REMOVEADS;
    }
}
