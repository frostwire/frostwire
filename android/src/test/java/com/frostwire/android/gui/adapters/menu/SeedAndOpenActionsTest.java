/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Dialog;
import android.os.Looper;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.TimerObserver;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.android.util.TorrentUtils;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Seeding, opening and reconnecting from the transfer and file context menus. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SeedAndOpenActionsTest {

  /** A host activity that is also a TimerObserver, like the screens that show these menus. */
  public static final class ObserverActivity extends AppCompatActivity implements TimerObserver {
    final AtomicInteger ticks = new AtomicInteger();

    @Override
    public void onTime() {
      ticks.incrementAndGet();
    }
  }

  private ObserverActivity activity;
  private MockedStatic<TransferManager> transfers;
  private MockedStatic<ConfigurationManager> configs;
  private MockedStatic<NetworkManager> networks;
  private MockedStatic<UIUtils> ui;
  private MockedStatic<SystemUtils> system;
  private TransferManager manager;
  private ConfigurationManager config;
  private NetworkManager network;
  private final List<Runnable> posted = new ArrayList<>();

  @Before
  public void setUp() {
    ActivityController<ObserverActivity> controller =
        Robolectric.buildActivity(ObserverActivity.class);
    controller.get().setTheme(R.style.Theme_FrostWire);
    controller.setup();
    activity = controller.get();
    manager = mock(TransferManager.class);
    config = mock(ConfigurationManager.class);
    network = mock(NetworkManager.class);
    transfers = mockStatic(TransferManager.class);
    transfers.when(TransferManager::instance).thenReturn(manager);
    configs = mockStatic(ConfigurationManager.class);
    configs.when(ConfigurationManager::instance).thenReturn(config);
    networks = mockStatic(NetworkManager.class);
    networks.when(NetworkManager::instance).thenReturn(network);
    ui = mockStatic(UIUtils.class);
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(SystemUtils.HandlerThreadName.class), any()))
        .thenAnswer(
            i -> {
              posted.add(i.getArgument(1));
              return null;
            });
  }

  @After
  public void tearDown() {
    transfers.close();
    configs.close();
    networks.close();
    ui.close();
    system.close();
  }

  private YesNoDialog shownYesNo() {
    shadowOf(Looper.getMainLooper()).idle();
    for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
      if (f instanceof YesNoDialog && f.isAdded()) {
        return (YesNoDialog) f;
      }
    }
    return null;
  }

  private FWFileDescriptor file(String path) {
    FWFileDescriptor fd = new FWFileDescriptor();
    fd.filePath = path;
    fd.title = "title";
    return fd;
  }

  // ---- SeedAction

  @Test
  public void seedingIsRefusedWhenBitTorrentMayOnlyRunOverAVpnAndThereIsNone() {
    when(manager.isBittorrentOnVpnOnlyAndNoVpn()).thenReturn(true);
    new SeedAction(activity).onClick();
    ui.verify(() -> UIUtils.showLongMessage(activity, R.string.cannot_start_engine_without_vpn));
    verify(manager, never()).seedFinishedTransfers();
  }

  @Test
  public void aDisconnectedBitTorrentAsksToReconnectAndNoKeepsItOff() {
    when(manager.isBittorrentDisconnected()).thenReturn(true);
    new SeedAction(activity).onClick();
    YesNoDialog dialog = shownYesNo();
    assertNotNull(dialog);
    Dialog dlg = dialog.getDialog();
    ((Button) dlg.findViewById(R.id.dialog_default_button_no)).performClick();
    ui.verify(
        () ->
            UIUtils.showLongMessage(
                activity,
                R.string.the_file_could_not_be_seeded_bittorrent_will_remain_disconnected));
  }

  @Test
  public void yesOnTheReconnectDialogStartsTheEngineOnItsThreadPool() {
    when(manager.isBittorrentDisconnected()).thenReturn(true);
    ExecutorService pool = mock(ExecutorService.class);
    Engine engine = mock(Engine.class);
    when(engine.getThreadPool()).thenReturn(pool);
    try (MockedStatic<Engine> engines = mockStatic(Engine.class)) {
      engines.when(Engine::instance).thenReturn(engine);
      new SeedAction(activity).onClick();
      Dialog dlg = shownYesNo().getDialog();
      ((Button) dlg.findViewById(R.id.dialog_default_button_yes)).performClick();
      verify(pool).execute(any(OnBittorrentConnectRunnable.class));
    }
  }

  @Test
  public void yesOnTheReconnectDialogWithoutAVpnTunnelExplainsWhy() {
    when(manager.isBittorrentDisconnected()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY)).thenReturn(true);
    when(network.isTunnelUp()).thenReturn(false);
    new SeedAction(activity).onClick();
    Dialog dlg = shownYesNo().getDialog();
    ((Button) dlg.findViewById(R.id.dialog_default_button_yes)).performClick();
    ui.verify(
        () ->
            UIUtils.showShortMessage(
                any(android.view.View.class), eq(R.string.cannot_start_engine_without_vpn)));
  }

  @Test
  public void seedingOnlyOnWifiWithoutWifiExplainsInsteadOfSeeding() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    when(config.isSeedingEnabledOnlyForWifi()).thenReturn(true);
    when(network.isDataWIFIUp()).thenReturn(false);
    new SeedAction(activity).onClick();
    shadowOf(Looper.getMainLooper()).idle();
    SeedAction.ShowNoWifiInformationDialog dialog = null;
    for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
      if (f instanceof SeedAction.ShowNoWifiInformationDialog) {
        dialog = (SeedAction.ShowNoWifiInformationDialog) f;
      }
    }
    assertNotNull(dialog);
    Dialog dlg = dialog.getDialog();
    assertEquals(
        activity.getString(R.string.according_to_settings_i_cant_seed_unless_wifi),
        ((android.widget.TextView) dlg.findViewById(R.id.dialog_default_info_text))
            .getText()
            .toString());
    ((Button) dlg.findViewById(R.id.dialog_default_info_button_ok)).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue(!dlg.isShowing());
    verify(manager, never()).seedFinishedTransfers();
  }

  @Test
  public void withSeedingDisabledTheUserIsAskedToEnableItAndYesSeedsEverythingFinished() {
    when(config.isSeedFinishedTorrents()).thenReturn(false);
    new SeedAction(activity).onClick();
    Dialog dlg = shownYesNo().getDialog();
    ((Button) dlg.findViewById(R.id.dialog_default_button_yes)).performClick();
    verify(config).setSeedFinishedTorrents(true);
    verify(manager).seedFinishedTransfers();
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(activity));
  }

  @Test
  public void noToEnablingSeedingTellsTheUserTheFileWasNotSeeded() {
    when(config.isSeedFinishedTorrents()).thenReturn(false);
    new SeedAction(activity).onClick();
    Dialog dlg = shownYesNo().getDialog();
    ((Button) dlg.findViewById(R.id.dialog_default_button_no)).performClick();
    ui.verify(
        () ->
            UIUtils.showLongMessage(
                activity, R.string.the_file_could_not_be_seeded_enable_seeding));
    verify(manager, never()).seedFinishedTransfers();
  }

  @Test
  public void enablingSeedingWhileVpnOnlyAndNoVpnStopsAfterSavingTheSetting() {
    when(config.isSeedFinishedTorrents()).thenReturn(false);
    new SeedAction(activity).onClick();
    Dialog dlg = shownYesNo().getDialog();
    when(manager.isBittorrentOnVpnOnlyAndNoVpn()).thenReturn(true);
    ((Button) dlg.findViewById(R.id.dialog_default_button_yes)).performClick();
    verify(config).setSeedFinishedTorrents(true);
    ui.verify(() -> UIUtils.showLongMessage(activity, R.string.cannot_start_engine_without_vpn));
    verify(manager, never()).seedFinishedTransfers();
  }

  @Test
  public void seedingEnabledSeedsFinishedTransfersAndTicksTheHostTimer() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    new SeedAction(activity).onClick();
    verify(manager).seedFinishedTransfers();
    assertEquals(1, activity.ticks.get());
    ui.verify(() -> UIUtils.showTransfersOnDownloadStart(activity));
  }

  @Test
  public void mobileDataSavingsExplainsAndSkipsTheSeedingItself() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    when(manager.isMobileAndDataSavingsOn()).thenReturn(true);
    new SeedAction(activity).onClick();
    shadowOf(Looper.getMainLooper()).idle();
    boolean explained = false;
    for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
      explained |= f instanceof SeedAction.ShowMobileDataProtectionInformationDialog;
    }
    assertTrue(explained);
    verify(manager, never()).seedFinishedTransfers();
  }

  @Test
  public void seedingAPlainFileHandsItToTheTorrentCreatorOffTheMainThread() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    Transfer toClear = mock(Transfer.class);
    FWFileDescriptor fd = file("/sdcard/Music/song.mp3");
    try (MockedStatic<TorrentUtils> torrents = mockStatic(TorrentUtils.class)) {
      new SeedAction(activity, fd, toClear).onClick();
      assertEquals(1, posted.size());
      torrents.verify(() -> TorrentUtils.seedFile(any(), any(), any(), any()), never());
      posted.get(0).run();
      torrents.verify(
          () ->
              TorrentUtils.seedFile(
                  eq(new File("/sdcard/Music/song.mp3")), eq("title"), eq(manager), eq(toClear)));
    }
  }

  @Test
  public void seedingIsSkippedWhileMobileDataSavingsAreOnEvenWhenEnabled() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    when(manager.isMobileAndDataSavingsOn()).thenReturn(true);
    new SeedAction(activity, file("/sdcard/a.mp3")).onClick();
    assertTrue(posted.isEmpty());
  }

  // ---- OpenMenuAction

  @Test
  public void openByPathAndMimeLaunchesTheFileViewer() {
    new OpenMenuAction(activity, "/sdcard/doc.pdf", "application/pdf").onClick();
    ui.verify(() -> UIUtils.openFile(activity, "/sdcard/doc.pdf", "application/pdf"));
    OpenMenuAction titled =
        new OpenMenuAction(activity, "My Doc", "/sdcard/doc.pdf", "application/pdf");
    assertEquals("My Doc", titled.getText());
    titled.onClick();
    ui.verify(() -> UIUtils.openFile(activity, "/sdcard/doc.pdf", "application/pdf"), times(2));
  }

  @Test
  public void openingAudioFilesPlaysThemAsAnEphemeralPlaylist() {
    FWFileDescriptor fd = file("/sdcard/Music/a.mp3");
    fd.fileType = Constants.FILE_TYPE_AUDIO;
    fd.mime = "audio/mpeg";
    new OpenMenuAction(activity, fd, 3).onClick();
    ui.verify(() -> UIUtils.playEphemeralPlaylist(activity, fd));
  }

  @Test
  public void openingARingtonePausesWhateverPlaysThenPlaysIt() {
    FWFileDescriptor fd = file("/sdcard/Ringtones/r.ogg");
    fd.fileType = Constants.FILE_TYPE_RINGTONES;
    try (MockedStatic<MusicUtils> music = mockStatic(MusicUtils.class)) {
      music.when(MusicUtils::isPlaying).thenReturn(true);
      new OpenMenuAction(activity, "Ringtone", fd).onClick();
      music.verify(MusicUtils::playPauseOrResume);
      music.verify(() -> MusicUtils.playSimple("/sdcard/Ringtones/r.ogg"));
    }
  }

  @Test
  public void openingAPictureDescriptorUsesItsMimeType() {
    FWFileDescriptor fd = file("/sdcard/Pictures/p.jpg");
    fd.fileType = Constants.FILE_TYPE_PICTURES;
    fd.mime = "image/jpeg";
    new OpenMenuAction(activity, "p.jpg", fd).onClick();
    ui.verify(() -> UIUtils.openFile(activity, "/sdcard/Pictures/p.jpg", "image/jpeg"));
  }

  // ---- OnBittorrentConnectRunnable

  @Test
  public void reconnectRunnableStartsServicesWaitsForTheEngineThenRetriesTheAction() {
    BittorrentDownload dl = mock(BittorrentDownload.class);
    ResumeDownloadMenuAction action =
        new ResumeDownloadMenuAction(activity, dl, R.string.resume_torrent_menu_action);
    Engine engine = mock(Engine.class);
    when(engine.isStarted()).thenReturn(true);
    when(dl.isPaused()).thenReturn(true);
    when(network.isInternetDataConnectionUp()).thenReturn(true);
    try (MockedStatic<Engine> engines = mockStatic(Engine.class)) {
      engines.when(Engine::instance).thenReturn(engine);
      new OnBittorrentConnectRunnable(action).run();
      shadowOf(Looper.getMainLooper()).idle();
    }
    verify(engine).startServices();
    // the retried click resumes the paused download
    verify(dl).resume();
  }

  @Test
  public void reconnectRunnableDoesNothingAfterTheActionIsGone() {
    Engine engine = mock(Engine.class);
    when(engine.isStarted()).thenReturn(true);
    try (MockedStatic<Engine> engines = mockStatic(Engine.class)) {
      engines.when(Engine::instance).thenReturn(engine);
      OnBittorrentConnectRunnable runnable = new OnBittorrentConnectRunnable(null);
      runnable.run();
      shadowOf(Looper.getMainLooper()).idle();
    }
    verify(engine).startServices();
    // no menu action left to retry: nothing is posted to the main thread
    assertTrue(shadowOf(Looper.getMainLooper()).isIdle());
  }

  // ---- ResumeDownloadMenuAction reconnect flow

  @Test
  public void resumingWhileDisconnectedAsksToReconnectAndYesStartsTheEngine() {
    when(manager.isBittorrentDisconnected()).thenReturn(true);
    BittorrentDownload dl = mock(BittorrentDownload.class);
    ExecutorService pool = mock(ExecutorService.class);
    Engine engine = mock(Engine.class);
    when(engine.getThreadPool()).thenReturn(pool);
    try (MockedStatic<Engine> engines = mockStatic(Engine.class)) {
      engines.when(Engine::instance).thenReturn(engine);
      new ResumeDownloadMenuAction(activity, dl, R.string.resume_torrent_menu_action).onClick();
      YesNoDialog dialog = shownYesNo();
      assertNotNull(dialog);
      ((Button) dialog.getDialog().findViewById(R.id.dialog_default_button_yes)).performClick();
      verify(pool).execute(any(OnBittorrentConnectRunnable.class));
    }
    verify(dl, never()).resume();
  }

  @Test
  public void reconnectOutsideAnActivityWithoutAVpnTunnelShowsTheMessageOnTheContext() {
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY)).thenReturn(true);
    when(network.isTunnelUp()).thenReturn(false);
    android.content.Context app = activity.getApplicationContext();
    new OnBittorrentConnectRunnable(new SeedAction(activity)).onBittorrentConnect(app);
    ui.verify(() -> UIUtils.showShortMessage(app, R.string.cannot_start_engine_without_vpn));
  }
}
