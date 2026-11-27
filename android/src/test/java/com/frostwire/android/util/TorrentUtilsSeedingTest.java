/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

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

import android.app.Application;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.activities.MainActivity;
import com.frostwire.android.gui.fragments.TransfersFragment;
import com.frostwire.android.gui.transfers.TransferManager;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.Transfer;
import java.io.File;
import java.nio.file.Files;
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
 * Seeding a file the user has: a torrent is built from it by libtorrent and added to a real engine,
 * and a finished HTTP download is seeded when the settings and the network allow it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TorrentUtilsSeedingTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private TestEngine local;
  private TransferManager manager;
  private ConfigurationManager config;
  private NetworkManager network;
  private File song;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<ConfigurationManager> configStatic;
  private MockedStatic<NetworkManager> networkStatic;
  private MockedStatic<MainActivity> activities;

  @Before
  public void setUp() throws Exception {
    local = TestEngine.start(tmp.newFolder("engine").toPath());
    manager = mock(TransferManager.class);
    config = mock(ConfigurationManager.class);
    network = mock(NetworkManager.class);
    song = tmp.newFolder("music");
    song = new File(song, "song.mp3");
    Files.write(song.toPath(), new byte[40_000]);
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              ((Runnable) call.getArgument(1)).run();
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            call -> {
              ((Runnable) call.getArgument(0)).run();
              return null;
            });
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    networkStatic = mockStatic(NetworkManager.class);
    networkStatic.when(NetworkManager::instance).thenReturn(network);
    activities = mockStatic(MainActivity.class);
  }

  @After
  public void tearDown() {
    activities.close();
    networkStatic.close();
    configStatic.close();
    system.close();
    local.close();
  }

  private TorrentInfo seededTorrentOf(File file) throws Exception {
    return TorrentInfo.bdecode(
        new com.frostwire.jlibtorrent.TorrentBuilder().path(file).generate().entry().bencode());
  }

  @Test
  public void aFileOnDiskBecomesATorrentTheEngineSeeds() throws Exception {
    BittorrentDownload listed = mock(BittorrentDownload.class);
    when(manager.ensureUiDownload(any(TorrentHandle.class))).thenReturn(listed);
    Transfer httpTransfer = mock(Transfer.class);

    assertTrue(TorrentUtils.seedFile(song, "A song", manager, httpTransfer));

    // libtorrent has the torrent of that very file, with every byte on disk
    TorrentInfo expected = seededTorrentOf(song);
    TorrentHandle handle = local.engine.find(expected.infoHashV1());
    assertNotNull(handle);
    assertTrue(handle.isValid());
    assertEquals(song.length(), handle.torrentFile().totalSize());
    assertEquals(song.getName(), handle.torrentFile().name());
    // the HTTP row is replaced by the torrent's
    verify(manager).ensureUiDownload(any(TorrentHandle.class));
    verify(manager).remove(httpTransfer);
  }

  @Test
  public void aTorrentTheTransfersAlreadyHoldIsNotAddedAgain() throws Exception {
    BittorrentDownload existing = mock(BittorrentDownload.class);
    when(manager.getBittorrentDownload(any())).thenReturn(existing);
    Transfer httpTransfer = mock(Transfer.class);

    assertTrue(TorrentUtils.seedFile(song, null, manager, httpTransfer));

    assertEquals(null, local.engine.find(new Sha1Hash(seededTorrentOf(song).infoHashV1().toHex())));
    verify(manager, never()).ensureUiDownload(any(TorrentHandle.class));
    verify(manager).remove(httpTransfer);
  }

  @Test
  public void aSeedThatCannotBeListedKeepsTheHttpRow() throws Exception {
    when(manager.ensureUiDownload(any(TorrentHandle.class))).thenReturn(null);
    Transfer httpTransfer = mock(Transfer.class);

    assertFalse(TorrentUtils.seedFile(song, "A song", manager, httpTransfer));

    verify(manager, never()).remove(httpTransfer);
  }

  @Test
  public void seedingWithoutAnHttpRowJustAddsTheTorrent() {
    BittorrentDownload listed = mock(BittorrentDownload.class);
    when(manager.ensureUiDownload(any(TorrentHandle.class))).thenReturn(listed);

    assertTrue(TorrentUtils.seedFile(song, "A song", manager, null));

    verify(manager, never()).remove(any(Transfer.class));
  }

  @Test
  public void aFinishedDownloadIsSeededWhenSeedingIsOnAndTheNetworkAllowsIt() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    BittorrentDownload listed = mock(BittorrentDownload.class);
    when(manager.ensureUiDownload(any(TorrentHandle.class))).thenReturn(listed);
    Transfer httpTransfer = mock(Transfer.class);

    TorrentUtils.seedFinishedHttpDownloadIfEnabled(
        song, "A song", Constants.FILE_TYPE_AUDIO, manager, httpTransfer);

    verify(manager).ensureUiDownload(any(TorrentHandle.class));
    verify(manager).remove(httpTransfer);
    // the transfers screen is refreshed, when there is one
    activities.verify(MainActivity::instance);
  }

  @Test
  public void aFinishedDownloadIsSeededWithoutAnHttpRowToo() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    when(manager.ensureUiDownload(any(TorrentHandle.class)))
        .thenReturn(mock(BittorrentDownload.class));

    TorrentUtils.seedFinishedHttpDownloadIfEnabled(
        song, "A song", Constants.FILE_TYPE_AUDIO, manager);

    verify(manager).ensureUiDownload(any(TorrentHandle.class));
  }

  @Test
  public void aFinishedDownloadIsNotSeededWhenTheSettingsOrTheNetworkSayNo() {
    // seeding off
    TorrentUtils.seedFinishedHttpDownloadIfEnabled(
        song, "A song", Constants.FILE_TYPE_AUDIO, manager);
    // seeding on, but only on wifi, and wifi is down
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    when(config.isSeedingEnabledOnlyForWifi()).thenReturn(true);
    when(network.isDataWIFIUp()).thenReturn(false);
    TorrentUtils.seedFinishedHttpDownloadIfEnabled(
        song, "A song", Constants.FILE_TYPE_AUDIO, manager);
    // wifi is up, but mobile data savings are on
    when(network.isDataWIFIUp()).thenReturn(true);
    when(manager.isMobileAndDataSavingsOn()).thenReturn(true);
    TorrentUtils.seedFinishedHttpDownloadIfEnabled(
        song, "A song", Constants.FILE_TYPE_AUDIO, manager);
    // everything allows it, but the file is gone
    when(manager.isMobileAndDataSavingsOn()).thenReturn(false);
    TorrentUtils.seedFinishedHttpDownloadIfEnabled(
        new File(tmp.getRoot(), "gone.mp3"), "Gone", Constants.FILE_TYPE_AUDIO, manager);
    TorrentUtils.seedFinishedHttpDownloadIfEnabled(
        null, "Nothing", Constants.FILE_TYPE_AUDIO, manager);

    verify(manager, never()).ensureUiDownload(any(TorrentHandle.class));
  }

  @Test
  public void theTransfersScreenIsRefreshedAfterASeedWhenItIsOnScreen() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    when(manager.ensureUiDownload(any(TorrentHandle.class)))
        .thenReturn(mock(BittorrentDownload.class));
    MainActivity activity = mock(MainActivity.class);
    TransfersFragment fragment = mock(TransfersFragment.class);
    activities.when(MainActivity::instance).thenReturn(activity);
    when(activity.getFragmentByNavMenuId(R.id.menu_main_transfers)).thenReturn(fragment);

    TorrentUtils.seedFinishedHttpDownloadIfEnabled(
        song, "A song", Constants.FILE_TYPE_AUDIO, manager);

    verify(fragment).onTime(true);
  }

  @Test
  public void aFailureRefreshingTheTransfersScreenIsContained() {
    when(config.isSeedFinishedTorrents()).thenReturn(true);
    when(manager.ensureUiDownload(any(TorrentHandle.class)))
        .thenReturn(mock(BittorrentDownload.class));
    activities.when(MainActivity::instance).thenThrow(new IllegalStateException("no activity"));

    TorrentUtils.seedFinishedHttpDownloadIfEnabled(
        song, "A song", Constants.FILE_TYPE_AUDIO, manager);

    verify(manager).ensureUiDownload(any(TorrentHandle.class));
  }
}
