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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.ThemeManager;
import com.frostwire.android.gui.fragments.FragmentTestHost;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.util.SystemUtils;
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
public class ApplicationPreferencesFragmentTest {
  private MockedStatic<ConfigurationRepository> repository;
  private MockedStatic<ConfigurationManager> configurations;
  private MockedStatic<Engine> engines;
  private MockedStatic<NetworkManager> networks;
  private MockedStatic<TransferManager> transfers;
  private MockedStatic<ThemeManager> themes;
  private MockedStatic<SystemUtils> utils;
  private ConfigurationManager configuration;
  private Engine engine;
  private NetworkManager network;
  private TransferManager transferManager;
  private final List<Runnable> background = new ArrayList<>();

  @Before
  public void setUp() {
    configuration = mock(ConfigurationManager.class);
    engine = mock(Engine.class);
    network = mock(NetworkManager.class);
    transferManager = mock(TransferManager.class);
    repository = mockStatic(ConfigurationRepository.class);
    configurations = mockStatic(ConfigurationManager.class);
    engines = mockStatic(Engine.class);
    networks = mockStatic(NetworkManager.class);
    transfers = mockStatic(TransferManager.class);
    themes = mockStatic(ThemeManager.class);
    utils = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    configurations.when(ConfigurationManager::instance).thenReturn(configuration);
    engines.when(Engine::instance).thenReturn(engine);
    networks.when(NetworkManager::instance).thenReturn(network);
    transfers.when(TransferManager::instance).thenReturn(transferManager);
    // the saved theme is loaded right away, dark
    themes
        .when(() -> ThemeManager.loadSavedThemeModeAsync(any()))
        .thenAnswer(
            invocation -> {
              ((ThemeManager.OnThemeLoadedUIThreadCallback) invocation.getArgument(0))
                  .onThemeLoaded(AppCompatDelegate.MODE_NIGHT_YES);
              return null;
            });
    themes
        .when(() -> ThemeManager.getThemeEntryFromMode(AppCompatDelegate.MODE_NIGHT_YES))
        .thenReturn("dark");
    // background work is held, the tests run what they want to see happen
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
    themes.close();
    transfers.close();
    networks.close();
    engines.close();
    configurations.close();
    repository.close();
  }

  private void runBackground() {
    List<Runnable> tasks = new ArrayList<>(background);
    background.clear();
    for (Runnable task : tasks) {
      task.run();
    }
  }

  private static ApplicationPreferencesFragment launch() {
    ApplicationPreferencesFragment fragment = new ApplicationPreferencesFragment();
    FragmentTestHost.launch(fragment);
    return fragment;
  }

  private static SwitchPreference connectSwitch(ApplicationPreferencesFragment f) {
    return f.findPreference("frostwire.prefs.internal.connect_disconnect");
  }

  @Test
  public void theConnectSwitchShowsTheEngineState() {
    when(engine.isStarted()).thenReturn(true);
    ApplicationPreferencesFragment fragment = launch();
    assertTrue(connectSwitch(fragment).isChecked());

    when(engine.isStarted()).thenReturn(false);
    when(engine.isStopped()).thenReturn(true);
    fragment = launch();
    assertFalse(connectSwitch(fragment).isChecked());
  }

  @Test
  public void switchingBitTorrentOffDisconnectsTheEngine() {
    when(engine.isStarted()).thenReturn(true);
    ApplicationPreferencesFragment fragment = launch();
    assertTrue(PreferenceTestSupport.change(connectSwitch(fragment), false));
    verify(engine).stopServices(true);
  }

  @Test
  public void switchingBitTorrentOnNeedsAVpnWhenTheVpnGuardIsOn() {
    when(engine.isStopped()).thenReturn(true);
    when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY))
        .thenReturn(true);
    when(network.isTunnelUp()).thenReturn(false);
    ApplicationPreferencesFragment fragment = launch();

    assertFalse(PreferenceTestSupport.change(connectSwitch(fragment), true));
    verify(engine, never()).startServices();
  }

  @Test
  public void switchingBitTorrentOnNeedsWifiWhenOnlyWifiIsAllowed() {
    when(engine.isStopped()).thenReturn(true);
    when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(true);
    when(network.isDataMobileUp()).thenReturn(true);
    ApplicationPreferencesFragment fragment = launch();

    assertFalse(PreferenceTestSupport.change(connectSwitch(fragment), true));
    verify(engine, never()).startServices();
  }

  @Test
  public void switchingBitTorrentOnStartsTheEngine() {
    when(engine.isDisconnected()).thenReturn(true);
    ApplicationPreferencesFragment fragment = launch();

    assertTrue(PreferenceTestSupport.change(connectSwitch(fragment), true));
    verify(engine).startServices();
  }

  @Test
  public void theVpnGuardSuspendsTorrentsWhenThereIsNoVpnAndResumesThemWhenOff() {
    ApplicationPreferencesFragment fragment = launch();
    SwitchPreference vpn =
        fragment.findPreference(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY);
    when(transferManager.isBittorrentOnVpnOnlyAndNoVpn()).thenReturn(true);
    background.clear();

    assertTrue(PreferenceTestSupport.change(vpn, true));
    verify(configuration).setBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY, true);
    runBackground();
    verify(transferManager).suspendTorrentsForPolicy();

    assertTrue(PreferenceTestSupport.change(vpn, false));
    verify(configuration).setBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY, false);
    runBackground();
    verify(transferManager).resumeResumableTransfers();
    verify(transferManager).seedFinishedTransfers();
  }

  @Test
  public void wifiOnlyStopsTransfersWhenWifiIsDown() {
    ApplicationPreferencesFragment fragment = launch();
    SwitchPreference wifiOnly = fragment.findPreference(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
    // wifi is up: nothing to stop
    when(network.isDataWIFIUp()).thenReturn(true);
    assertTrue(PreferenceTestSupport.change(wifiOnly, true));
    verify(transferManager, never()).pauseTorrents();

    // wifi is down: transfers are paused
    when(network.isDataWIFIUp()).thenReturn(false);
    assertTrue(PreferenceTestSupport.change(wifiOnly, true));
    verify(transferManager).stopHttpTransfers();
    verify(transferManager).pauseTorrents();

    // turning it off changes nothing
    assertTrue(PreferenceTestSupport.change(wifiOnly, false));
    verify(transferManager).pauseTorrents();
  }

  @Test
  public void wifiOnlyAsksBeforeKillingAnHttpDownloadInProgress() {
    ApplicationPreferencesFragment fragment = new ApplicationPreferencesFragment();
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    SwitchPreference wifiOnly = fragment.findPreference(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
    when(network.isDataWIFIUp()).thenReturn(false);
    when(transferManager.isHttpDownloadInProgress()).thenReturn(true);

    assertFalse(PreferenceTestSupport.change(wifiOnly, true));
    verify(transferManager, never()).pauseTorrents();
    host.get().getSupportFragmentManager().executePendingTransactions();
    assertNotNull(
        host.get()
            .getSupportFragmentManager()
            .findFragmentByTag("ApplicationPreferencesFragment.DIALOG.stop.http"));

    // an answer of no changes nothing, one of yes stops transfers and checks the option
    fragment.onDialogClick(
        "ApplicationPreferencesFragment.DIALOG.stop.http", Dialog.BUTTON_NEGATIVE);
    verify(transferManager, never()).pauseTorrents();
    fragment.onDialogClick("another dialog", Dialog.BUTTON_POSITIVE);
    verify(transferManager, never()).pauseTorrents();
    fragment.onDialogClick(
        "ApplicationPreferencesFragment.DIALOG.stop.http", Dialog.BUTTON_POSITIVE);
    verify(transferManager).stopHttpTransfers();
    verify(transferManager).pauseTorrents();
    assertTrue(wifiOnly.isChecked());
  }

  @Test
  public void thePickedThemeIsSavedAndAppliedAndTheSavedOneShown() {
    ApplicationPreferencesFragment fragment = launch();
    ListPreference theme = fragment.findPreference(Constants.PREF_KEY_GUI_THEME_MODE);
    // what was saved is what the list shows
    runBackground();
    assertEquals("dark", theme.getValue());
    assertEquals(fragment.getString(R.string.dark), theme.getSummary().toString());

    assertTrue(PreferenceTestSupport.change(theme, "light"));
    themes.verify(() -> ThemeManager.saveThemeModeAsync("light"));
    themes.verify(() -> ThemeManager.applyThemeMode("light"));
    assertEquals(fragment.getString(R.string.light), theme.getSummary().toString());
  }

  @Test
  public void theSupportRowOpensTheOffer() {
    ApplicationPreferencesFragment fragment = launch();
    Preference support = fragment.findPreference("frostwire.prefs.offers.buy_no_ads");
    assertNotNull(support);
    assertTrue(support.getOnPreferenceClickListener().onPreferenceClick(support));
    runBackground();
    Intent intent =
        shadowOf((Application) ApplicationProvider.getApplicationContext())
            .getNextStartedActivity();
    assertNotNull(intent);
    assertEquals(Intent.ACTION_VIEW, intent.getAction());
  }
}
