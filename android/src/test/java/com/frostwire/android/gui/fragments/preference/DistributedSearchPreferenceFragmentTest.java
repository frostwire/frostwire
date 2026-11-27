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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.AndroidSearchEngines;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.fragments.FragmentTestHost;
import com.frostwire.android.gui.services.EngineForegroundService;
import com.frostwire.android.search.AndroidRelayStack;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityLifecycle;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.RelaySearchWiring;
import com.frostwire.search.relay.icebridge.IceBridgeHostCache;
import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

/**
 * The Distributed Search screen with libtorrent and the relay stack replaced: identity actions, the
 * status rows, the peers and the IceBridge hosts. Background work (posted to handler threads) runs
 * right away on the test thread, where the mocks are.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class DistributedSearchPreferenceFragmentTest {
  private static final String KEY = "frostwire.prefs.distributed.identity.";

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  private MockedStatic<ConfigurationRepository> repository;
  private MockedStatic<ConfigurationManager> configurations;
  private MockedStatic<SystemUtils> utils;
  private MockedStatic<AndroidSearchEngines> engines;
  private MockedStatic<IdentityLifecycle> lifecycle;
  private MockedStatic<EngineForegroundService> services;
  private MockedStatic<AndroidRelayStack> stacks;
  private MockedStatic<IceBridgeHostCache> hostCaches;
  private MockedStatic<NetworkManager> networks;
  private MockedStatic<Platforms> platforms;
  private Platform platform;
  private ConfigurationManager configuration;
  private RelaySearchWiring wiring;
  private IdentityKeys keys;
  private IceBridgeHostCache hostCache;
  private File home;
  private EngineForegroundService service;

  @Before
  public void setUp() throws IOException {
    home = temp.newFolder("libtorrent");
    configuration = mock(ConfigurationManager.class);
    wiring = new RelaySearchWiring();
    keys = mock(IdentityKeys.class);
    when(keys.nodeId()).thenReturn(new byte[] {1, 2, 3, 4});
    when(keys.ed25519PubRaw()).thenReturn(new byte[] {9, 8, 7, 6});
    hostCache = mock(IceBridgeHostCache.class);
    repository = mockStatic(ConfigurationRepository.class);
    platform = FragmentTestHost.newPlatform(home);
    platforms = mockStatic(Platforms.class);
    platforms.when(Platforms::get).thenAnswer(invocation -> platform);
    configurations = mockStatic(ConfigurationManager.class);
    utils = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    engines = mockStatic(AndroidSearchEngines.class);
    lifecycle = mockStatic(IdentityLifecycle.class);
    services = mockStatic(EngineForegroundService.class);
    stacks = mockStatic(AndroidRelayStack.class);
    hostCaches = mockStatic(IceBridgeHostCache.class);
    networks = mockStatic(NetworkManager.class);
    configurations.when(ConfigurationManager::instance).thenReturn(configuration);
    engines.when(AndroidSearchEngines::distributed).thenReturn(wiring);
    engines.when(AndroidSearchEngines::local).thenReturn(new RelaySearchWiring());
    hostCaches.when(IceBridgeHostCache::getInstance).thenReturn(hostCache);
    when(hostCache.getAll()).thenReturn(Collections.emptyList());
    when(hostCache.getPingable(org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(Collections.emptyList());
    utils
        .when(() -> SystemUtils.postToHandler(any(), any()))
        .thenAnswer(
            invocation -> {
              ((Runnable) invocation.getArgument(1)).run();
              return null;
            });
    lifecycle
        .when(() -> IdentityLifecycle.resolve(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lifecycle
        .when(() -> IdentityLifecycle.formatGroupedHex(anyString()))
        .thenAnswer(i -> "G" + i.getArgument(0));
    lifecycle.when(() -> IdentityLifecycle.difficultyBits(any())).thenReturn(22);
    lifecycle.when(() -> IdentityLifecycle.meetsDifficultyRequirement(any())).thenReturn(true);
    services.when(EngineForegroundService::getInstance).thenAnswer(i -> service);
    stacks.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
  }

  @After
  public void tearDown() {
    networks.close();
    platforms.close();
    hostCaches.close();
    stacks.close();
    services.close();
    lifecycle.close();
    engines.close();
    utils.close();
    configurations.close();
    repository.close();
  }

  private ActivityController<FragmentTestHost> controller;

  private DistributedSearchPreferenceFragment launch() {
    DistributedSearchPreferenceFragment fragment = new DistributedSearchPreferenceFragment();
    controller = FragmentTestHost.launch(fragment, home);
    idle();
    return fragment;
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  private static String summary(DistributedSearchPreferenceFragment f, String key) {
    Preference p = f.findPreference(key);
    assertNotNull(key, p);
    return p.getSummary() == null ? null : p.getSummary().toString();
  }

  private static void press(DistributedSearchPreferenceFragment f, int textRes) {
    android.widget.Button button = PreferenceTestSupport.button(f, f.getString(textRes));
    assertNotNull("no button " + f.getString(textRes), button);
    button.performClick();
    idle();
  }

  private AlertDialog latestDialog() {
    return (AlertDialog) ShadowDialog.getLatestDialog();
  }

  private void confirm() {
    AlertDialog dialog = latestDialog();
    assertNotNull(dialog);
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
  }

  private static String toastText() {
    return ShadowToast.getTextOfLatestToast();
  }

  private String clip() {
    Context app = ApplicationProvider.getApplicationContext();
    ClipboardManager cm = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData data = cm.getPrimaryClip();
    return data == null ? null : data.getItemAt(0).getText().toString();
  }

  @Test
  public void withoutAnIdentityTheRowsSayNotInitialized() {
    DistributedSearchPreferenceFragment fragment = launch();
    String notInitialized = fragment.getString(R.string.distributed_identity_not_initialized);
    assertEquals(notInitialized, summary(fragment, KEY + "node_id"));
    assertEquals(notInitialized, summary(fragment, KEY + "fingerprint"));
    assertEquals(notInitialized, summary(fragment, KEY + "public_key"));
    assertEquals(notInitialized, summary(fragment, KEY + "difficulty"));
    assertEquals(
        fragment.getString(R.string.distributed_identity_not_available),
        summary(fragment, KEY + "karma"));
    assertEquals(
        fragment.getString(R.string.distributed_identity_initialize),
        fragment.findPreference(KEY + "initialize").getTitle().toString());
    // and none of the copy rows copy that
    Preference nodeId = fragment.findPreference(KEY + "node_id");
    assertTrue(nodeId.getOnPreferenceClickListener().onPreferenceClick(nodeId));
    assertNull(clip());
  }

  @Test
  public void anIdentityShowsItsRowsAndTheyCopyWithoutTheHelperLine() {
    wiring.identity(keys);
    DistributedSearchPreferenceFragment fragment = launch();
    String tap = fragment.getString(R.string.distributed_identity_tap_to_copy);
    assertEquals("G01020304\n" + tap, summary(fragment, KEY + "node_id"));
    assertEquals("G09080706\n" + tap, summary(fragment, KEY + "fingerprint"));
    assertEquals("09080706\n" + tap, summary(fragment, KEY + "public_key"));
    assertEquals(
        fragment.getString(R.string.distributed_identity_difficulty_meets, 22),
        summary(fragment, KEY + "difficulty"));
    assertEquals(
        fragment.getString(R.string.distributed_identity_shared_torrents, 0),
        summary(fragment, KEY + "shared_count"));
    assertEquals(
        fragment.getString(R.string.distributed_identity_create_new),
        fragment.findPreference(KEY + "initialize").getTitle().toString());

    Preference nodeId = fragment.findPreference(KEY + "node_id");
    nodeId.getOnPreferenceClickListener().onPreferenceClick(nodeId);
    // grouped hex has its spaces stripped
    assertEquals("G01020304", clip());
    assertEquals(fragment.getString(R.string.distributed_identity_copied), toastText());

    Preference karma = fragment.findPreference(KEY + "karma");
    karma.setSummary("5\nTap to copy");
    karma.getOnPreferenceClickListener().onPreferenceClick(karma);
    assertEquals("5", clip());
  }

  @Test
  public void anIdentityBelowTheRequiredDifficultyIsSaidSo() {
    wiring.identity(keys);
    lifecycle.when(() -> IdentityLifecycle.meetsDifficultyRequirement(any())).thenReturn(false);
    DistributedSearchPreferenceFragment fragment = launch();
    assertTrue(
        summary(fragment, KEY + "difficulty")
            .startsWith(
                fragment
                    .getString(R.string.distributed_identity_difficulty_below, 22, 0)
                    .substring(0, 5)));
  }

  @Test
  public void aMissingLibtorrentHomeKeepsLoadingUntilTheRetriesRunOut() {
    DistributedSearchPreferenceFragment fragment = new DistributedSearchPreferenceFragment();
    platform = FragmentTestHost.newPlatform(null);
    ActivityController<FragmentTestHost> host = FragmentTestHost.launch(fragment);
    idle();
    assertEquals(
        fragment.getString(R.string.distributed_identity_loading),
        summary(fragment, KEY + "node_id"));
    // the periodic refresh would start retry chains of its own
    host.pause();
    // each retry is 300 ms later; after 20 of them the row gives up and says not initialized
    String notInitialized = fragment.getString(R.string.distributed_identity_not_initialized);
    int retries = 0;
    while (retries < 40 && !notInitialized.equals(summary(fragment, KEY + "node_id"))) {
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
      retries++;
    }
    assertTrue("gave up after " + retries, retries >= 20 && retries < 40);
    assertEquals(notInitialized, summary(fragment, KEY + "node_id"));
  }

  @Test
  public void anIdentityFileThatCannotBeLoadedStaysOnLoading() throws IOException {
    assertTrue(
        new File(home, com.frostwire.search.relay.RelayConstants.IDENTITY_FILE).createNewFile());
    new java.io.FileOutputStream(
            new File(home, com.frostwire.search.relay.RelayConstants.IDENTITY_FILE))
        .write(new byte[] {1, 2, 3});
    DistributedSearchPreferenceFragment fragment = launch();
    assertEquals(
        fragment.getString(R.string.distributed_identity_loading),
        summary(fragment, KEY + "node_id"));
  }

  @Test
  public void initializingCreatesTheIdentityAfterConfirming() {
    lifecycle.when(() -> IdentityLifecycle.generateAndInstall(any(), anyInt())).thenReturn(keys);
    DistributedSearchPreferenceFragment fragment = launch();

    press(fragment, R.string.distributed_identity_initialize);
    // the confirmation: the message is the initialize one
    confirm();

    assertSame(keys, wiring.identity());
    assertEquals(fragment.getString(R.string.distributed_identity_init_ok), toastText());
    assertTrue(summary(fragment, KEY + "node_id").startsWith("G01020304"));
    lifecycle.verify(() -> IdentityLifecycle.generateAndInstall(any(File.class), anyInt()));
  }

  @Test
  public void aFailedInitializationToastsTheFailure() {
    lifecycle
        .when(() -> IdentityLifecycle.generateAndInstall(any(), anyInt()))
        .thenThrow(new IllegalStateException("disk full"));
    DistributedSearchPreferenceFragment fragment = launch();

    press(fragment, R.string.distributed_identity_initialize);
    confirm();

    assertNull(wiring.identity());
    assertEquals(
        fragment.getString(R.string.distributed_identity_failed, "disk full"), toastText());
  }

  @Test
  public void anInitializationWithoutAHomeDirectoryFails() {
    DistributedSearchPreferenceFragment fragment = new DistributedSearchPreferenceFragment();
    platform = FragmentTestHost.newPlatform(null);
    FragmentTestHost.launch(fragment);
    idle();
    press(fragment, R.string.distributed_identity_initialize);
    confirm();
    assertEquals(
        fragment.getString(
            R.string.distributed_identity_failed, "libtorrent homeDir not available"),
        toastText());
  }

  @Test
  public void showingTheSeedPhraseNeedsAnIdentity() {
    DistributedSearchPreferenceFragment fragment = launch();
    press(fragment, R.string.distributed_identity_show_seed);
    assertEquals(fragment.getString(R.string.distributed_identity_no_identity), toastText());

    wiring.identity(keys);
    lifecycle.when(() -> IdentityLifecycle.seedPhrase(any())).thenReturn("alpha beta gamma");
    press(fragment, R.string.distributed_identity_show_seed);
    AlertDialog dialog = latestDialog();
    assertTrue(dialog.isShowing());
    // copy puts the phrase on the clipboard
    dialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
    idle();
    assertEquals("alpha beta gamma", clip());
  }

  @Test
  public void aSeedPhraseThatCannotBeShownIsReported() {
    wiring.identity(keys);
    lifecycle
        .when(() -> IdentityLifecycle.seedPhrase(any()))
        .thenThrow(new IllegalArgumentException("no entropy"));
    DistributedSearchPreferenceFragment fragment = launch();
    press(fragment, R.string.distributed_identity_show_seed);
    assertEquals(
        fragment.getString(R.string.distributed_identity_failed, "no entropy"), toastText());
  }

  @Test
  public void restoringFromASeedPhraseInstallsTheIdentity() {
    lifecycle
        .when(() -> IdentityLifecycle.restoreFromSeedPhrase(anyString(), any()))
        .thenReturn(keys);
    DistributedSearchPreferenceFragment fragment = launch();

    press(fragment, R.string.distributed_identity_restore_seed);
    confirm(); // "this replaces your identity"
    AlertDialog prompt = latestDialog();
    EditText input = (EditText) findEditText(prompt);
    input.setText("one two three");
    prompt.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();

    assertSame(keys, wiring.identity());
    assertEquals(fragment.getString(R.string.distributed_identity_restore_ok), toastText());
    lifecycle.verify(() -> IdentityLifecycle.restoreFromSeedPhrase(eq("one two three"), any()));
  }

  @Test
  public void aBadSeedPhraseIsReported() {
    lifecycle
        .when(() -> IdentityLifecycle.restoreFromSeedPhrase(anyString(), any()))
        .thenThrow(new IllegalArgumentException("bad word"));
    DistributedSearchPreferenceFragment fragment = launch();
    press(fragment, R.string.distributed_identity_restore_seed);
    confirm();
    AlertDialog prompt = latestDialog();
    ((EditText) findEditText(prompt)).setText("nope");
    prompt.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    assertEquals(
        fragment.getString(R.string.distributed_identity_invalid_seed, "bad word"), toastText());
  }

  private static android.view.View findEditText(AlertDialog dialog) {
    return findEditText(dialog.getWindow().getDecorView());
  }

  private static android.view.View findEditText(android.view.View view) {
    if (view instanceof EditText) {
      return view;
    }
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.view.View found = findEditText(group.getChildAt(i));
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  @Test
  public void exportingNeedsAnIdentityAndWritesItsBytesToThePickedFile() {
    DistributedSearchPreferenceFragment fragment = launch();
    press(fragment, R.string.distributed_identity_export);
    assertEquals(fragment.getString(R.string.distributed_identity_no_identity), toastText());

    wiring.identity(keys);
    byte[] payload = new byte[100];
    Arrays.fill(payload, (byte) 7);
    lifecycle.when(() -> IdentityLifecycle.exportBytes(any())).thenReturn(payload);
    press(fragment, R.string.distributed_identity_export);
    Uri target = Uri.parse("content://test.documents/identity.dat");
    ByteArrayOutputStream written = new ByteArrayOutputStream();
    resolver().registerOutputStream(target, written);
    deliverResult(Uri.class, target);
    assertEquals(100, written.size());
    assertEquals(fragment.getString(R.string.distributed_identity_export_ok), toastText());
  }

  @Test
  public void anExportOfAnIdentityThatIsTooSmallFails() {
    wiring.identity(keys);
    lifecycle.when(() -> IdentityLifecycle.exportBytes(any())).thenReturn(new byte[10]);
    DistributedSearchPreferenceFragment fragment = launch();
    press(fragment, R.string.distributed_identity_export);
    Uri target = Uri.parse("content://test.documents/small.dat");
    resolver().registerOutputStream(target, new ByteArrayOutputStream());
    deliverResult(Uri.class, target);
    assertEquals(
        fragment.getString(R.string.distributed_identity_failed, "encoded identity too small: 10"),
        toastText());
  }

  @Test
  public void importingReplacesTheIdentityFromThePickedFile() {
    lifecycle.when(() -> IdentityLifecycle.importFromFile(any(), any())).thenReturn(keys);
    DistributedSearchPreferenceFragment fragment = launch();
    press(fragment, R.string.distributed_identity_import);
    confirm();
    Uri source = Uri.parse("content://test.documents/in.dat");
    resolver().registerInputStream(source, new ByteArrayInputStream(new byte[200]));
    deliverResult(Uri.class, source);

    assertSame(keys, wiring.identity());
    assertEquals(fragment.getString(R.string.distributed_identity_import_ok), toastText());
  }

  @Test
  public void anImportedFileThatIsTooSmallFails() {
    DistributedSearchPreferenceFragment fragment = launch();
    press(fragment, R.string.distributed_identity_import);
    confirm();
    Uri source = Uri.parse("content://test.documents/tiny.dat");
    resolver().registerInputStream(source, new ByteArrayInputStream(new byte[10]));
    deliverResult(Uri.class, source);
    assertEquals(
        fragment.getString(R.string.distributed_identity_failed, "import file too small: 10 bytes"),
        toastText());
  }

  private static ShadowContentResolver resolver() {
    Context app = ApplicationProvider.getApplicationContext();
    return shadowOf(app.getContentResolver());
  }

  /** The picker's answer: delivered the way the framework does, to the registered callback. */
  private void deliverResult(Class<?> type, Uri uri) {
    ShadowActivity shadow = shadowOf((Activity) controller.get());
    ShadowActivity.IntentForResult started = shadow.getNextStartedActivityForResult();
    assertNotNull("the picker was not opened", started);
    Intent data = new Intent().setData(uri);
    shadow.receiveResult(started.intent, Activity.RESULT_OK, data);
    idle();
  }

  @Test
  public void copyingTheWholeIdentityNeedsOneAndCopiesItsSummary() {
    DistributedSearchPreferenceFragment fragment = launch();
    press(fragment, R.string.distributed_identity_copy_all);
    assertEquals(fragment.getString(R.string.distributed_identity_no_identity), toastText());

    wiring.identity(keys);
    lifecycle
        .when(() -> IdentityLifecycle.summaryText(any(), anyString(), anyInt()))
        .thenReturn("the summary");
    press(fragment, R.string.distributed_identity_copy_all);
    assertEquals("the summary", clip());
  }

  @Test
  public void thePeersListShowsVerifiedAndUnverifiedPeers() {
    PeerDirectory directory = mock(PeerDirectory.class);
    PeerDirectory.PeerInfo verified = peer("alpha.example", 6881, 6888, true);
    PeerDirectory.PeerInfo unverified = peer("", 6882, 0, false);
    when(directory.size()).thenReturn(2);
    when(directory.topByTrustVerified(50)).thenReturn(Collections.singletonList(verified));
    when(directory.topByTrust(50)).thenReturn(Arrays.asList(verified, unverified));
    wiring.peerDirectory(directory);
    DistributedSearchPreferenceFragment fragment = launch();

    assertEquals(
        fragment.getString(R.string.distributed_peers_count, 1, 2),
        summary(fragment, "frostwire.prefs.distributed.peers.count"));
    PreferenceCategory list = fragment.findPreference("frostwire.prefs.distributed.peers.list");
    assertEquals(2, list.getPreferenceCount());
    assertEquals(
        fragment.getString(R.string.distributed_peers_row, "alpha.example", 6881, 6888),
        list.getPreference(0).getTitle().toString());
    assertEquals(
        fragment.getString(R.string.distributed_peers_row_unverified, "(unknown)", 6882),
        list.getPreference(1).getTitle().toString());
  }

  private static PeerDirectory.PeerInfo peer(String host, int utp, int rudp, boolean verified) {
    PeerDirectory.PeerInfo p = mock(PeerDirectory.PeerInfo.class);
    when(p.hostname()).thenReturn(host);
    when(p.utpPort()).thenReturn(utp);
    when(p.rudpPort()).thenReturn(rudp);
    when(p.isVerified()).thenReturn(verified);
    when(p.peerPub()).thenReturn(new byte[32]);
    return p;
  }

  @Test
  public void theEmptyAndMissingPeerDirectoriesSaySo() {
    DistributedSearchPreferenceFragment fragment = launch();
    assertEquals(
        fragment.getString(R.string.distributed_peers_not_available),
        summary(fragment, "frostwire.prefs.distributed.peers.count"));

    PeerDirectory directory = mock(PeerDirectory.class);
    when(directory.size()).thenReturn(0);
    wiring.peerDirectory(directory);
    press(fragment, R.string.distributed_peers_refresh);
    assertEquals(
        fragment.getString(R.string.distributed_peers_empty),
        summary(fragment, "frostwire.prefs.distributed.peers.count"));
    PreferenceCategory list = fragment.findPreference("frostwire.prefs.distributed.peers.list");
    assertEquals(1, list.getPreferenceCount());
  }

  @Test
  public void theStackStatusFollowsTheService() {
    DistributedSearchPreferenceFragment fragment = launch();
    String status = "frostwire.prefs.distributed.stack.status";
    // no service
    assertEquals(
        fragment.getString(R.string.distributed_stack_no_service), summary(fragment, status));

    // a service whose stack isn't running: why it isn't, here disabled
    service = mock(EngineForegroundService.class);
    controller.pause().resume();
    idle();
    assertEquals(fragment.getString(R.string.disabled), summary(fragment, status));

    // enabled but the network isn't allowed: the VPN guard says it
    when(configuration.getBoolean(Constants.PREF_KEY_ICEBRIDGE_ENABLED)).thenReturn(true);
    stacks.when(AndroidRelayStack::isNetworkAllowed).thenReturn(false);
    NetworkManager network = mock(NetworkManager.class);
    networks.when(NetworkManager::instance).thenReturn(network);
    when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY))
        .thenReturn(true);
    controller.pause().resume();
    idle();
    assertEquals(
        fragment.getString(R.string.cannot_start_engine_without_vpn), summary(fragment, status));

    // or the wifi-only guard
    when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY))
        .thenReturn(false);
    when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(true);
    controller.pause().resume();
    idle();
    assertEquals(fragment.getString(R.string.wifi_network_unavailable), summary(fragment, status));

    // or just no connection
    when(configuration.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(false);
    controller.pause().resume();
    idle();
    assertEquals(
        fragment.getString(R.string.no_data_check_internet_connection), summary(fragment, status));

    // network allowed, but starting: the startup tracker's say (none yet)
    stacks.when(AndroidRelayStack::isNetworkAllowed).thenReturn(true);
    controller.pause().resume();
    idle();
    assertEquals(
        fragment.getString(R.string.distributed_stack_no_service), summary(fragment, status));
  }

  @Test
  public void aRunningStackShowsItsPortsAndRole() {
    service = mock(EngineForegroundService.class);
    AndroidRelayStack stack = mock(AndroidRelayStack.class);
    IceBridgeServer server = mock(IceBridgeServer.class);
    when(server.rudpPort()).thenReturn(7001);
    when(server.controlPort()).thenReturn(7002);
    when(stack.server()).thenReturn(server);
    when(service.getRelayStack()).thenReturn(stack);
    wiring.searchTransport(mock(DistributedSearchTransport.class));
    when(configuration.getString(Constants.PREF_KEY_ICEBRIDGE_ROLE)).thenReturn("CLIENT");
    when(configuration.getString(Constants.PREF_KEY_ICEBRIDGE_RUDP_PORT)).thenReturn("7000");
    DistributedSearchPreferenceFragment fragment = launch();

    assertEquals(
        fragment.getString(R.string.distributed_stack_running, 7001, 7002, "CLIENT"),
        summary(fragment, "frostwire.prefs.distributed.stack.status"));
    assertTrue(summary(fragment, Constants.PREF_KEY_ICEBRIDGE_RUDP_PORT).endsWith("7000 → 7001"));
    assertEquals(
        "CLIENT",
        ((ListPreference) fragment.findPreference(Constants.PREF_KEY_ICEBRIDGE_ROLE)).getValue());
  }

  @Test
  public void thePortAndRolePreferencesValidateWhatIsPicked() {
    DistributedSearchPreferenceFragment fragment = launch();
    EditTextPreference rudp = fragment.findPreference(Constants.PREF_KEY_ICEBRIDGE_RUDP_PORT);
    ListPreference role = fragment.findPreference(Constants.PREF_KEY_ICEBRIDGE_ROLE);

    assertFalse(PreferenceTestSupport.change(rudp, "99999"));
    assertFalse(PreferenceTestSupport.change(rudp, "abc"));
    assertTrue(PreferenceTestSupport.change(rudp, "7000"));
    verify(configuration).setString(Constants.PREF_KEY_ICEBRIDGE_RUDP_PORT, "7000");
    assertTrue(rudp.getSummary().toString().endsWith("7000"));
    assertEquals(fragment.getString(R.string.distributed_icebridge_ports_changed), toastText());

    assertFalse(PreferenceTestSupport.change(role, "BOSS"));
    assertTrue(PreferenceTestSupport.change(role, "forwarder"));
    verify(configuration).setString(Constants.PREF_KEY_ICEBRIDGE_ROLE, "FORWARDER");
    assertTrue(PreferenceTestSupport.change(role, null));
    verify(configuration).setString(Constants.PREF_KEY_ICEBRIDGE_ROLE, "BOTH");
  }

  @Test
  public void theToggleExplainsWhatTheStackWillDo() {
    DistributedSearchPreferenceFragment fragment = launch();
    SwitchPreference toggle = fragment.findPreference(Constants.PREF_KEY_ICEBRIDGE_ENABLED);
    assertEquals(
        fragment.getString(R.string.distributed_stack_not_running), toggle.getSummary().toString());
    assertTrue(PreferenceTestSupport.change(toggle, true));
    assertEquals(fragment.getString(R.string.icebridge_join_note), toggle.getSummary().toString());
    assertTrue(PreferenceTestSupport.change(toggle, false));
    assertEquals(
        fragment.getString(R.string.distributed_stack_not_running), toggle.getSummary().toString());
  }

  @Test
  public void startingTheStackRestartsItInTheService() {
    DistributedSearchPreferenceFragment fragment = launch();
    // no service
    press(fragment, R.string.distributed_icebridge_start);
    assertEquals(fragment.getString(R.string.distributed_stack_no_service), toastText());

    service = mock(EngineForegroundService.class);
    when(service.isRelayStackRunning()).thenReturn(true);
    press(fragment, R.string.distributed_icebridge_start);
    org.mockito.ArgumentCaptor<Runnable> done = org.mockito.ArgumentCaptor.forClass(Runnable.class);
    verify(service).ensureRelayStack(eq(true), done.capture());
    done.getValue().run();
    assertEquals(fragment.getString(R.string.distributed_icebridge_start_ok), toastText());

    when(service.isRelayStackRunning()).thenReturn(false);
    done.getValue().run();
    assertEquals(fragment.getString(R.string.distributed_identity_init_failed), toastText());
  }

  @Test
  public void theHostsListShowsTheCachedIceBridgeHosts() {
    when(hostCache.getAll())
        .thenReturn(
            Arrays.asList(
                new IceBridgeHostCache.Entry("a.example", 6888, "BOTH", 0),
                new IceBridgeHostCache.Entry("b.example", 6889, null, 1_700_000_000_000L)));
    DistributedSearchPreferenceFragment fragment = launch();
    PreferenceCategory list =
        fragment.findPreference("frostwire.prefs.distributed.icebridge.hosts_list");
    assertEquals(2, list.getPreferenceCount());
    assertEquals("a.example:6888 · BOTH", list.getPreference(0).getTitle().toString());
    assertEquals(
        fragment.getString(
            R.string.distributed_icebridge_host_row,
            "BOTH",
            fragment.getString(R.string.distributed_icebridge_host_never)),
        list.getPreference(0).getSummary().toString());
    assertEquals("b.example:6889 · ?", list.getPreference(1).getTitle().toString());
  }

  @Test
  public void refreshingTheHostsPingsThemAndTheMesh() {
    service = mock(EngineForegroundService.class);
    AndroidRelayStack stack = mock(AndroidRelayStack.class);
    IceBridgeClient client = mock(IceBridgeClient.class);
    when(stack.client()).thenReturn(client);
    when(service.getRelayStack()).thenReturn(stack);
    DistributedSearchPreferenceFragment fragment = launch();
    press(fragment, R.string.distributed_icebridge_hosts_refresh);
    verify(hostCache).refreshPings();
    verify(hostCache).refreshMeshTelemetry(client);
    assertEquals(fragment.getString(R.string.distributed_icebridge_pinging), toastText());
  }

  @Test
  public void aFailingPingIsNotFatal() {
    org.mockito.Mockito.doThrow(new IllegalStateException("offline"))
        .when(hostCache)
        .refreshPings();
    DistributedSearchPreferenceFragment fragment = launch();
    // no service: the mesh refresh is skipped
    press(fragment, R.string.distributed_icebridge_hosts_refresh);
    verify(hostCache).refreshPings();
  }

  @Test
  public void theScreenRefreshesItselfWhileItIsVisibleAndStopsWhenPaused() {
    DistributedSearchPreferenceFragment fragment = launch();
    wiring.identity(keys);
    // the third tick refreshes everything, including the identity
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
    assertTrue(summary(fragment, KEY + "node_id").startsWith("G01020304"));

    controller.pause();
    wiring.identity(null);
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
    assertTrue(summary(fragment, KEY + "node_id").startsWith("G01020304"));
  }
}
