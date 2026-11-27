/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.ConfigurationRepository;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.MainApplication;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.libtorrent.LoopbackTorrent;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferState;
import java.io.File;
import java.nio.file.Files;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The transfer list's BitTorrent side over a real engine: transfers the engine adds show up as
 * wrappers, the policy and bulk actions reach libtorrent, and the preferences are applied to the
 * session.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TransferManagerEngineTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private LoopbackTorrent torrent;
  private TestEngine local;
  private ConfigurationManager config;
  private NetworkManager network;
  private TransferManager manager;
  private ConfigurationRepository.OnPreferenceChangeListener preferences;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<ConfigurationManager> configStatic;
  private MockedStatic<NetworkManager> networkStatic;
  private MockedStatic<MainApplication> main;
  private MockedStatic<Engine> engineStatic;
  private MockedStatic<BTEngine> engines;
  private MockedStatic<TransferManager> managers;

  @Before
  public void setUp() throws Exception {
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system.when(SystemUtils::isUIThread).thenReturn(false);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              ((Runnable) call.getArgument(1)).run();
              return null;
            });
    torrent = new LoopbackTorrent(tmp.newFolder("root").toPath());
    local = torrent.local;
    config = mock(ConfigurationManager.class);
    when(config.getStoragePath()).thenReturn(tmp.newFolder("storage").getAbsolutePath());
    when(config.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS)).thenReturn(true);
    network = mock(NetworkManager.class);
    when(network.isDataWIFIUp()).thenReturn(true);
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    networkStatic = mockStatic(NetworkManager.class);
    networkStatic.when(NetworkManager::instance).thenReturn(network);
    main = mockStatic(MainApplication.class);
    main.when(MainApplication::hasBTEngineInitializationFailure).thenReturn(false);
    Engine engine = mock(Engine.class);
    when(engine.isStarted()).thenReturn(true);
    engineStatic = mockStatic(Engine.class);
    engineStatic.when(Engine::instance).thenReturn(engine);
    engines = mockStatic(BTEngine.class);
    engines.when(BTEngine::getInstance).thenReturn(local.engine);

    // plenty of space, whatever the Robolectric file system says
    managers = mockStatic(TransferManager.class, CALLS_REAL_METHODS);
    managers.when(TransferManager::getCurrentMountAvailableBytes).thenReturn(Long.MAX_VALUE);
    manager = TransferManager.instance();
    manager.onShutdown(false);
    manager.resetStartedTransfers();
    // restores the (empty) previous session and starts listening to the engine
    manager.reset();
    ArgumentCaptor<ConfigurationRepository.OnPreferenceChangeListener> captor =
        ArgumentCaptor.forClass(ConfigurationRepository.OnPreferenceChangeListener.class);
    verify(config, atLeastOnce()).registerOnPreferenceChange(captor.capture());
    preferences = captor.getValue();
  }

  @After
  public void tearDown() {
    manager.onShutdown(false);
    managers.close();
    engines.close();
    engineStatic.close();
    main.close();
    networkStatic.close();
    configStatic.close();
    torrent.close();
    system.close();
  }

  /**
   * Waits until the transfer is complete and its finished listener, which ran on libtorrent's
   * thread with the app's real settings (seeding is off by default, which pauses the transfer), has
   * had its say; then resumes it so every test starts from a seeding transfer.
   */
  private void awaitCompleteAndSeeding(UIBittorrentDownload ui) {
    TestEngine.await(
        () -> {
          ui.updateCachedState();
          return ui.isComplete();
        },
        "the whole album arrived");
    TestEngine.Events.next(local.events.finished, "album");
    ui.resume();
    ui.getDl().refreshStatusCache();
    ui.updateCachedState();
  }

  private UIBittorrentDownload awaitTransfer() throws Exception {
    String hash = torrent.album.infoHashV1().toHex();
    TestEngine.await(() -> manager.getBittorrentDownload(hash) != null, "the transfer is listed");
    // The wrapper the engine's listener made was made on libtorrent's thread, where the free
    // space of the test's storage is whatever Robolectric says (often none: a disk full
    // transfer). The same refresh the screens ask for makes it again on this thread.
    manager.updateUIBittorrentDownload(
        ((UIBittorrentDownload) manager.getBittorrentDownload(hash)).getDl().getTorrentHandle());
    return (UIBittorrentDownload) manager.getBittorrentDownload(hash);
  }

  @Test
  public void aTransferTheEngineAddsShowsUpAndFollowsTheEngineUntilItIsComplete() throws Exception {
    torrent.add(null, true);

    UIBittorrentDownload ui = awaitTransfer();

    assertEquals("album", ui.getDisplayName());
    assertTrue(manager.getTransfers().contains(ui));
    assertTrue(ui.getDl().getListener() instanceof UIBTDownloadListener);
    awaitCompleteAndSeeding(ui);
    assertEquals(TransferState.SEEDING, ui.getState());
    assertEquals(0, manager.getActiveDownloads());
    manager.clearComplete();
    // a finished transfer that is still seeding stays in the list
    assertTrue(manager.getTransfers().contains(ui));
    manager.pauseTorrents();
    TestEngine.await(() -> ui.isPaused(), "the transfer is paused");
    manager.clearComplete();
    assertFalse("a finished and paused transfer is cleared", manager.getTransfers().contains(ui));
  }

  @Test
  public void aSecondUpdateForTheSameTorrentRefreshesTheRowInsteadOfAddingAnother()
      throws Exception {
    BTDownload dl = torrent.add(null, false);
    UIBittorrentDownload first = awaitTransfer();
    local.events.updated.clear();

    manager.updateUIBittorrentDownload(dl.getTorrentHandle());
    manager.ensureUiDownload(dl.getTorrentHandle());

    int rows = 0;
    for (Transfer t : manager.getTransfers()) {
      if (t instanceof UIBittorrentDownload
          && torrent.album.infoHashV1().toHex().equals(((UIBittorrentDownload) t).getInfoHash())) {
        rows++;
      }
    }
    assertEquals(1, rows);
    assertNotNull(first);
  }

  @Test
  public void ensuringATransferForAHandleListsItOnceAndRefusesInvalidHandles() throws Exception {
    BTDownload dl = torrent.add(null, false);
    // the engine's own listener is not wired: the manager lists the handle itself
    manager.onShutdown(false);
    assertNull(manager.getBittorrentDownload(torrent.album.infoHashV1().toHex()));
    TorrentHandle handle = dl.getTorrentHandle();

    BittorrentDownload ui = manager.ensureUiDownload(handle);

    assertNotNull(ui);
    assertSame(ui, manager.getBittorrentDownload(torrent.album.infoHashV1().toHex()));
    assertSame(ui, manager.ensureUiDownload(handle));
    assertNull(manager.ensureUiDownload(null));
    assertEquals(1, manager.getTransfers().size());
    // an update replaces the row in place
    manager.updateUIBittorrentDownload(handle);
    assertEquals(1, manager.getTransfers().size());
    assertTrue(manager.getTransfers().get(0) != ui);
  }

  @Test
  public void thePolicyPausesAndResumesTheTransfersWithoutTouchingTheUsersPauses()
      throws Exception {
    torrent.add(null, true);
    UIBittorrentDownload ui = awaitTransfer();
    awaitCompleteAndSeeding(ui);

    manager.suspendSeedingTorrents();
    assertTrue(ui.isPaused());
    manager.resumePolicySuspendedSeeding();
    ui.getDl().refreshStatusCache();
    ui.updateCachedState();
    assertFalse(ui.isPaused());

    manager.suspendTorrentsForPolicy();
    assertTrue(ui.isPaused());
    // not complete downloads only: a complete one waits for the seeding policy
    manager.resumePolicySuspendedDownloads();
    assertTrue(ui.isPaused());
    // data savings on: seeding stays suspended
    when(network.isDataMobileUp()).thenReturn(true);
    when(config.getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY)).thenReturn(true);
    manager.resumePolicySuspendedSeeding();
    assertTrue(ui.isPaused());
    when(network.isDataMobileUp()).thenReturn(false);
    manager.resumePolicySuspendedSeeding();
    ui.getDl().refreshStatusCache();
    ui.updateCachedState();
    assertFalse(ui.isPaused());

    manager.stopSeedingTorrents();
    assertTrue(ui.isPaused());
  }

  @Test
  public void aDownloadInProgressIsCountedAsActiveAndAPausedOneIsNot() throws Exception {
    torrent.add(null, false);
    UIBittorrentDownload ui = awaitTransfer();
    assertEquals(0, manager.getActiveUploads());
    manager.pauseTorrents();
    TestEngine.await(() -> ui.isPaused(), "the transfer is paused");
    assertEquals(0, manager.getActiveDownloads());
    manager.resetStartedTransfers();
    assertEquals(0, manager.startedTransfers());
  }

  @Test
  public void theBandwidthComesFromTheEngineSession() throws Exception {
    assertEquals(local.engine.downloadRate(), manager.getDownloadsBandwidth());
    assertEquals(local.engine.uploadRate(), manager.getUploadsBandwidth(), 0.0);
  }

  @Test
  public void aTorrentFileOnDiskIsAddedToTheEngineAndCounted() throws Exception {
    File file = new File(tmp.newFolder("incoming"), "album.torrent");
    Files.write(file.toPath(), torrent.album.bencode());

    BittorrentDownload download = manager.downloadTorrent(file.toURI().toString(), null);

    assertNull(download);
    assertEquals(1, manager.startedTransfers());
    UIBittorrentDownload ui = awaitTransfer();
    assertEquals("album", ui.getDisplayName());
  }

  @Test
  public void thePreferencesAreAppliedToTheSession() {
    when(config.getLong(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED)).thenReturn(150_000L);
    when(config.getLong(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED)).thenReturn(90_000L);
    when(config.getLong(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS)).thenReturn(7L);
    when(config.getLong(Constants.PREF_KEY_TORRENT_MAX_UPLOADS)).thenReturn(6L);
    when(config.getLong(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS)).thenReturn(321L);
    when(config.getLong(Constants.PREF_KEY_TORRENT_MAX_PEERS)).thenReturn(55L);

    preferences.onPreferenceChanged(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED);
    preferences.onPreferenceChanged(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED);
    preferences.onPreferenceChanged(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS);
    preferences.onPreferenceChanged(Constants.PREF_KEY_TORRENT_MAX_UPLOADS);
    preferences.onPreferenceChanged(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS);
    preferences.onPreferenceChanged(Constants.PREF_KEY_TORRENT_MAX_PEERS);
    // a preference the session does not care about
    preferences.onPreferenceChanged("frostwire.prefs.something.else");

    assertEquals(150_000, local.engine.downloadRateLimit());
    assertEquals(90_000, local.engine.uploadRateLimit());
    assertEquals(7, local.engine.maxActiveDownloads());
    assertEquals(6, local.engine.maxActiveSeeds());
    assertEquals(321, local.engine.maxConnections());
    assertEquals(55, local.engine.maxPeers());
  }

  @Test
  public void theSkippedAndIncompleteFilesOfPartialTransfersAreListedForTheLibrary()
      throws Exception {
    boolean[] onlyTheMp3 = new boolean[2];
    onlyTheMp3[torrent.fileIndex("one.mp3")] = true;
    BTDownload dl = torrent.add(onlyTheMp3, false);
    awaitTransfer();

    Set<File> skipped = Transfers.getSkippedFiles(dl);

    File twoTxt = new File(dl.getContentSavePath(), "two.txt");
    assertEquals(Set.of(twoTxt), skipped);
    assertEquals(Set.of(twoTxt), Transfers.getSkipedFiles());
    Set<File> incomplete = Transfers.getIncompleteFiles();
    assertEquals(dl.getIncompleteFiles(), incomplete);
    Set<File> ignorable = Transfers.getIgnorableFiles();
    assertTrue(ignorable.containsAll(skipped));
    assertTrue(ignorable.containsAll(incomplete));
  }

  @Test
  public void aTransferWithEveryFileWantedSkipsNothing() throws Exception {
    BTDownload dl = torrent.add(null, false);
    assertTrue(Transfers.getSkippedFiles(dl).isEmpty());
  }
}
