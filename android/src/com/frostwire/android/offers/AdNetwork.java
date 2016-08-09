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

import java.lang.ref.WeakReference;

interface AdNetwork {
    void initialize(final Activity activity);

    /** Stops the network, any calls to started() should return false after this has been invoked */
    void stop(Context context);

    /** Enables or disables the ad network */
    void enable(boolean enabled);

    /** Returns whether the ad network is enabled or not */
    boolean enabled();

    /** Returns true if the network is enabled and initialized */
    boolean started();

    boolean showInterstitial(final WeakReference<? extends Activity> activityRef,
                             final boolean shutdownActivityAfterwards,
                             final boolean dismissActivityAfterward);

    void loadNewInterstitial(Activity activity);

    String getShortCode();
    String getInUsePreferenceKey();

    boolean isDebugOn();
}
