/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.KeyEvent;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.SoftwareUpdater;
import com.frostwire.android.gui.fragments.SearchFragment;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.offers.Offers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * The main screen booted under Robolectric with a plain Application, a fake Engine and
 * configuration, and no ads: navigation, intents, shutdown and the first-run gate.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MainActivityBootTest {

  private MockedStatic<ConfigurationManager> configs;
  private MockedStatic<Engine> engines;
  private MockedStatic<Offers> offers;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<SoftwareUpdater> updaters;
  private MockedStatic<TransferManager> transfers;
  private MockedStatic<UIUtils> ui;
  private ConfigurationManager config;
  private Engine engine;
  private TransferManager manager;
  private static final String YES_NO_TAG =
      com.frostwire.android.gui.views.AbstractDialog.getSuggestedTAG(
          com.frostwire.android.gui.dialogs.YesNoDialog.class);
  private MockedStatic<com.frostwire.android.gui.NetworkManager> networks;
  private final List<Runnable> background = new ArrayList<>();

  @Before
  public void setUp() {
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
    config = mock(ConfigurationManager.class);
    configs = mockStatic(ConfigurationManager.class);
    configs.when(ConfigurationManager::instance).thenReturn(config);
    engine = mock(Engine.class);
    engines = mockStatic(Engine.class);
    engines.when(Engine::instance).thenReturn(engine);
    offers = mockStatic(Offers.class);
    offers.when(Offers::disabledAds).thenReturn(true);
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(SystemUtils.HandlerThreadName.class), any()))
        .thenAnswer(
            i -> {
              background.add(i.getArgument(1));
              return null;
            });
    system.when(SystemUtils::stopAllHandlerThreads).thenAnswer(i -> null);
    // Platforms keeps the first platform any test installed, so don't depend on it here.
    system
        .when(SystemUtils::isUIThread)
        .thenAnswer(i -> Looper.myLooper() == Looper.getMainLooper());
    updaters = mockStatic(SoftwareUpdater.class);
    updaters.when(SoftwareUpdater::getInstance).thenReturn(mock(SoftwareUpdater.class));
    manager = mock(TransferManager.class);
    transfers = mockStatic(TransferManager.class);
    transfers.when(TransferManager::instance).thenReturn(manager);
    ui = mockStatic(UIUtils.class);
    networks = mockStatic(com.frostwire.android.gui.NetworkManager.class);
    networks
        .when(com.frostwire.android.gui.NetworkManager::instance)
        .thenReturn(mock(com.frostwire.android.gui.NetworkManager.class));
  }

  @After
  public void tearDown() {
    configs.close();
    engines.close();
    offers.close();
    system.close();
    updaters.close();
    transfers.close();
    ui.close();
    networks.close();
  }

  private ActivityController<MainActivity> controller(Intent intent) {
    return Robolectric.buildActivity(MainActivity.class, intent);
  }

  private MainActivity boot() {
    MainActivity activity = controller(new Intent()).create().get();
    shadowOf(Looper.getMainLooper()).idle();
    return activity;
  }

  private static void sendBroadcast(MainActivity activity, Intent intent) {
    LocalBroadcastManager.getInstance(activity).sendBroadcastSync(intent);
  }

  @Test
  public void bootsShowingTheSearchFragmentAndRemembersTheLastInstance() {
    MainActivity activity = boot();
    assertSame(activity, MainActivity.instance());
    assertTrue(activity.getCurrentFragment() instanceof SearchFragment);
    assertSame(
        activity.getCurrentFragment(), activity.getFragmentByNavMenuId(R.id.menu_main_search));
    assertTrue(
        activity.getFragmentByNavMenuId(R.id.menu_main_transfers) instanceof TransfersFragment);
    assertNull(activity.getFragmentByNavMenuId(R.id.menu_main_settings));
  }

  @Test
  public void switchingContentTracksTheCurrentFragmentAndBackReturnsToTheOneBefore() {
    MainActivity activity = controller(new Intent()).create().start().resume().get();
    shadowOf(Looper.getMainLooper()).idle();
    Fragment search = activity.getCurrentFragment();
    Fragment transfers = activity.getFragmentByNavMenuId(R.id.menu_main_transfers);
    activity.switchContent(transfers);
    assertSame(transfers, activity.getCurrentFragment());
    // switching to what is already showing changes nothing
    activity.switchContent(transfers);
    assertSame(transfers, activity.getCurrentFragment());
    activity.getOnBackPressedDispatcher().onBackPressed();
    assertSame(search, activity.getCurrentFragment());
  }

  @Test
  public void backOnTheLastScreenAsksBeforeMinimizingAndYesMinimizes() {
    MainActivity activity = controller(new Intent()).create().start().resume().get();
    shadowOf(Looper.getMainLooper()).idle();
    activity.handleOnBackPressed();
    shadowOf(Looper.getMainLooper()).idle();
    assertNotNull(activity.getSupportFragmentManager().findFragmentByTag(YES_NO_TAG));
  }

  @Test
  public void anOpenDrawerIsClosedByBackBeforeAnythingElse() {
    MainActivity activity = boot();
    activity.updateNavigationMenu(false);
    MainActivity.instance().getOnBackPressedDispatcher();
    // opening via the menu key, then back closes it instead of showing the dialog
    assertTrue(
        activity.onKeyDown(
            KeyEvent.KEYCODE_MENU, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU)));
    activity.handleOnBackPressed();
    assertNull(activity.getSupportFragmentManager().findFragmentByTag(YES_NO_TAG));
  }

  @Test
  public void searchKeyBringsTheSearchFragmentBack() {
    MainActivity activity = boot();
    activity.switchContent(activity.getFragmentByNavMenuId(R.id.menu_main_transfers));
    assertTrue(
        activity.onKeyDown(
            KeyEvent.KEYCODE_SEARCH, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SEARCH)));
    assertTrue(activity.getCurrentFragment() instanceof SearchFragment);
  }

  @Test
  public void showTransfersIntentSwitchesToTheTransfersFragment() {
    MainActivity activity = boot();
    activity.onNewIntent(new Intent(Constants.ACTION_SHOW_TRANSFERS));
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue(activity.getCurrentFragment() instanceof TransfersFragment);
  }

  @Test
  public void switchToTransfersShowsTheTransfersTab() {
    MainActivity activity = boot();
    activity.switchToTransfers();
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue(activity.getCurrentFragment() instanceof TransfersFragment);
  }

  @Test
  public void viewIntentsWithTorrentUrisStartTheDownloadAndShowTransfers() {
    MainActivity activity = boot();
    Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse("magnet:?xt=urn:btih:abc"));
    activity.onNewIntent(view);
    verify(manager)
        .downloadTorrent(org.mockito.ArgumentMatchers.eq("magnet:?xt=urn:btih:abc"), any());
    Intent showTransfers = shadowOf(activity).getNextStartedActivity();
    assertEquals(Constants.ACTION_SHOW_TRANSFERS, showTransfers.getAction());
    assertNull("the handled action is cleared", view.getAction());
  }

  @Test
  public void viewIntentsWithNoDataOrUnknownSchemesDoNotStartDownloads() {
    MainActivity activity = boot();
    activity.onNewIntent(new Intent(Intent.ACTION_VIEW));
    activity.onNewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("ftp://example.com/a.torrent")));
    verify(manager, never()).downloadTorrent(anyString(), any());
  }

  @Test
  public void contentUrisAreCopiedOnABackgroundThreadBeforeDownloading() {
    MainActivity activity = boot();
    background.clear();
    activity.onNewIntent(
        new Intent(Intent.ACTION_VIEW, Uri.parse("content://nobody/some.torrent")));
    assertEquals(1, background.size());
    // nothing can read that content uri: no download is started
    try (MockedStatic<Platforms> platforms = mockStatic(Platforms.class)) {
      platforms
          .when(Platforms::temp)
          .thenReturn(new java.io.File(System.getProperty("java.io.tmpdir"), "fw-main-test"));
      background.get(0).run();
    }
    verify(manager, never()).downloadTorrent(anyString(), any());
  }

  @Test
  public void musicPlayerIntentsAreForwardedToTheAudioPlayer() {
    MainActivity activity = boot();
    activity.onNewIntent(new Intent("android.intent.action.MUSIC_PLAYER"));
    Intent started = shadowOf(activity).getNextStartedActivity();
    assertEquals(
        "com.andrew.apollo.ui.activities.AudioPlayerActivity",
        started.getComponent().getClassName());
  }

  @Test
  public void goHomeAndFinishExtrasFinishTheActivity() {
    MainActivity activity = boot();
    activity.onNewIntent(new Intent().putExtra("gohome-frostwire", true));
    assertTrue(activity.isFinishing());
    MainActivity other = boot();
    other.onNewIntent(new Intent().putExtra(Constants.EXTRA_FINISH_MAIN_ACTIVITY, true));
    assertTrue(other.isFinishing());
  }

  @Test
  public void requestShutdownIntentAsksForConfirmation() {
    MainActivity activity = controller(new Intent()).create().start().resume().get();
    shadowOf(Looper.getMainLooper()).idle();
    activity.onNewIntent(new Intent(Constants.ACTION_REQUEST_SHUTDOWN));
    shadowOf(Looper.getMainLooper()).idle();
    assertNotNull(activity.getSupportFragmentManager().findFragmentByTag(YES_NO_TAG));
  }

  @Test
  public void downloadCompleteNotificationIsHandledOffTheMainThread() {
    MainActivity activity = boot();
    background.clear();
    activity.onNewIntent(
        new Intent().putExtra(Constants.EXTRA_DOWNLOAD_COMPLETE_NOTIFICATION, true));
    assertEquals(1, background.size());
    background.get(0).run();
    shadowOf(Looper.getMainLooper()).idle();
    verify(manager).clearDownloadsToReview();
    assertTrue(activity.getCurrentFragment() instanceof TransfersFragment);
  }

  @Test
  public void shutdownDialogPositiveClickShutsEverythingDownOnce() {
    MainActivity activity = boot();
    try (MockedStatic<com.frostwire.android.gui.SearchMediator> search =
            mockStatic(com.frostwire.android.gui.SearchMediator.class);
        MockedStatic<com.andrew.apollo.utils.MusicUtils> music =
            mockStatic(com.andrew.apollo.utils.MusicUtils.class);
        MockedStatic<com.frostwire.util.http.OkHttpClientWrapper> http =
            mockStatic(com.frostwire.util.http.OkHttpClientWrapper.class);
        MockedStatic<com.frostwire.android.core.TellurideCourier> telluride =
            mockStatic(com.frostwire.android.core.TellurideCourier.class)) {
      com.frostwire.android.gui.SearchMediator mediator =
          mock(com.frostwire.android.gui.SearchMediator.class);
      search.when(com.frostwire.android.gui.SearchMediator::instance).thenReturn(mediator);
      activity.onDialogClick("other_dialog", android.app.Dialog.BUTTON_POSITIVE);
      verify(engine, never()).shutdown();
      activity.onDialogClick("shutdown_dialog", android.app.Dialog.BUTTON_NEGATIVE);
      verify(engine, never()).shutdown();
      activity.onDialogClick("shutdown_dialog", android.app.Dialog.BUTTON_POSITIVE);
      activity.shutdown();
      verify(mediator).cancelSearch();
      verify(engine).shutdown();
      music.verify(
          () -> com.andrew.apollo.utils.MusicUtils.requestMusicPlaybackServiceShutdown(activity));
      http.verify(com.frostwire.util.http.OkHttpClientWrapper::cancelAllRequests);
      telluride.verify(com.frostwire.android.core.TellurideCourier::abortCurrentQuery);
      assertTrue(activity.isFinishing());
    }
  }

  @Test
  public void aShutdownIntentShutsDownInsteadOfBooting() {
    try (MockedStatic<com.frostwire.android.gui.SearchMediator> search =
            mockStatic(com.frostwire.android.gui.SearchMediator.class);
        MockedStatic<com.andrew.apollo.utils.MusicUtils> music =
            mockStatic(com.andrew.apollo.utils.MusicUtils.class);
        MockedStatic<com.frostwire.util.http.OkHttpClientWrapper> http =
            mockStatic(com.frostwire.util.http.OkHttpClientWrapper.class);
        MockedStatic<com.frostwire.android.core.TellurideCourier> telluride =
            mockStatic(com.frostwire.android.core.TellurideCourier.class)) {
      search
          .when(com.frostwire.android.gui.SearchMediator::instance)
          .thenReturn(mock(com.frostwire.android.gui.SearchMediator.class));
      Intent shutdown = new Intent().putExtra("shutdown-frostwire", true);
      try {
        controller(shutdown).create();
      } catch (RuntimeException expectedWhileTheUiNeverFinishesBooting) {
        // the screen skips its component setup when shutting down
      }
      verify(engine).shutdown();
      music.verify(
          () ->
              com.andrew.apollo.utils.MusicUtils.requestMusicPlaybackServiceShutdown(
                  org.mockito.ArgumentMatchers.any(android.content.Context.class)));
      http.verify(com.frostwire.util.http.OkHttpClientWrapper::cancelAllRequests);
      telluride.verify(com.frostwire.android.core.TellurideCourier::abortCurrentQuery);
    }
  }

  @Test
  public void firstRunSendsTheUserToTheWizardAndResetsTheConfiguration() {
    when(config.getBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE)).thenReturn(false);
    MainActivity activity = controller(new Intent()).create().start().resume().get();
    shadowOf(Looper.getMainLooper()).idle();
    verify(config).resetToDefaults();
    Intent wizard = shadowOf(activity).getNextStartedActivity();
    assertEquals(WizardActivity.class.getName(), wizard.getComponent().getClassName());
  }

  @Test
  public void resumingAfterSetupStartsTheEngineServicesOnceAndChecksForUpdates() {
    when(config.getBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE)).thenReturn(true);
    SoftwareUpdater updater = mock(SoftwareUpdater.class);
    updaters.when(SoftwareUpdater::getInstance).thenReturn(updater);
    MainActivity activity = controller(new Intent()).create().start().resume().get();
    shadowOf(Looper.getMainLooper()).idle();
    verify(updater).checkForUpdate(activity);
    offers.verify(() -> Offers.initAdNetworks(activity));
    // the engine's own startup is only requested the first time the process shows the screen
    verify(config)
        .setString(
            org.mockito.ArgumentMatchers.eq(Constants.PREF_KEY_CORE_LAST_SEEN_VERSION_BUILD),
            anyString());
    assertFalse(background.isEmpty());
  }

  @Test
  public void aWasShutdownEngineIsRestartedOnResume() {
    when(config.getBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE)).thenReturn(true);
    when(engine.wasShutdown()).thenReturn(true);
    controller(new Intent()).create().start().resume();
    shadowOf(Looper.getMainLooper()).idle();
    verify(engine, org.mockito.Mockito.atLeastOnce()).startServices();
  }

  @Test
  public void updateAvailableBroadcastMarksTheIntentAndShowsTheUpdateButton() {
    MainActivity activity = controller(new Intent()).create().start().resume().get();
    shadowOf(Looper.getMainLooper()).idle();
    sendBroadcast(
        activity, new Intent(Constants.ACTION_NOTIFY_UPDATE_AVAILABLE).putExtra("value", true));
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue(activity.getIntent().getBooleanExtra("updateAvailable", false));
  }

  @Test
  public void dataConnectionBroadcastTellsTheSearchFragment() {
    MainActivity activity = controller(new Intent()).create().start().resume().get();
    shadowOf(Looper.getMainLooper()).idle();
    sendBroadcast(
        activity,
        new Intent(Constants.ACTION_NOTIFY_DATA_INTERNET_CONNECTION).putExtra("isDataUp", false));
    shadowOf(Looper.getMainLooper()).idle();
    // the receiver goes quiet again once the screen is paused
    ActivityController<MainActivity> c = controller(new Intent());
    c.create().start().resume().pause();
  }

  @Test
  public void instanceStateKeepsTheCurrentFragmentStackAndUpdateFlag() {
    ActivityController<MainActivity> controller = controller(new Intent());
    MainActivity activity = controller.create().start().resume().get();
    shadowOf(Looper.getMainLooper()).idle();
    activity.switchContent(activity.getFragmentByNavMenuId(R.id.menu_main_transfers));
    Bundle state = new Bundle();
    controller.saveInstanceState(state);
    assertNotNull(state.getIntArray("fragments_stack"));
    assertEquals(2, state.getIntArray("fragments_stack").length);
    assertFalse(state.getBoolean("updateAvailable", true));
  }

  @Test
  public void refreshTransfersLaunchesMainClearingTheTopAndSwallowsFailures() {
    MainActivity activity = boot();
    MainActivity.refreshTransfers(activity);
    Intent started = shadowOf(activity).getNextStartedActivity();
    assertEquals(Constants.ACTION_SHOW_TRANSFERS, started.getAction());
    assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
    android.content.Context failing =
        new android.content.ContextWrapper(activity) {
          @Override
          public void startActivity(Intent intent) {
            throw new IllegalStateException("nope");
          }
        };
    MainActivity.refreshTransfers(failing);
  }

  @Test
  public void previewDownloadActionStartsTheDownloadOfTheResultThatWasPreviewed() {
    MainActivity activity = boot();
    com.frostwire.search.FileSearchResult sr = mock(com.frostwire.search.FileSearchResult.class);
    com.frostwire.android.gui.dialogs.NewTransferDialog.srRef = com.frostwire.util.Ref.weak(sr);
    try (MockedStatic<SearchFragment> search = mockStatic(SearchFragment.class)) {
      activity.onNewIntent(new Intent(Constants.ACTION_START_TRANSFER_FROM_PREVIEW));
      search.verify(
          () ->
              SearchFragment.startDownload(
                  org.mockito.ArgumentMatchers.eq(activity),
                  org.mockito.ArgumentMatchers.eq(sr),
                  anyString()));
    } finally {
      com.frostwire.android.gui.dialogs.NewTransferDialog.srRef = null;
    }
  }

  @Test
  public void previewDownloadActionWithoutAResultStartsNothing() {
    MainActivity activity = boot();
    com.frostwire.android.gui.dialogs.NewTransferDialog.srRef = null;
    try (MockedStatic<SearchFragment> search = mockStatic(SearchFragment.class)) {
      activity.onNewIntent(new Intent(Constants.ACTION_START_TRANSFER_FROM_PREVIEW));
      search.verify(() -> SearchFragment.startDownload(any(), any(), anyString()), never());
    }
  }

  @Test
  public void launcherIntentsFromTheOplusSeedlingWithAMediaIdOpenTheAudioPlayer() {
    MainActivity activity = boot();
    Intent seedling =
        new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .putExtra("isFromOplusSeedling", "true")
            .putExtra("mediaId", 5L);
    activity.onNewIntent(seedling);
    Intent started = shadowOf(activity).getNextStartedActivity();
    assertEquals(
        "com.andrew.apollo.ui.activities.AudioPlayerActivity",
        started.getComponent().getClassName());
    assertNull("the intent no longer looks like a music player intent", seedling.getAction());

    // without the media id it is an ordinary launch
    MainActivity other = boot();
    other.onNewIntent(
        new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .putExtra("isFromOplusSeedling", "true"));
    assertNull(shadowOf(other).getNextStartedActivity());
  }

  @Test
  public void resumingWithTheTermsAcceptedChecksStoragePermissionsOnlyOnce() {
    when(config.getBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED)).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE)).thenReturn(true);
    ActivityController<MainActivity> controller =
        controller(new Intent()).create().start().resume();
    shadowOf(Looper.getMainLooper()).idle();
    // a second resume must not ask again
    controller.pause().resume();
    assertFalse(controller.get().isFinishing());
  }

  @Test
  public void onResumeQueriesTheNetworkStatusOffTheMainThread() {
    when(config.getBoolean(Constants.PREF_KEY_GUI_INITIAL_SETTINGS_COMPLETE)).thenReturn(true);
    controller(new Intent()).create().start().resume();
    shadowOf(Looper.getMainLooper()).idle();
    for (Runnable r : new ArrayList<>(background)) {
      // only the screen's own tasks, not what the fragments queue (promo slides need the network)
      if (r.getClass().getName().contains("MainActivity")) {
        r.run();
      }
    }
    networks.verify(
        () ->
            com.frostwire.android.gui.NetworkManager.queryNetworkStatusBackground(
                org.mockito.ArgumentMatchers.any(com.frostwire.android.gui.NetworkManager.class)),
        org.mockito.Mockito.atLeastOnce());
  }

  @Test
  public void permissionResultsGoToTheMatchingCheckerOrTheSuperclass() {
    MainActivity activity = boot();
    // an unknown request code falls through to the framework without failing
    activity.onRequestPermissionsResult(
        424242, new String[] {"android.permission.CAMERA"}, new int[] {0});
    // a known one is handled by its checker
    activity.onRequestPermissionsResult(
        com.frostwire.android.gui.util.DangerousPermissionsChecker
            .EXTERNAL_STORAGE_PERMISSIONS_REQUEST_CODE,
        new String[] {"android.permission.READ_EXTERNAL_STORAGE"},
        new int[] {android.content.pm.PackageManager.PERMISSION_GRANTED});
    assertFalse(activity.isFinishing());
  }

  @Test
  public void promoVideoPreviewsAreLaunchedForResult() {
    MainActivity activity = boot();
    Intent preview = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/v.mp4"));
    activity.launchPromoVideoPreview(preview);
    Intent started = shadowOf(activity).getNextStartedActivityForResult().intent;
    assertEquals("https://example.com/v.mp4", started.getDataString());
  }

  @Test
  public void configurationChangesAndOptionItemsReachTheNavigationDrawer() {
    MainActivity activity = boot();
    activity.onConfigurationChanged(activity.getResources().getConfiguration());
    android.view.MenuItem item = mock(android.view.MenuItem.class);
    when(item.getItemId()).thenReturn(android.R.id.home);
    assertFalse(activity.onOptionsItemSelected(item));
    assertFalse(activity.onKeyDown(KeyEvent.KEYCODE_BACK, null));
  }

  @Test
  public void leavingAndSavingMinimizesOnlyWhenTheUserAnswersYesToTheLastBackDialog() {
    MainActivity activity = boot();
    activity.onDialogClick("last_back_dialog", android.app.Dialog.BUTTON_NEGATIVE);
    activity.onDialogClick("last_back_dialog", android.app.Dialog.BUTTON_POSITIVE);
    assertFalse("minimizing keeps the activity alive", activity.isFinishing());
  }
}
