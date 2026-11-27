/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.Constants;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * NetworkManager is a process-wide singleton bound to the first application context it sees, so the
 * whole story is told in one test, in a Robolectric sandbox (SDK 31) no other test shares. The
 * connectivity service is a mock whose answers the test changes as the network "moves".
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, application = Application.class)
public class NetworkManagerTest {

  private static NetworkCapabilities capabilities(boolean wifi, boolean mobile, boolean vpn) {
    NetworkCapabilities caps = mock(NetworkCapabilities.class);
    when(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(wifi);
    when(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(mobile);
    when(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(vpn);
    return caps;
  }

  @Test
  public void networkStateFollowsTheActiveNetworkAndAnnouncesChanges() {
    Application app = ApplicationProvider.getApplicationContext();
    ConnectivityManager cm = mock(ConnectivityManager.class);
    Context context =
        new ContextWrapper(app) {
          @Override
          public Context getApplicationContext() {
            return this;
          }

          @Override
          public Object getSystemService(String name) {
            return Context.CONNECTIVITY_SERVICE.equals(name) ? cm : super.getSystemService(name);
          }
        };
    List<Intent> broadcasts = new ArrayList<>();
    LocalBroadcastManager.getInstance(context)
        .registerReceiver(
            new BroadcastReceiver() {
              @Override
              public void onReceive(Context ctx, Intent intent) {
                broadcasts.add(intent);
              }
            },
            new IntentFilter(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION));

    // 1. nothing connected when the manager starts
    NetworkManager.create(context);
    NetworkManager.create(context); // a second create is ignored
    NetworkManager manager = NetworkManager.instance();
    assertSame(manager, NetworkManager.instance());
    ArgumentCaptor<ConnectivityManager.NetworkCallback> captor =
        ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
    verify(cm).registerNetworkCallback(any(NetworkRequest.class), captor.capture());
    ConnectivityManager.NetworkCallback callback = captor.getValue();
    assertFalse(manager.isInternetDataConnectionUp());
    assertFalse(manager.isDataWIFIUp());
    assertFalse(manager.isDataMobileUp());
    assertFalse(manager.isVpnConnected());

    // 2. wifi comes up: the callback updates the state and announces it
    Network network = mock(Network.class);
    when(cm.getActiveNetwork()).thenReturn(network);
    NetworkCapabilities wifiOnly = capabilities(true, false, false);
    when(cm.getNetworkCapabilities(network)).thenReturn(wifiOnly);
    callback.onAvailable(network);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertTrue(manager.isDataWIFIUp());
    assertFalse(manager.isDataMobileUp());
    assertTrue(manager.isInternetDataConnectionUp());
    assertTrue(manager.hasNetworkStateTransitioned());
    assertEquals(1, broadcasts.size());
    assertTrue(broadcasts.get(0).getBooleanExtra("isDataUp", false));

    // the same capabilities again change nothing, so nothing is announced
    callback.onCapabilitiesChanged(network, wifiOnly);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertEquals(1, broadcasts.size());

    // 3. wifi hands over to mobile data behind a VPN
    NetworkCapabilities mobileVpn = capabilities(false, true, true);
    when(cm.getNetworkCapabilities(network)).thenReturn(mobileVpn);
    callback.onCapabilitiesChanged(network, mobileVpn);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertFalse(manager.isDataWIFIUp());
    assertTrue(manager.isDataMobileUp());
    assertTrue(manager.isVpnConnected());
    assertEquals(2, broadcasts.size());

    // 4. the VPN drops without the network callback noticing: the periodic refresh announces it
    NetworkCapabilities mobileOnly = capabilities(false, true, false);
    when(cm.getNetworkCapabilities(network)).thenReturn(mobileOnly);
    manager.refreshNetworkState();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertFalse(manager.isVpnConnected());
    assertEquals(3, broadcasts.size());
    // nothing moved: the refresh is silent
    manager.refreshNetworkState();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertEquals(3, broadcasts.size());

    // 5. the status helper always announces what the manager currently believes
    NetworkManager.queryNetworkStatusBackground(manager);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertEquals(4, broadcasts.size());
    assertTrue(broadcasts.get(3).getBooleanExtra("isDataUp", false));

    // 6. no capabilities, then no active network at all: everything is down
    when(cm.getNetworkCapabilities(network)).thenReturn(null);
    callback.onLost(network);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertFalse(manager.isInternetDataConnectionUp());
    assertEquals(5, broadcasts.size());
    assertFalse(broadcasts.get(4).getBooleanExtra("isDataUp", true));
    when(cm.getActiveNetwork()).thenReturn(null);
    callback.onAvailable(network);
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertEquals("already down: nothing new to announce", 5, broadcasts.size());

    // 7. unregistering is idempotent
    manager.unregisterNetworkCallback();
    verify(cm).unregisterNetworkCallback(callback);
    manager.unregisterNetworkCallback();
  }
}
