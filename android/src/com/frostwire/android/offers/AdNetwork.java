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
import android.content.Context;

/**
 * @author gubatron
 * @author aldenml
 */
interface AdNetwork {

    void initialize(final Activity activity);

    /**
     * Stops the network, any calls to started() should return false after this has been invoked
     */
    void stop(Context context);

    /**
     * Marks the network as started.
     */
    void start();

    /**
     * Returns true if the network is initialized. The app can't start unless it's enabled
     */
    boolean started();

    /**
     * Enables or disables the ad network in the app's inner configuration
     */
    void enable(boolean enabled);

    /**
     * Returns whether the ad network is enabled or not in the configuration
     */
    boolean enabled();


    boolean showInterstitial(Activity activity,
                             String placement,
                             final boolean shutdownActivityAfterwards,
                             final boolean dismissActivityAfterward);

    void loadNewInterstitial(Activity activity);

    String getShortCode();

    String getInUsePreferenceKey();

    boolean isDebugOn();
}
