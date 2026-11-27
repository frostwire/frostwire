/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import android.app.Application;
import androidx.preference.EditTextPreference;
import androidx.preference.SwitchPreference;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.fragments.FragmentTestHost;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.views.preference.CustomSeekBarPreference;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The torrent settings screen over a running engine: what the user picks is applied to libtorrent's
 * session (rate limits, queue sizes, connections, DHT, NAT-PMP).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TorrentPreferenceFragmentEngineTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private TestEngine local;
  private MockedStatic<ConfigurationRepository> repository;
  private MockedStatic<ConfigurationManager> configurations;
  private MockedStatic<TransferManager> transfers;
  private MockedStatic<NetworkManager> networks;
  private MockedStatic<SystemUtils> utils;

  @Before
  public void setUp() throws Exception {
    local = TestEngine.start(tmp.newFolder("engine").toPath());
    repository = mockStatic(ConfigurationRepository.class);
    configurations = mockStatic(ConfigurationManager.class);
    configurations
        .when(ConfigurationManager::instance)
        .thenReturn(mock(ConfigurationManager.class));
    transfers = mockStatic(TransferManager.class);
    transfers.when(TransferManager::instance).thenReturn(mock(TransferManager.class));
    networks = mockStatic(NetworkManager.class);
    networks.when(NetworkManager::instance).thenReturn(mock(NetworkManager.class));
    utils = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    // the settings reach libtorrent from the MISC thread; here that is this one
    utils
        .when(() -> SystemUtils.postToHandler(any(), any()))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(1)).run();
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
    local.close();
  }

  private static EngineTorrentPreferenceFragment launch() {
    EngineTorrentPreferenceFragment fragment = new EngineTorrentPreferenceFragment();
    FragmentTestHost.launch(fragment);
    return fragment;
  }

  @Test
  public void theSeekbarOptionsAreAppliedToTheRunningSession() {
    EngineTorrentPreferenceFragment fragment = launch();

    assertTrue(change(fragment, Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED, 120_000));
    assertTrue(change(fragment, Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED, 60_000));
    assertTrue(change(fragment, Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS, 9));
    assertTrue(change(fragment, Constants.PREF_KEY_TORRENT_MAX_UPLOADS, 8));
    assertTrue(change(fragment, Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS, 250));
    assertTrue(change(fragment, Constants.PREF_KEY_TORRENT_MAX_PEERS, 44));

    assertEquals(120_000, local.engine.downloadRateLimit());
    assertEquals(60_000, local.engine.uploadRateLimit());
    assertEquals(9, local.engine.maxActiveDownloads());
    assertEquals(8, local.engine.maxActiveSeeds());
    assertEquals(250, local.engine.maxConnections());
    assertEquals(44, local.engine.maxPeers());
  }

  @Test
  public void theDhtSwitchStartsAndStopsTheDhtOfTheSession() {
    EngineTorrentPreferenceFragment fragment = launch();
    SwitchPreference dht = fragment.findPreference(Constants.PREF_KEY_NETWORK_ENABLE_DHT);

    assertTrue(PreferenceTestSupport.change(dht, true));
    TestEngine.await(local.engine::isDhtRunning, "the DHT started");

    assertTrue(PreferenceTestSupport.change(dht, false));
    TestEngine.await(() -> !local.engine.isDhtRunning(), "the DHT stopped");
  }

  @Test
  public void theAdvancedOptionsAreAppliedToTheSessionSettings() {
    EngineTorrentPreferenceFragment fragment = launch();
    SwitchPreference multiple =
        fragment.findPreference(Constants.PREF_KEY_NETWORK_ALLOW_MULTIPLE_CONNECTIONS_PER_PID);
    EditTextPreference gateway = fragment.findPreference(Constants.PREF_KEY_NETWORK_NATPMP_GATEWAY);
    EditTextPreference lease =
        fragment.findPreference(Constants.PREF_KEY_NETWORK_NATPMP_LEASE_DURATION);

    assertTrue(PreferenceTestSupport.change(multiple, true));
    assertTrue(PreferenceTestSupport.change(gateway, " 192.168.1.1 "));
    assertTrue(PreferenceTestSupport.change(lease, "1800"));

    assertTrue(local.engine.settings().allowMultipleConnectionsPerPid());
    assertEquals("192.168.1.1", local.engine.settings().natpmpGateway());
    assertEquals(1800, local.engine.settings().natpmpLeaseDuration());

    assertTrue(PreferenceTestSupport.change(multiple, false));
    assertTrue(PreferenceTestSupport.change(gateway, null));
    assertFalse(local.engine.settings().allowMultipleConnectionsPerPid());
    assertEquals("", local.engine.settings().natpmpGateway());
  }

  private static boolean change(EngineTorrentPreferenceFragment fragment, String key, int value) {
    CustomSeekBarPreference preference = fragment.findPreference(key);
    return PreferenceTestSupport.change(preference, value);
  }

  /** The screen as the app has it: the engine is the application's. */
  public static class EngineTorrentPreferenceFragment extends TorrentPreferenceFragment {
    @Override
    BTEngine currentBTEngine() {
      return BTEngine.getInstance();
    }
  }
}
