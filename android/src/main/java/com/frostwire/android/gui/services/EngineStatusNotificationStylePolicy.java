/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

public final class EngineStatusNotificationStylePolicy {

    public enum Style {
        CUSTOM_REMOTE_VIEWS,
        SIMPLE_NOTIFICATION
    }

    private static final int ANDROID_14_API_LEVEL = 34;

    private EngineStatusNotificationStylePolicy() {
    }

    public static Style resolve(int sdkInt) {
        if (sdkInt >= ANDROID_14_API_LEVEL) {
            return Style.SIMPLE_NOTIFICATION;
        }
        return Style.CUSTOM_REMOTE_VIEWS;
    }
}
