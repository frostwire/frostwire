/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.SwitchPreference;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.fragments.FragmentTestHost;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.android.gui.views.preference.CustomSeekBarPreference;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TorrentPreferenceFragmentTest {
  private MockedStatic<ConfigurationRepository> repository;
  private MockedStatic<ConfigurationManager> configurations;
  private MockedStatic<TransferManager> transfers;
  private MockedStatic<NetworkManager> networks;
  private MockedStatic<SystemUtils> utils;
  private TransferManager transferManager;
  private NetworkManager network;
  private final List<Runnable> background = new ArrayList<>();

  @Before
  public void setUp() {
    transferManager = mock(TransferManager.class);
    network = mock(NetworkManager.class);
    repository = mockStatic(ConfigurationRepository.class);
    configurations = mockStatic(ConfigurationManager.class);
    configurations
        .when(ConfigurationManager::instance)
        .thenReturn(mock(ConfigurationManager.class));
    transfers = mockStatic(TransferManager.class);
    networks = mockStatic(NetworkManager.class);
    utils = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    transfers.when(TransferManager::instance).thenReturn(transferManager);
    networks.when(NetworkManager::instance).thenReturn(network);
    // background work is held: it would talk to libtorrent
    utils
        .when(() -> SystemUtils.postToHandler(any(), any()))
        .thenAnswer(
            invocation -> {
              background.add(invocation.getArgument(1));
              return null;
            });
  }

  @After
  public void tearDown() {
    utils.close();
    networks.close();
    transfers.close();
    configurations.close();
    repository.close();
  }

  private static TestTorrentPreferenceFragment launch() {
    TestTorrentPreferenceFragment fragment = new TestTorrentPreferenceFragment();
    FragmentTestHost.launch(fragment);
    return fragment;
  }

  private void runBackground() {
    List<Runnable> tasks = new ArrayList<>(background);
    background.clear();
    for (Runnable task : tasks) {
      task.run();
    }
  }

  @Test
  public void turningSeedingOffSuspendsSeedingAndDisablesTheWifiOnlyOption() {
    TestTorrentPreferenceFragment fragment = launch();
    CheckBoxPreference seeding =
        fragment.findPreference(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS);
    CheckBoxPreference wifiOnly =
        fragment.findPreference(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY);
    // seeding starts off in the mocked configuration: wifi only can't be picked
    assertFalse(wifiOnly.isEnabled());
    background.clear();

    assertTrue(PreferenceTestSupport.change(seeding, true));
    assertTrue(wifiOnly.isEnabled());
    runBackground();
    verify(transferManager).resumePolicySuspendedSeeding();

    assertTrue(PreferenceTestSupport.change(seeding, false));
    assertFalse(wifiOnly.isEnabled());
    runBackground();
    verify(transferManager).suspendSeedingTorrents();
  }

  @Test
  public void seedingOnWifiOnlyStopsSeedingWhileOnMobileData() {
    TestTorrentPreferenceFragment fragment = launch();
    CheckBoxPreference wifiOnly =
        fragment.findPreference(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY);
    background.clear();

    when(network.isDataWIFIUp()).thenReturn(true);
    assertTrue(PreferenceTestSupport.change(wifiOnly, true));
    runBackground();
    verify(transferManager, never()).suspendSeedingTorrents();
    verify(transferManager).resumePolicySuspendedSeeding();

    when(network.isDataWIFIUp()).thenReturn(false);
    assertTrue(PreferenceTestSupport.change(wifiOnly, true));
    runBackground();
    verify(transferManager).suspendSeedingTorrents();
  }

  @Test
  public void theSequentialTransfersOptionSetsTheDownloadMode() {
    TestTorrentPreferenceFragment fragment = launch();
    SwitchPreference sequential =
        fragment.findPreference(Constants.PREF_KEY_TORRENT_SEQUENTIAL_TRANSFERS_ENABLED);
    boolean before = UIBittorrentDownload.SEQUENTIAL_DOWNLOADS;
    try {
      assertTrue(PreferenceTestSupport.change(sequential, true));
      assertTrue(UIBittorrentDownload.SEQUENTIAL_DOWNLOADS);
      assertTrue(PreferenceTestSupport.change(sequential, false));
      assertFalse(UIBittorrentDownload.SEQUENTIAL_DOWNLOADS);
    } finally {
      UIBittorrentDownload.SEQUENTIAL_DOWNLOADS = before;
    }
  }

  @Test
  public void aNatPmpLeaseDurationMustBeAPositiveNumber() {
    TestTorrentPreferenceFragment fragment = launch();
    EditTextPreference lease =
        fragment.findPreference(Constants.PREF_KEY_NETWORK_NATPMP_LEASE_DURATION);
    background.clear();

    assertFalse(PreferenceTestSupport.change(lease, "0"));
    assertFalse(PreferenceTestSupport.change(lease, "-5"));
    assertFalse(PreferenceTestSupport.change(lease, "abc"));
    assertFalse(PreferenceTestSupport.change(lease, null));
    assertTrue(background.isEmpty());

    // a good one is applied to libtorrent in the background
    assertTrue(PreferenceTestSupport.change(lease, "3600"));
    assertEquals(1, background.size());
  }

  @Test
  public void theOtherAdvancedOptionsApplyInTheBackground() {
    TestTorrentPreferenceFragment fragment = launch();
    SwitchPreference multiple =
        fragment.findPreference(Constants.PREF_KEY_NETWORK_ALLOW_MULTIPLE_CONNECTIONS_PER_PID);
    EditTextPreference gateway = fragment.findPreference(Constants.PREF_KEY_NETWORK_NATPMP_GATEWAY);
    background.clear();

    assertTrue(PreferenceTestSupport.change(multiple, true));
    assertEquals(1, background.size());
    assertTrue(PreferenceTestSupport.change(gateway, " 192.168.1.1 "));
    assertEquals(2, background.size());
    assertTrue(PreferenceTestSupport.change(gateway, null));
    assertEquals(3, background.size());
  }

  @Test
  public void theSpeedAndConnectionPreferencesOpenTheirSeekbarDialog() {
    TestTorrentPreferenceFragment fragment = new TestTorrentPreferenceFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    CustomSeekBarPreference maxPeers =
        fragment.findPreference(Constants.PREF_KEY_TORRENT_MAX_PEERS);
    assertNotNull(maxPeers);

    fragment.onDisplayPreferenceDialog(maxPeers);
    host.get().getSupportFragmentManager().executePendingTransactions();
    assertNotNull(
        host.get()
            .getSupportFragmentManager()
            .findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG"));
  }

  @Test
  public void withoutAnEngineTheSeekbarOptionsStillSaveTheirValue() {
    TestTorrentPreferenceFragment fragment = launch();
    CustomSeekBarPreference maxPeers =
        fragment.findPreference(Constants.PREF_KEY_TORRENT_MAX_PEERS);
    background.clear();
    assertTrue(PreferenceTestSupport.change(maxPeers, 50));
    assertTrue(background.isEmpty());
    assertNull(fragment.currentBTEngine());
  }

  /** libtorrent's native library isn't loadable here: there is no engine. */
  public static class TestTorrentPreferenceFragment extends TorrentPreferenceFragment {
    @Override
    BTEngine currentBTEngine() {
      return null;
    }
  }
}
