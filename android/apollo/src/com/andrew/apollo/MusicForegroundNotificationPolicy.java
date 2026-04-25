/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

final class MusicForegroundNotificationPolicy {

    private static final int ANDROID_12_API = 31;

    enum Action {
        PROMOTE_WITH_MEDIA3,
        UPDATE_WITH_MEDIA3,
        UPDATE_VIA_NOTIFICATION_MANAGER
    }

    private MusicForegroundNotificationPolicy() {
    }

    static Action resolve(int sdkInt,
                          boolean startInForegroundRequired,
                          boolean appVisible) {
        if (!startInForegroundRequired) {
            return Action.UPDATE_WITH_MEDIA3;
        }
        if (sdkInt < ANDROID_12_API) {
            return Action.PROMOTE_WITH_MEDIA3;
        }
        if (appVisible) {
            return Action.PROMOTE_WITH_MEDIA3;
        }
        return Action.UPDATE_VIA_NOTIFICATION_MANAGER;
    }
}
