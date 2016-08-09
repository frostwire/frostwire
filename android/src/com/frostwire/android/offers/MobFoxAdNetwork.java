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
import com.frostwire.android.core.Constants;
import com.frostwire.util.Logger;

import java.lang.ref.WeakReference;

/**
 * Created on 8/8/16.
 * @author gubatron
 * @author aldenml
 */
class MobFoxAdNetwork implements AdNetwork {
    private static final Logger LOG = Logger.getLogger(MobFoxAdNetwork.class);
    private static final boolean DEBUG_MODE = Offers.DEBUG_MODE;

    private final String inventoryHash = "cc73727fabc4235d769120f8a1d0635d";
    private boolean started = false;

    @Override
    public void initialize(Activity activity) {
        if (!enabled()) {
            if (!started()) {
                LOG.info("MobFoxAdNetwork initialize(): aborted. not enabled.");
            } else {
                // initialize can be called multiple times, we may have to stop
                // this network if we started it using a default value.
                stop(activity);
            }
            return;
        }
        started = true;
    }

    @Override
    public void stop(Context context) {
        started = false;
    }

    @Override
    public void enable(boolean enabled) {
        Offers.AdNetworkHelper.enable(this, enabled);
    }

    @Override
    public boolean enabled() {
        return Offers.AdNetworkHelper.enabled(this);
    }

    @Override
    public boolean started() {
        return started;
    }

    @Override
    public boolean showInterstitial(WeakReference<Activity> activityRef, boolean shutdownActivityAfterwards, boolean dismissActivityAfterward) {
        return false;
    }

    @Override
    public void loadNewInterstitial(Activity activity) {

    }

    @Override
    public String getShortCode() {
        return Constants.AD_NETWORK_SHORTCODE_MOBFOX;
    }

    @Override
    public String getInUsePreferenceKey() {
        return Constants.PREF_KEY_GUI_USE_MOBFOX;
    }

    @Override
    public boolean isDebugOn() {
        return DEBUG_MODE;
    }
}
