/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class NetworkLifecycleTest {

  @Test
  public void duplicateCapabilitiesDoNotBroadcastAnotherNetworkChange() throws Exception {
    ConnectivityManager connectivity = mock(ConnectivityManager.class);
    Network network = mock(Network.class);
    NetworkCapabilities capabilities = capabilities(true, false, false);
    when(connectivity.getActiveNetwork()).thenReturn(network);
    when(connectivity.getNetworkCapabilities(network)).thenReturn(capabilities);
    LocalBroadcastManager broadcasts = mock(LocalBroadcastManager.class);

    try (MockedStatic<LocalBroadcastManager> localBroadcasts =
        mockStatic(LocalBroadcastManager.class)) {
      localBroadcasts.when(() -> LocalBroadcastManager.getInstance(any())).thenReturn(broadcasts);
      networkManager(connectivity);

      ArgumentCaptor<ConnectivityManager.NetworkCallback> callback =
          ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
      verify(connectivity).registerNetworkCallback(any(), callback.capture());
      callback.getValue().onCapabilitiesChanged(network, capabilities);

      verify(broadcasts, never()).sendBroadcast(any());
    }
  }

  @Test
  public void simultaneousWifiAndMobileStillHaveInternetData() throws Exception {
    ConnectivityManager connectivity = mock(ConnectivityManager.class);
    Network network = mock(Network.class);
    NetworkCapabilities capabilities = capabilities(true, true, false);
    when(connectivity.getActiveNetwork()).thenReturn(network);
    when(connectivity.getNetworkCapabilities(network)).thenReturn(capabilities);

    assertTrue(networkManager(connectivity).isInternetDataConnectionUp());
  }

  @Test
  public void handlingNetworkBroadcastDoesNotSendTheSameBroadcastAgain() throws Exception {
    Path root = Path.of(System.getProperty("user.dir"));
    String relative =
        "src/main/java/com/frostwire/android/gui/services/EngineBroadcastReceiver.java";
    Path source = root.resolve(relative);
    if (!Files.exists(source)) {
      source = root.resolve("android").resolve(relative);
    }
    String receiver = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    int start = receiver.indexOf("private void handleNetworkStateChange");
    int end = receiver.indexOf("private void handleConnectedNetwork", start);
    String handler = receiver.substring(start, end);

    assertFalse(handler.contains("handleNetworkStatusChange()"));
    assertFalse(handler.contains("queryNetworkStatusBackground"));
  }

  private static NetworkManager networkManager(ConnectivityManager connectivity) throws Exception {
    Context context = mock(Context.class);
    when(context.getApplicationContext()).thenReturn(context);
    when(context.getSystemService(Application.CONNECTIVITY_SERVICE)).thenReturn(connectivity);
    Constructor<NetworkManager> constructor =
        NetworkManager.class.getDeclaredConstructor(Context.class);
    constructor.setAccessible(true);
    return constructor.newInstance(context);
  }

  private static NetworkCapabilities capabilities(boolean wifi, boolean mobile, boolean vpn) {
    NetworkCapabilities capabilities = mock(NetworkCapabilities.class);
    when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(wifi);
    when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(mobile);
    when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(vpn);
    return capabilities;
  }
}
