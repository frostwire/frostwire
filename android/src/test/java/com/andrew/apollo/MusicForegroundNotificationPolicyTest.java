/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pure JVM regression tests for the policy that decides whether Media3 may
 * promote MusicPlaybackService to a foreground service.
 */
public class MusicForegroundNotificationPolicyTest {

    @Test
    public void android12BackgroundPlayback_usesNotificationManagerInsteadOfPromoting() {
        assertEquals(
                MusicForegroundNotificationPolicy.Action.UPDATE_VIA_NOTIFICATION_MANAGER,
                MusicForegroundNotificationPolicy.resolve(
                        31,
                        true,
                        false));
    }

    @Test
    public void android16BackgroundPlayback_usesNotificationManagerInsteadOfPromoting() {
        assertEquals(
                MusicForegroundNotificationPolicy.Action.UPDATE_VIA_NOTIFICATION_MANAGER,
                MusicForegroundNotificationPolicy.resolve(
                        36,
                        true,
                        false));
    }

    @Test
    public void android16BackgroundWithoutForegroundPromotion_updatesViaNotificationManager() {
        assertEquals(
                MusicForegroundNotificationPolicy.Action.UPDATE_VIA_NOTIFICATION_MANAGER,
                MusicForegroundNotificationPolicy.resolve(
                        36,
                        true,
                        false));
    }

    @Test
    public void android16VisibleApp_allowsMedia3Promotion() {
        assertEquals(
                MusicForegroundNotificationPolicy.Action.PROMOTE_WITH_MEDIA3,
                MusicForegroundNotificationPolicy.resolve(
                        36,
                        true,
                        true));
    }

    @Test
    public void android11RetainsLegacyMedia3PromotionBehavior() {
        assertEquals(
                MusicForegroundNotificationPolicy.Action.PROMOTE_WITH_MEDIA3,
                MusicForegroundNotificationPolicy.resolve(
                        30,
                        true,
                        false));
    }

    @Test
    public void notificationUpdateNotRequiringForeground_neverPromotes() {
        assertEquals(
                MusicForegroundNotificationPolicy.Action.UPDATE_WITH_MEDIA3,
                MusicForegroundNotificationPolicy.resolve(
                        36,
                        false,
                        true));
    }
}
