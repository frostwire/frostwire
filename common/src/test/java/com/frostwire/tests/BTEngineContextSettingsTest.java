/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.SettingsPack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BTEngineContextSettingsTest {

    @Test
    public void btContextDefaultsKeepNewSessionSettingsConservative() {
        BTContext ctx = new BTContext();

        assertEquals("", ctx.natpmpGateway);
        assertEquals(0, ctx.natpmpLeaseDuration);
        assertFalse(ctx.allowMultipleConnectionsPerPid);
    }

    @Test
    public void applyContextSettingsCopiesNatpmpAndPeerConnectionSettings() throws Exception {
        BTContext ctx = new BTContext();
        ctx.natpmpGateway = "10.8.0.1";
        ctx.natpmpLeaseDuration = 900;
        ctx.allowMultipleConnectionsPerPid = true;
        SettingsPack settings = new SettingsPack();

        applyContextSettings(settings, ctx);

        assertEquals("10.8.0.1", settings.natpmpGateway());
        assertEquals(900, settings.natpmpLeaseDuration());
        assertTrue(settings.allowMultipleConnectionsPerPid());
    }

    @Test
    public void applyContextSettingsKeepsDefaultLeaseDurationWhenContextValueIsNotPositive() throws Exception {
        BTContext ctx = new BTContext();
        ctx.natpmpGateway = null;
        ctx.natpmpLeaseDuration = 0;
        SettingsPack settings = new SettingsPack();
        int defaultLeaseDuration = settings.natpmpLeaseDuration();

        applyContextSettings(settings, ctx);

        assertEquals("", settings.natpmpGateway());
        assertEquals(defaultLeaseDuration, settings.natpmpLeaseDuration());
        assertFalse(settings.allowMultipleConnectionsPerPid());
    }

    private static void applyContextSettings(SettingsPack settings, BTContext ctx) throws Exception {
        Method method = BTEngine.class.getDeclaredMethod("applyContextSettings", SettingsPack.class, BTContext.class);
        method.setAccessible(true);
        method.invoke(null, settings, ctx);
    }
}
