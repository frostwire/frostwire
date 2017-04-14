/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.util;

import android.app.Activity;
import android.content.pm.ActivityInfo;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.util.Logger;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 4/14/17.
 */


public final class ScreenOrientationLocker {
    private static Logger LOG = Logger.getLogger(ScreenOrientationLocker.class);
    private static boolean ENABLED = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_LOCK_SCREEN_ORIENTATION_ON_START);

    private enum Orientation {
        PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
        LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
        UNSET(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        final int androidValue;
        Orientation(int androidValue) {
            this.androidValue = androidValue;
        }
        static Orientation fromDegrees(int degrees) {
            switch (degrees) {
                case 0:
                case 180:
                    return PORTRAIT;
                case 90:
                case 270:
                    return LANDSCAPE;
                default:
                    return UNSET;
            }
        }
    }

    private static Orientation initialOrientation = Orientation.UNSET;

    public static void enable(final Activity activity, boolean enabled) {
        initialOrientation = Orientation.UNSET;
        ENABLED = enabled;
        if (activity != null) {
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            onRotationRequested(activity, rotation);
        }
    }

    public static void onRotationRequested(final Activity activity, int orientationInDegrees) {
        Orientation newOrientation = Orientation.fromDegrees(orientationInDegrees);
        if (newOrientation == Orientation.UNSET) {
            return;
        }
        if (initialOrientation == Orientation.UNSET) {
            setInitialRotation(activity, newOrientation);
        }
        if (ENABLED && newOrientation != initialOrientation) {
            activity.setRequestedOrientation(initialOrientation.androidValue);
        } else if (!ENABLED) {
            activity.setRequestedOrientation(newOrientation.androidValue);
        }
    }

    private static void setInitialRotation(final Activity activity, Orientation orientation) {
        if (initialOrientation != Orientation.UNSET || activity == null) {
            LOG.info("setInitialRotation() aborted. Kept initialRotation="+initialOrientation+" , activity="+activity);
            return;
        }
        initialOrientation = orientation;
        LOG.info("setInitialRotation() set to " + initialOrientation);
    }
}
