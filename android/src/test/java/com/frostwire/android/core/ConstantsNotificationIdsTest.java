/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConstantsNotificationIdsTest {

    @Test
    public void engineOwnedNotifications_excludeMusicPlaybackServiceNotification() {
        Set<Integer> ids = toSet(Constants.engineOwnedNotificationIds());

        assertTrue(ids.contains(Constants.NOTIFICATION_FROSTWIRE_STATUS));
        assertTrue(ids.contains(Constants.NOTIFICATION_DOWNLOAD_TRANSFER_FINISHED));
        assertFalse(ids.contains(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE));
    }

    @Test
    public void engineOwnedNotifications_areDistinct() {
        int[] ids = Constants.engineOwnedNotificationIds();
        Set<Integer> unique = toSet(ids);

        assertTrue(unique.size() == ids.length);
    }

    private static Set<Integer> toSet(int[] ids) {
        Set<Integer> unique = new HashSet<>();
        for (int id : ids) {
            unique.add(id);
        }
        return unique;
    }
}
