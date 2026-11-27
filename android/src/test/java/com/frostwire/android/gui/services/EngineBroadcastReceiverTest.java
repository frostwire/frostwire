/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.telephony.TelephonyManager;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class EngineBroadcastReceiverTest {
  private final EngineBroadcastReceiver receiver = new EngineBroadcastReceiver();
  private final List<Runnable> posted = new ArrayList<>();
  private Application app;
  private Engine engine;
  private NetworkManager network;
  private ConfigurationManager config;
  private TransferManager transfers;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<Engine> engineStatic;
  private MockedStatic<NetworkManager> networkStatic;
  private MockedStatic<ConfigurationManager> configStatic;
  private MockedStatic<TransferManager> transfersStatic;
  private MockedStatic<AndroidRelayStack> relay;

  @Before
  public void setUp() {
    app = ApplicationProvider.getApplicationContext();
    engine = mock(Engine.class);
    network = mock(NetworkManager.class);
    config = mock(ConfigurationManager.class);
    transfers = mock(TransferManager.class);
    system = mockStatic(SystemUtils.class);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              posted.add(call.getArgument(1));
              return null;
            });
    engineStatic = mockStatic(Engine.class);
    engineStatic.when(Engine::instance).thenReturn(engine);
    networkStatic = mockStatic(NetworkManager.class);
    networkStatic.when(NetworkManager::instance).thenReturn(network);
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    transfersStatic = mockStatic(TransferManager.class);
    transfersStatic.when(TransferManager::instance).thenReturn(transfers);
    relay = mockStatic(AndroidRelayStack.class);
    when(engine.wasShutdown()).thenReturn(true); // keeps the native socket reopen out of it
  }

  @After
  public void tearDown() {
    relay.close();
    transfersStatic.close();
    configStatic.close();
    networkStatic.close();
    engineStatic.close();
    system.close();
  }

  private void networkChanged() {
    receiver.onReceive(app, new Intent(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION));
    assertEquals(1, posted.size());
    posted.get(0).run();
  }

  @Test
  public void unrelatedAndPhoneStateBroadcastsQueueNothing() {
    receiver.onReceive(app, new Intent("some.other.ACTION"));
    receiver.onReceive(
        app,
        new Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            .putExtra(TelephonyManager.EXTRA_STATE, "RINGING"));
    assertTrue(posted.isEmpty());
    // a null action must not crash the receiver either
    receiver.onReceive(app, new Intent());
  }

  @Test
  public void aLostNetworkStopsTheServicesBeforeAnyQueuedWork() {
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(false);
    receiver.onReceive(app, new Intent(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION));
    verify(engine).stopServices(true);
    assertEquals(1, posted.size());
    posted.get(0).run();
    verify(engine, org.mockito.Mockito.times(2)).stopServices(true);
  }

  @Test
  public void mobileDataWithTheWifiOnlySettingSuspendsTorrentsAndStopsServices() {
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
    when(network.isDataMobileUp()).thenReturn(true);
    when(network.isDataWIFIUp()).thenReturn(false);
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(true);
    networkChanged();
    verify(transfers).suspendTorrentsForPolicy();
    verify(engine).stopServices(true);
    verify(engine, never()).resumeServicesIfDisconnected();
  }

  @Test
  public void aConnectedNetworkWithoutWifiOrMobileDoesNothing() {
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
    networkChanged();
    verify(transfers, never()).suspendTorrentsForPolicy();
    verify(engine, never()).stopServices(anyBoolean());
  }

  @Test
  public void theVpnGuardPausesTorrentsWhenNoVpnIsUp() {
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
    when(network.isDataWIFIUp()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY)).thenReturn(true);
    networkChanged();
    verify(transfers).suspendTorrentsForPolicy();
    verify(engine).stopServices(true);
  }

  @Test
  public void wifiResumesDisconnectedServicesAndKeepsSeedingWhenAllowed() {
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
    when(network.isDataWIFIUp()).thenReturn(true);
    when(engine.isDisconnected()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS)).thenReturn(true);
    networkChanged();
    verify(engine).resumeServicesIfDisconnected();
    verify(transfers).resumePolicySuspendedDownloads();
    verify(transfers).resumePolicySuspendedSeeding();
    verify(transfers, never()).suspendSeedingTorrents();
  }

  @Test
  public void seedingIsSuspendedWhenDisabledOrWifiOnlyOnMobile() {
    relay.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
    when(network.isDataWIFIUp()).thenReturn(true);
    networkChanged();
    verify(transfers).suspendSeedingTorrents();

    posted.clear();
    when(network.isDataWIFIUp()).thenReturn(false);
    when(network.isDataMobileUp()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS)).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY))
        .thenReturn(true);
    networkChanged();
    verify(transfers, org.mockito.Mockito.times(2)).suspendSeedingTorrents();
  }

  @Test
  public void mountingAnSdCardAnnouncesItAndResumesTheServices() {
    Intent mounted =
        new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file:///storage/ABCD-1234"));
    system.when(() -> SystemUtils.isPrimaryExternalPath(any())).thenReturn(false);
    when(engine.isDisconnected()).thenReturn(true);
    receiver.onReceive(app, mounted);
    assertEquals(1, posted.size());
    posted.get(0).run();
    List<Intent> broadcasts = Shadows.shadowOf(app).getBroadcastIntents();
    assertEquals(
        Constants.ACTION_NOTIFY_SDCARD_MOUNTED, broadcasts.get(broadcasts.size() - 1).getAction());
    verify(engine).resumeServicesIfDisconnected();
  }

  @Test
  public void mountingWithTheVpnGuardOnAndNoVpnDoesNotResume() {
    Intent mounted =
        new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file:///storage/emulated/0"));
    system.when(() -> SystemUtils.isPrimaryExternalPath(any())).thenReturn(true);
    when(engine.isDisconnected()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY)).thenReturn(true);
    receiver.onReceive(app, mounted);
    posted.get(0).run();
    verify(engine, never()).resumeServicesIfDisconnected();
    // with a VPN it resumes
    when(network.isVpnConnected()).thenReturn(true);
    posted.get(0).run();
    verify(engine).resumeServicesIfDisconnected();
  }

  @Test
  public void mountingWithoutDataIsSurvivedAndConnectedServicesAreLeftAlone() {
    receiver.onReceive(app, new Intent(Intent.ACTION_MEDIA_MOUNTED));
    posted.get(0).run();
    verify(engine, never()).resumeServicesIfDisconnected();
  }

  @Test
  public void unmountingTheSdCardFallsBackToThePrimaryStorage() {
    Intent unmounted =
        new Intent(Intent.ACTION_MEDIA_UNMOUNTED, Uri.parse("file:///storage/ABCD-1234"));
    system.when(() -> SystemUtils.isPrimaryExternalPath(any())).thenReturn(false);
    system.when(SystemUtils::isPrimaryExternalStorageMounted).thenReturn(true);
    receiver.onReceive(app, unmounted);
    posted.get(0).run();
    verify(config).setStoragePath(any());

    // primary storage unmounting changes nothing
    system.when(() -> SystemUtils.isPrimaryExternalPath(any())).thenReturn(true);
    posted.get(0).run();
    verify(config, org.mockito.Mockito.times(1)).setStoragePath(any());
  }
}
