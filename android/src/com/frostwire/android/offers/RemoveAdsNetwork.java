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
        if (abortInitialize(activity)) {
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
            if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION | PlayStore.available()) {
                enabled = true;
            } else {
                enabled = super.enabled();
            }
        } catch (Throwable e) {
            e.printStackTrace();
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
