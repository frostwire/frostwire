/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.BuildConfig;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.dialogs.SoftwareUpdaterDialog;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.engines.SearchEngine;
import com.frostwire.search.engines.SearchEngineID;
import com.frostwire.search.engines.SearchEngines;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class SoftwareUpdaterTest {
  private final SoftwareUpdater updater = SoftwareUpdater.getInstance();
  private final List<Runnable> background = new ArrayList<>();
  private final List<Runnable> ui = new ArrayList<>();
  private MainActivity activity;
  private HttpClient client;
  private ConfigurationManager config;
  private SearchEngines registry;
  private AndroidSearchEngineAdapter adapter;
  private SoftwareUpdaterDialog dialog;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<HttpClientFactory> http;
  private MockedStatic<ConfigurationManager> configStatic;
  private MockedStatic<AndroidSearchEngines> engines;
  private MockedStatic<SoftwareUpdaterDialog> dialogs;
  private MockedStatic<Offers> offers;

  @Before
  public void setUp() {
    Application app = ApplicationProvider.getApplicationContext();
    activity = mock(MainActivity.class);
    when(activity.getApplicationContext()).thenReturn(app);
    when(activity.getSupportFragmentManager()).thenReturn(mock(FragmentManager.class));
    client = mock(HttpClient.class);
    config = mock(ConfigurationManager.class);
    registry = mock(SearchEngines.class);
    adapter = mock(AndroidSearchEngineAdapter.class);
    dialog = mock(SoftwareUpdaterDialog.class);
    system = mockStatic(SystemUtils.class);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              background.add(call.getArgument(1));
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            call -> {
              ui.add(call.getArgument(0));
              return null;
            });
    http = mockStatic(HttpClientFactory.class);
    http.when(() -> HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC))
        .thenReturn(client);
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    engines = mockStatic(AndroidSearchEngines.class);
    engines.when(AndroidSearchEngines::registry).thenReturn(registry);
    engines.when(AndroidSearchEngines::adapter).thenReturn(adapter);
    dialogs = mockStatic(SoftwareUpdaterDialog.class);
    dialogs.when(() -> SoftwareUpdaterDialog.newInstance(any(), any(), any())).thenReturn(dialog);
    offers = mockStatic(Offers.class);
    // The checker only runs every ten minutes. A failed notification rewinds that clock,
    // which is the supported way to make the next resume check again.
    dialogs
        .when(() -> SoftwareUpdaterDialog.newInstance(any(), any(), any()))
        .thenThrow(new IllegalStateException("rewind the update clock"));
    updater.notifyUserAboutUpdate(activity);
    dialogs.when(() -> SoftwareUpdaterDialog.newInstance(any(), any(), any())).thenReturn(dialog);
    org.mockito.Mockito.clearInvocations(activity);
  }

  @After
  public void tearDown() {
    offers.close();
    dialogs.close();
    engines.close();
    configStatic.close();
    http.close();
    system.close();
  }

  private void serve(String json) throws Exception {
    when(client.getBytes(eq(Constants.SERVER_UPDATE_URL), anyInt(), any(), any()))
        .thenReturn(json == null ? null : json.getBytes(StandardCharsets.UTF_8));
  }

  /** Runs the check the way MainActivity does: the background fetch, then the UI-thread post. */
  private void check() {
    background.clear();
    ui.clear();
    updater.checkForUpdate(activity);
    assertEquals(1, background.size());
    background.get(0).run();
    assertEquals(1, ui.size());
  }

  private static String newerVersionCode() {
    return String.valueOf(BuildConfig.VERSION_CODE + 1);
  }

  @Test
  public void aNewerReleaseAnnouncesTheUpdateAndAppliesTheRemoteConfiguration() throws Exception {
    SearchEngine known = mock(SearchEngine.class);
    when(known.getId()).thenReturn(SearchEngineID.YT_ID);
    when(registry.byName("YouTube")).thenReturn(known);
    when(registry.byName("Gone")).thenReturn(null);
    serve(
        "{\"vc\":\""
            + newerVersionCode()
            + "\",\"u\":\"https://example.invalid/fw.apk\",\"changelog\":[\"fixed\"],"
            + "\"updateMessages\":{\"en\":\"new\"},"
            + "\"config\":{\"activeSearchEngines\":{\"YouTube\":true,\"Gone\":false},"
            + "\"supportVpnThreshold\":150},"
            + "\"sc_client_id\":\"cid\",\"sc_app_version\":\"42\"}");
    check();
    verify(adapter).setActive(SearchEngineID.YT_ID, true);
    verify(config).setInt(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD, 50);
    offers.verify(() -> Offers.initAdNetworks(activity));
    assertEquals("cid", SoftwareUpdater.getSoundCloudClientId());
    assertEquals("42", SoftwareUpdater.getSoundCloudAppVersion());

    ui.get(0).run();
    verify(activity).updateNavigationMenu(true);
    verify(dialog).show(any(FragmentManager.class));
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    Intent broadcast = lastBroadcast;
    assertNotNull(broadcast);
    assertEquals(Constants.ACTION_NOTIFY_UPDATE_AVAILABLE, broadcast.getAction());
    assertTrue(broadcast.getBooleanExtra("value", false));
  }

  private Intent lastBroadcast;

  @Before
  public void listenForBroadcasts() {
    Application app = ApplicationProvider.getApplicationContext();
    LocalBroadcastManager.getInstance(app)
        .registerReceiver(
            new android.content.BroadcastReceiver() {
              @Override
              public void onReceive(android.content.Context context, Intent intent) {
                lastBroadcast = intent;
              }
            },
            new android.content.IntentFilter(Constants.ACTION_NOTIFY_UPDATE_AVAILABLE));
  }

  @Test
  public void anOlderOrEqualReleaseOnlyBroadcastsThatNothingIsNew() throws Exception {
    serve("{\"vc\":\"" + BuildConfig.VERSION_CODE + "\",\"u\":\"https://example.invalid/fw.apk\"}");
    check();
    ui.get(0).run();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertNotNull(lastBroadcast);
    assertFalse(lastBroadcast.getBooleanExtra("value", true));
    verify(dialog, never()).show(any(FragmentManager.class));
  }

  @Test
  public void aReleaseWithoutADownloadUrlIsNotAnUpdate() throws Exception {
    serve("{\"vc\":\"" + newerVersionCode() + "\"}");
    check();
    ui.get(0).run();
    verify(dialog, never()).show(any(FragmentManager.class));
  }

  @Test
  public void anUnparseableVersionCodeIsNotAnUpdate() throws Exception {
    serve("{\"vc\":\"not-a-number\",\"u\":\"https://example.invalid/fw.apk\"}");
    check();
    ui.get(0).run();
    verify(dialog, never()).show(any(FragmentManager.class));
  }

  @Test
  public void aMissingVersionCodeKeepsThePreviousVerdictAndStillAppliesTheConfig()
      throws Exception {
    serve("{\"config\":{\"supportVpnThreshold\":30}}");
    check();
    verify(config).setInt(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD, 30);
  }

  @Test
  public void negativeThresholdsFallBackToFifty() throws Exception {
    serve("{\"config\":{\"supportVpnThreshold\":-1}}");
    check();
    verify(config).setInt(Constants.PREF_KEY_GUI_SUPPORT_VPN_THRESHOLD, 50);
  }

  @Test
  public void anEngineMissingFromTheRegistryAndBlankCredentialsAreIgnored() throws Exception {
    when(registry.byName("Nope")).thenReturn(null);
    serve(
        "{\"config\":{\"activeSearchEngines\":{\"Nope\":true}},"
            + "\"sc_client_id\":\"   \",\"sc_app_version\":\"\"}");
    check();
    verify(adapter, never()).setActive(any(SearchEngineID.class), anyBoolean());
  }

  @Test
  public void anEngineSwitchedOffRemotelyIsDeactivated() throws Exception {
    SearchEngine engine = mock(SearchEngine.class);
    when(engine.getId()).thenReturn(SearchEngineID.SOUNDCLOUD_ID);
    when(registry.byName("SoundCloud")).thenReturn(engine);
    serve("{\"config\":{\"activeSearchEngines\":{\"SoundCloud\":false}}}");
    check();
    verify(adapter).setActive(SearchEngineID.SOUNDCLOUD_ID, false);
  }

  @Test
  public void anUnreachableServerLeavesTheCurrentStateAlone() throws Exception {
    serve(null);
    check();
    ui.get(0).run();
    verify(dialog, never()).show(any(FragmentManager.class));
    verify(config, never()).setInt(any(), anyInt());
  }

  @Test
  public void aBrokenResponseIsSurvived() throws Exception {
    serve("this is not json");
    check();
    ui.get(0).run();
    verify(dialog, never()).show(any(FragmentManager.class));
  }

  @Test
  public void checksAreThrottledToOncePerTenMinutes() throws Exception {
    serve("{}");
    check();
    background.clear();
    updater.checkForUpdate(activity);
    assertTrue("the second check within ten minutes is skipped", background.isEmpty());
  }

  @Test
  public void aFailedNotificationLetsTheNextResumeCheckAgain() {
    dialogs
        .when(() -> SoftwareUpdaterDialog.newInstance(any(), any(), any()))
        .thenThrow(new IllegalStateException("no fragment manager"));
    updater.notifyUserAboutUpdate(activity);
    background.clear();
    updater.checkForUpdate(activity);
    assertEquals(1, background.size());
  }
}
