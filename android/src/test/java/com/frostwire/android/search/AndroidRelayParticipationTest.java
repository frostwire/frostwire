/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import android.app.Application;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AndroidRelayParticipationTest {
    @Test
    public void optOutWifiAndVpnRestrictionsGateBeforeAnyStartupResources() {
        ConfigurationManager configuration = mock(ConfigurationManager.class);
        NetworkManager network = mock(NetworkManager.class);
        try (MockedStatic<ConfigurationManager> config = mockStatic(ConfigurationManager.class);
             MockedStatic<NetworkManager> networks = mockStatic(NetworkManager.class)) {
            config.when(ConfigurationManager::instance).thenReturn(configuration);
            networks.when(NetworkManager::instance).thenReturn(network);
            when(configuration.getBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED)).thenReturn(true);
            when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(true);
            when(network.isDataWIFIUp()).thenReturn(true);
            assertTrue(AndroidRelayStack.isParticipationEnabled());
            when(network.isDataWIFIUp()).thenReturn(false);
            when(network.isDataMobileUp()).thenReturn(true);
            assertFalse(AndroidRelayStack.isParticipationEnabled());
            assertNull(AndroidRelayStack.start(null, null, null, () -> true));
            when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(false);
            assertTrue(AndroidRelayStack.isParticipationEnabled());
            when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY)).thenReturn(true);
            assertFalse(AndroidRelayStack.isParticipationEnabled());
            when(network.isVpnConnected()).thenReturn(true);
            assertTrue(AndroidRelayStack.isParticipationEnabled());
            when(configuration.getBoolean(Constants.PREF_KEY_SEARCH_USE_DISTRIBUTED)).thenReturn(false);
            assertTrue(AndroidRelayStack.isParticipationEnabled());
            when(configuration.getBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED)).thenReturn(false);
            assertFalse(AndroidRelayStack.isParticipationEnabled());
            assertNull(AndroidRelayStack.start(null, null, null, () -> true));
            when(configuration.getBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED)).thenReturn(true);
            assertNull(AndroidRelayStack.start(null, null, null, () -> false));
        }
    }
}
