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
import android.content.Context;

/**
 * @author gubatron
 * @author aldenml
 */
public interface AdNetwork {

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
                             final boolean shutdownAfterward,
                             final boolean dismissActivityAfterward);

    void loadNewInterstitial(Activity activity);

    String getShortCode();

    String getInUsePreferenceKey();

    boolean isDebugOn();
}
