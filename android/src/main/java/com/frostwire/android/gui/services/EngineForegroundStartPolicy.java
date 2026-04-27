/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

/**
 * Decides whether EngineForegroundService should attempt foreground promotion
 * for the current start request.
 */
final class EngineForegroundStartPolicy {

    enum Action {
        START_FOREGROUND,
        STOP_BACKGROUND_RESTART
    }

    private EngineForegroundStartPolicy() {
    }

    static Action resolve(int sdkInt, boolean isNullIntentRestart, boolean isAppInForeground) {
        if (sdkInt >= 31 && isNullIntentRestart && !isAppInForeground) {
            return Action.STOP_BACKGROUND_RESTART;
        }
        return Action.START_FOREGROUND;
    }
}
