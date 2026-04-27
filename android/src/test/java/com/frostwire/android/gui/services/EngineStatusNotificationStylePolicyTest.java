/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EngineStatusNotificationStylePolicyTest {

    @Test
    public void android13_usesCustomRemoteViews() {
        assertEquals(
                EngineStatusNotificationStylePolicy.Style.CUSTOM_REMOTE_VIEWS,
                EngineStatusNotificationStylePolicy.resolve(33));
    }

    @Test
    public void android14_usesSimpleNotification() {
        assertEquals(
                EngineStatusNotificationStylePolicy.Style.SIMPLE_NOTIFICATION,
                EngineStatusNotificationStylePolicy.resolve(34));
    }

    @Test
    public void android16_usesSimpleNotification() {
        assertEquals(
                EngineStatusNotificationStylePolicy.Style.SIMPLE_NOTIFICATION,
                EngineStatusNotificationStylePolicy.resolve(36));
    }
}
