/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import com.frostwire.transfers.TransferStateListener;
import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * The transfers list's wrapper of a libtorrent download, over a real engine, real torrents and a
 * seeder on 127.0.0.1: what it caches from libtorrent, how it pauses, resumes and removes, and what
 * it tells its listeners.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class UIBittorrentDownloadTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private TestEngine local;
  private TransferManager manager;
  private TorrentInfo album;
  private File seedDir;
  private SessionManager seeder;
  private MockedStatic<SystemUtils> system;
  private final List<SessionManager> seeders = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              ((Runnable) call.getArgument(1)).run();
              return null;
            });
    Path root = tmp.newFolder("root").toPath();
    local = TestEngine.start(root.resolve("engine"));
    manager = mock(TransferManager.class);
    seedDir = root.resolve("seed").toFile();
    TestEngine.content(seedDir.toPath().resolve("album/one.mp3"), 40_000, 1);
    TestEngine.content(seedDir.toPath().resolve("album/two.txt"), 20_000, 2);
    album =
        TestEngine.torrent(
            new File(seedDir, "album"), com.frostwire.jlibtorrent.swig.create_torrent.v1_only);
  }

  @After
  public void tearDown() {
    for (SessionManager s : seeders) {
      s.stop();
    }
    local.close();
    system.close();
  }

  private TcpEndpoint seederEndpoint() {
    SessionManager s = TestEngine.seed(album, seedDir);
    seeders.add(s);
    TestEngine.await(() -> s.swig().listen_port() > 0, "the seeder listens");
    return new TcpEndpoint("127.0.0.1", s.swig().listen_port());
  }

  /** The engine's download of {@code album}, optionally fed by a local seeder. */
  private BTDownload add(boolean[] selection, boolean withSeeder) throws Exception {
    File saveDir = tmp.newFolder();
    List<TcpEndpoint> peers = withSeeder ? List.of(seederEndpoint()) : null;
    local.engine.download(album, saveDir, selection, peers);
    return TestEngine.Events.next(local.events.added, "album");
  }

  private static void waitUntil(java.util.function.BooleanSupplier condition, String what) {
    TestEngine.await(condition, what);
  }

  @Test
  public void aTransferShowsTheNameSizeAndHashOfItsTorrentAndKeepsItsItemsForLater()
      throws Exception {
    BTDownload dl = add(null, false);

    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);

    assertSame(dl, ui.getDl());
    assertEquals("album", ui.getDisplayName());
    assertEquals(album.totalSize(), ui.getSize());
    assertEquals(album.infoHashV1().toHex(), ui.getInfoHash());
    assertNull(ui.getName());
    assertNull(ui.previewFile());
    assertEquals(dl.getSavePath(), ui.getSavePath());
    assertEquals(dl.getContentSavePath(), ui.getContentSavePath());
    assertEquals(dl.getCreated(), ui.getCreated());
    // the transfers list never loads the files until somebody asks
    assertNull(ui.peekItems());
    assertEquals(2, ui.getItems().size());
    assertNotNull(ui.peekItems());
    ui.clearCachedItems();
    assertNull(ui.peekItems());
    assertEquals(2, ui.getCachedFileCount());
    assertNotNull(ui.getCachedMagnetUri());
    assertEquals(ui.getCachedMagnetUri(), ui.magnetUri());
    assertEquals("", ui.getCachedTorrentComment());
    assertEquals("mp3", ui.getPredominantFileExtension());
    assertTrue(dl.getListener() instanceof UIBTDownloadListener);
    assertTrue(ui.getPaymentOptions().isEmpty());
    assertFalse(ui.hasPaymentOptions());
  }

  @Test
  public void aPartialTransferCountsAndListsOnlyTheFilesTheUserPicked() throws Exception {
    boolean[] onlyTheMp3 = new boolean[2];
    for (int i = 0; i < 2; i++) {
      onlyTheMp3[i] = album.files().filePath(i).endsWith("one.mp3");
    }
    BTDownload dl = add(onlyTheMp3, false);

    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);

    List<TransferItem> items = ui.getItems();
    assertEquals(1, items.size());
    assertEquals("one.mp3", items.get(0).getName());
    assertEquals(items.get(0).getSize(), ui.getSize());
    assertTrue(ui.getSize() < album.totalSize());
    // a refresh recomputes the loaded items and the size
    ui.updateUI(dl);
    assertEquals(1, ui.getItems().size());
    assertEquals(items.get(0).getSize(), ui.getSize());
  }

  @Test
  public void aTransferWithAPeerCompletesAndTheCachedStateFollowsIt() throws Exception {
    BTDownload dl = add(null, false);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    List<String> events = new ArrayList<>();
    TransferStateListener listener =
        new TransferStateListener() {
          @Override
          public void onTransferStateChanged(
              Transfer transfer, TransferState oldState, TransferState newState) {
            events.add(oldState + ">" + newState);
          }

          @Override
          public void onTransferProgressChanged(Transfer transfer, int progress) {
            events.add("progress " + progress);
          }
        };
    ui.addListener(listener);
    ui.addListener(listener); // a listener is only added once
    ui.addListener(null);
    // the peer shows up after the transfer is in the list
    dl.getTorrentHandle()
        .swig()
        .connect_peer(
            seederEndpoint().swig(), new com.frostwire.jlibtorrent.swig.peer_source_flags_t());

    waitUntil(
        () -> {
          ui.updateCachedState();
          return ui.isComplete();
        },
        "the whole album arrived");

    assertEquals(100, ui.getProgress());
    assertTrue(ui.isFinished() || ui.isSeeding());
    assertEquals(0, ui.getETA());
    assertEquals(album.totalSize(), ui.getBytesReceived());
    assertFalse(ui.isPaused());
    assertTrue(events.toString(), events.contains("progress 100"));
    assertTrue(events.toString(), events.stream().anyMatch(e -> e.contains(">")));
    assertTrue(new File(dl.getContentSavePath(), "one.mp3").isFile());
    assertEquals(2, ui.getItems().stream().filter(TransferItem::isComplete).count());
    ui.removeListener(listener);
    int seen = events.size();
    ui.pause();
    ui.updateCachedState();
    assertEquals(seen, events.size());
  }

  @Test
  public void pausingAndResumingKeepsTheCachedStateInSyncWithWhatTheUserDid() throws Exception {
    BTDownload dl = add(null, false);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    assertFalse(ui.isSharingPaused());

    ui.pause();

    assertTrue(ui.isPaused());
    assertEquals(TransferState.PAUSED, ui.getState());
    assertTrue(ui.isSharingPaused());
    assertTrue(dl.wasPaused());
    // pausing twice does not pause libtorrent twice
    ui.pause();
    ui.updateCachedState();
    assertEquals(TransferState.PAUSED, ui.getState());

    ui.resume();

    assertFalse(ui.isSharingPaused());
    assertFalse(dl.wasPaused());
    ui.updateCachedState();
    assertFalse(ui.isPaused());
    assertNotEquals(TransferState.PAUSED, ui.getState());
  }

  @Test
  public void aNetworkPolicyPausesTheTransferWithoutCountingAsTheUsersPause() throws Exception {
    BTDownload dl = add(null, false);
    when(manager.isMobileAndDataSavingsOn()).thenReturn(true);

    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);

    assertTrue(ui.isPaused());
    assertEquals(TransferState.PAUSED, ui.getState());
    assertFalse("a policy pause is not the user's", dl.wasPaused());
    assertFalse(ui.isSharingPaused());
    // the policy lifts: only a policy pause is resumed
    when(manager.isMobileAndDataSavingsOn()).thenReturn(false);
    ui.resumeFromPolicy();
    ui.updateCachedState();
    assertFalse(ui.isPaused());
    // not paused by policy any more: nothing to resume
    ui.resumeFromPolicy();
    // a transfer the user paused ignores the policy
    ui.pause();
    ui.pauseForPolicy();
    assertTrue(dl.wasPaused());
  }

  @Test
  public void aVpnOnlyPolicyWithoutAVpnPausesTheTransferToo() throws Exception {
    BTDownload dl = add(null, false);
    when(manager.isBittorrentOnVpnOnlyAndNoVpn()).thenReturn(true);

    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);

    assertTrue(ui.isPaused());
    assertEquals(TransferState.PAUSED, ui.getState());
  }

  @Test
  public void aFullDiskShowsTheDiskFullState() throws Exception {
    BTDownload dl = add(null, false);
    try (MockedStatic<TransferManager> managers = mockStatic(TransferManager.class)) {
      managers.when(TransferManager::getCurrentMountAvailableBytes).thenReturn(0L);
      UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
      assertEquals(TransferState.ERROR_DISK_FULL, ui.getState());
    }
    // when the free space can't be read the transfer is not blamed for it
    try (MockedStatic<TransferManager> managers = mockStatic(TransferManager.class)) {
      managers
          .when(TransferManager::getCurrentMountAvailableBytes)
          .thenThrow(new IllegalStateException("no storage path"));
      UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
      assertNotEquals(TransferState.ERROR_DISK_FULL, ui.getState());
    }
  }

  @Test
  public void theGlobalSequentialSettingIsAppliedToTheTransfer() throws Exception {
    BTDownload dl = add(null, false);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    boolean before = UIBittorrentDownload.SEQUENTIAL_DOWNLOADS;
    try {
      UIBittorrentDownload.SEQUENTIAL_DOWNLOADS = true;
      ui.checkSequentialDownload();
      dl.refreshStatusCache();
      ui.updateCachedState();
      assertTrue(dl.isSequentialDownload());
      assertTrue(ui.isSequentialDownloadCached());
      UIBittorrentDownload.SEQUENTIAL_DOWNLOADS = false;
      ui.updateUI(dl);
      dl.refreshStatusCache();
      ui.updateCachedState();
      assertFalse(dl.isSequentialDownload());
      assertFalse(ui.isSequentialDownloadCached());
    } finally {
      UIBittorrentDownload.SEQUENTIAL_DOWNLOADS = before;
    }
  }

  @Test
  public void theRateLimitsAreCachedFromLibtorrent() throws Exception {
    BTDownload dl = add(null, false);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    dl.setDownloadRateLimit(12_345);
    dl.setUploadRateLimit(6_789);

    ui.updateCachedState();

    assertEquals(12_345, ui.getCachedDownloadRateLimit());
    assertEquals(6_789, ui.getCachedUploadRateLimit());
    assertEquals(0, ui.getConnectedPeers());
    assertEquals(0, ui.getTotalPeers());
    assertEquals(0, ui.getConnectedSeeds());
    assertEquals(0, ui.getTotalSeeds());
    assertEquals(0, ui.getDownloadSpeed());
    assertEquals(0, ui.getUploadSpeed());
    assertFalse(ui.isDownloading());
    assertEquals(dl.getBytesSent(), ui.getBytesSent());
  }

  @Test
  public void removingATransferLeavesTheListAndTheEngineAndCanDeleteItsFiles() throws Exception {
    BTDownload dl = add(null, true);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    waitUntil(
        () -> {
          ui.updateCachedState();
          return ui.isComplete();
        },
        "the whole album arrived");
    File content = dl.getContentSavePath();
    assertTrue(new File(content, "one.mp3").isFile());

    ui.remove(
        new WeakReference<>(org.robolectric.RuntimeEnvironment.getApplication()), false, true);

    assertTrue(ui.isRemovedFromSharing());
    verify(manager).remove(ui);
    waitUntil(() -> local.engine.find(album) == null, "the engine let go of the torrent");
    waitUntil(() -> !new File(content, "one.mp3").exists(), "the files were deleted");
  }

  @Test
  public void removingWithoutDeletingDataKeepsTheFiles() throws Exception {
    BTDownload dl = add(null, true);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    waitUntil(
        () -> {
          ui.updateCachedState();
          return ui.isComplete();
        },
        "the whole album arrived");
    File one = new File(dl.getContentSavePath(), "one.mp3");

    ui.remove(false);

    verify(manager).remove(ui);
    waitUntil(() -> local.engine.find(album) == null, "the engine let go of the torrent");
    assertTrue(one.isFile());
    ui.remove(true, false);
    assertTrue(one.isFile());
  }

  @Test
  public void aTransferIsKnownByItsInfoHashAlone() throws Exception {
    BTDownload dl = add(null, false);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    UIBittorrentDownload same = new UIBittorrentDownload(manager, dl);

    assertEquals(ui, ui);
    assertEquals(ui, same);
    assertEquals(ui.hashCode(), same.hashCode());
    assertNotEquals(ui, null);
    assertNotEquals(ui, "a string");
    assertEquals(java.util.Objects.hashCode(ui.getInfoHash()), ui.hashCode());
  }

  @Test
  public void aTransferWhoseTorrentLeftTheEngineStillAnswersWithoutCrashing() throws Exception {
    BTDownload dl = add(null, false);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    assertEquals(2, ui.getItems().size());
    local.engine.remove(dl.getTorrentHandle());
    waitUntil(() -> !dl.getTorrentHandle().isValid(), "libtorrent dropped the torrent");

    // every refresh swallows what the dead handle throws and keeps the last known values
    ui.updateCachedState();
    assertEquals(0, ui.getProgress());
    assertNotNull(ui.getState());
    assertNotNull(ui.getItems());
    assertEquals(album.totalSize(), ui.getSize() == 0 ? album.totalSize() : ui.getSize());
  }
}
