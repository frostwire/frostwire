/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.media.MediaScannerConnection;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.libtorrent.LoopbackTorrent;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTDownload;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * What the app does when libtorrent reports a transfer finished, removed or changed: the finished
 * notification, the seeding policy, the clean-up of leftovers and the hand-off of the files to the
 * media library, with a real engine and a real download.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class UIBTDownloadListenerTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private LoopbackTorrent torrent;
  private ConfigurationManager config;
  private NetworkManager network;
  private Engine engine;
  private TransferManager manager;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<ConfigurationManager> configStatic;
  private MockedStatic<NetworkManager> networkStatic;
  private MockedStatic<Engine> engineStatic;
  private MockedStatic<TransferManager> managerStatic;
  private final UIBTDownloadListener listener = new UIBTDownloadListener();

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
    config = mock(ConfigurationManager.class);
    network = mock(NetworkManager.class);
    engine = mock(Engine.class);
    manager = mock(TransferManager.class);
    configStatic = mockStatic(ConfigurationManager.class);
    configStatic.when(ConfigurationManager::instance).thenReturn(config);
    networkStatic = mockStatic(NetworkManager.class);
    networkStatic.when(NetworkManager::instance).thenReturn(network);
    engineStatic = mockStatic(Engine.class);
    engineStatic.when(Engine::instance).thenReturn(engine);
    managerStatic = mockStatic(TransferManager.class);
    managerStatic.when(TransferManager::instance).thenReturn(manager);
  }

  @After
  public void tearDown() {
    managerStatic.close();
    engineStatic.close();
    networkStatic.close();
    configStatic.close();
    torrent.close();
    system.close();
  }

  private BTDownload completed() throws Exception {
    BTDownload dl = torrent.add(null, true);
    TestEngine.await(
        () -> {
          dl.refreshStatusCache();
          return dl.isComplete();
        },
        "the whole album arrived");
    return dl;
  }

  private void seedFinishedTorrents(boolean seed, boolean wifiOnly, boolean wifiUp) {
    when(config.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS)).thenReturn(seed);
    when(config.getBoolean(Constants.PREF_KEY_TORRENT_SEED_FINISHED_TORRENTS_WIFI_ONLY))
        .thenReturn(wifiOnly);
    when(network.isDataWIFIUp()).thenReturn(wifiUp);
  }

  @Test
  public void aFinishedTransferIsAnnouncedCountedForReviewAndRefreshed() throws Exception {
    seedFinishedTorrents(true, false, true);
    BTDownload dl = completed();
    UIBittorrentDownload ui = mock(UIBittorrentDownload.class);
    when(manager.getBittorrentDownload(dl.getInfoHash())).thenReturn(ui);

    listener.finished(dl);

    verify(ui).updateCachedState();
    verify(manager).incrementDownloadsToReview();
    verify(engine)
        .notifyDownloadFinished("album", dl.getSavePath().getAbsoluteFile(), dl.getInfoHash());
    // seeding is on: the transfer keeps running
    assertFalse(dl.wasPaused());
    // the files are handed to the media library on Android 10 and newer
    assertTrue(new File(dl.getContentSavePath(), "one.mp3").isFile());
  }

  @Test
  public void aFinishedTransferWithSeedingOffIsPaused() throws Exception {
    seedFinishedTorrents(false, false, true);
    BTDownload dl = completed();

    listener.finished(dl);

    assertTrue(dl.wasPaused());
    verify(manager).incrementDownloadsToReview();
  }

  @Test
  public void seedingOnWifiOnlyPausesTheTransferWhileOnMobileData() throws Exception {
    seedFinishedTorrents(true, true, false);
    BTDownload mobile = completed();
    listener.finished(mobile);
    assertTrue(mobile.wasPaused());
  }

  @Test
  public void seedingOnWifiOnlyKeepsTheTransferRunningOnWifi() throws Exception {
    seedFinishedTorrents(true, true, true);
    BTDownload dl = completed();
    listener.finished(dl);
    assertFalse(dl.wasPaused());
  }

  @Test
  public void aFinishedTransferThatIsNotInTheListIsStillNotified() throws Exception {
    seedFinishedTorrents(true, false, true);
    BTDownload dl = completed();
    when(manager.getBittorrentDownload(anyString())).thenReturn(null);

    listener.finished(dl);

    verify(manager).incrementDownloadsToReview();
    verify(engine).notifyDownloadFinished(anyString(), any(File.class), anyString());
  }

  @Test
  @Config(sdk = 29, application = Application.class)
  public void onAndroid10FinishedFilesAreCopiedToTheMediaStore() throws Exception {
    seedFinishedTorrents(true, false, true);
    BTDownload dl = completed();
    try (MockedStatic<Librarian> librarian = mockStatic(Librarian.class)) {
      listener.finished(dl);
      librarian.verify(
          () -> Librarian.mediaStoreSaveToDownloads(any(File.class), any(File.class), eq(true)),
          times(2));
    }
  }

  @Test
  public void onAndroid11AndNewerFinishedFilesAreHandedToTheMediaScanner() throws Exception {
    seedFinishedTorrents(true, false, true);
    BTDownload dl = completed();
    when(engine.getApplication()).thenReturn(RuntimeEnvironment.getApplication());
    try (MockedStatic<Librarian> librarian = mockStatic(Librarian.class);
        MockedStatic<MediaScannerConnection> scanner = mockStatic(MediaScannerConnection.class)) {
      listener.finished(dl);
      librarian.verify(
          () -> Librarian.mediaStoreSaveToDownloads(any(File.class), any(File.class), eq(true)),
          never());
      scanner.verify(
          () ->
              MediaScannerConnection.scanFile(
                  any(Context.class),
                  any(String[].class),
                  any(String[].class),
                  any(MediaScannerConnection.OnScanCompletedListener.class)),
          times(2));
    }
    verify(manager).incrementDownloadsToReview();
  }

  @Test
  public void withoutAnApplicationContextNothingIsHandedToTheMediaScanner() throws Exception {
    seedFinishedTorrents(true, false, true);
    BTDownload dl = completed();
    when(engine.getApplication()).thenReturn(null);
    try (MockedStatic<MediaScannerConnection> scanner = mockStatic(MediaScannerConnection.class);
        MockedStatic<com.frostwire.android.gui.MainApplication> main =
            mockStatic(com.frostwire.android.gui.MainApplication.class)) {
      listener.finished(dl);
      scanner.verifyNoInteractions();
      main.verify(com.frostwire.android.gui.MainApplication::context, atLeastOnce());
    }
  }

  @Test
  public void leftoverBinFilesInTheTransferFolderAreRenamed() throws Exception {
    seedFinishedTorrents(true, false, true);
    BTDownload dl = completed();
    File folder = dl.getContentSavePath();
    File nested = new File(folder, "nested");
    assertTrue(nested.mkdirs());
    Files.write(new File(folder, "song.mp3.bin").toPath(), new byte[] {1});
    Files.write(new File(nested, "deep.txt.bin").toPath(), new byte[] {2});

    listener.finished(dl);

    assertTrue(new File(folder, "song.mp3").isFile());
    assertFalse(new File(folder, "song.mp3.bin").exists());
    assertTrue(new File(nested, "deep.txt").isFile());
  }

  @Test
  public void aRemovedTransferDeletesItsIncompleteFilesAndEmptyFolders() throws Exception {
    BTDownload dl = torrent.add(null, false);
    File save = dl.getSavePath();
    File leftover = new File(new File(save, "album"), "partial.part");
    assertTrue(leftover.getParentFile().mkdirs());
    Files.write(leftover.toPath(), new byte[] {1, 2, 3});
    File stubborn = new File(save, "gone.tmp");
    Set<File> incomplete = new HashSet<>();
    incomplete.add(leftover);
    incomplete.add(stubborn); // never existed
    incomplete.add(null);

    listener.removed(dl, incomplete);

    assertFalse(leftover.exists());
    // the folder it was in has nothing left and goes away with the save folder
    assertFalse(save.exists());
  }

  @Test
  public void aRemovedTransferWithoutLeftoversStillTidiesItsFolder() throws Exception {
    BTDownload dl = torrent.add(null, false);

    listener.removed(dl, null);
    listener.removed(dl, new HashSet<>());

    assertFalse("an empty save folder goes away", dl.getSavePath().exists());
  }

  @Test
  public void aRemovalRunsOnABackgroundThreadWhenItIsAskedFromTheUiThread() throws Exception {
    BTDownload dl = torrent.add(null, false);
    system.when(SystemUtils::isUIThread).thenReturn(true, false);

    listener.removed(dl, new HashSet<>());

    system.verify(
        () ->
            SystemUtils.postToHandler(eq(SystemUtils.HandlerThreadName.MISC), any(Runnable.class)));
  }

  @Test
  public void aStateChangeRefreshesTheSharedWrapperOnTheDownloaderThread() throws Exception {
    BTDownload dl = torrent.add(null, false);
    UIBittorrentDownload ui = mock(UIBittorrentDownload.class);
    when(manager.getBittorrentDownload(dl.getInfoHash())).thenReturn(ui);

    listener.stateChanged(dl);

    system.verify(
        () ->
            SystemUtils.postToHandler(
                eq(SystemUtils.HandlerThreadName.DOWNLOADER), any(Runnable.class)));
    verify(ui).updateCachedState();
  }

  @Test
  public void aStateChangeOfATransferThatIsNotInTheListOnlyRefreshesItsOwnStatus()
      throws Exception {
    BTDownload dl = torrent.add(null, false);
    when(manager.getBittorrentDownload(anyString())).thenReturn(null);

    listener.stateChanged(dl);

    verify(manager).getBittorrentDownload(dl.getInfoHash());
  }
}
