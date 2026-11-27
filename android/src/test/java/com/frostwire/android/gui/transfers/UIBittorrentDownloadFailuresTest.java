/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.transfers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

import android.app.Application;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.libtorrent.LoopbackTorrent;
import com.frostwire.android.libtorrent.TestEngine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.transfers.TransferItem;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;
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
 * libtorrent reads can fail when a torrent leaves the session while the screen asks about it: the
 * wrapper must keep answering with what it has. The real transfer is wrapped by a spy that makes
 * one read fail; every other call goes to libtorrent.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class UIBittorrentDownloadFailuresTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private LoopbackTorrent torrent;
  private TransferManager manager;
  private MockedStatic<SystemUtils> system;
  private MockedStatic<TransferManager> managers;

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
    managers = mockStatic(TransferManager.class);
    managers.when(TransferManager::getCurrentMountAvailableBytes).thenReturn(Long.MAX_VALUE);
    torrent = new LoopbackTorrent(tmp.newFolder("root").toPath());
    manager = mock(TransferManager.class);
  }

  @After
  public void tearDown() {
    torrent.close();
    managers.close();
    system.close();
  }

  private BTDownload realDownload() throws Exception {
    BTDownload dl = torrent.add(null, false);
    dl.setListener(
        new com.frostwire.bittorrent.BTDownloadListener() {
          @Override
          public void finished(BTDownload finished) {}

          @Override
          public void removed(BTDownload removed, java.util.Set<File> incompleteFiles) {}
        });
    return spy(dl);
  }

  @Test
  public void aMagnetThatCannotBeReadIsRetriedWithTheNextRefresh() throws Exception {
    BTDownload dl = realDownload();
    doThrow(new IllegalStateException("handle gone")).when(dl).magnetUri();

    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);

    assertNull(ui.getCachedMagnetUri());
    org.mockito.Mockito.doCallRealMethod().when(dl).magnetUri();
    ui.updateCachedState();
    assertTrue(ui.getCachedMagnetUri().startsWith("magnet:?xt=urn:btih:"));
  }

  @Test
  public void aFileExtensionAndTorrentMetadataThatCannotBeReadKeepTheirDefaults() throws Exception {
    BTDownload dl = realDownload();
    doThrow(new IllegalStateException("handle gone")).when(dl).getPredominantFileExtension();
    doThrow(new IllegalStateException("handle gone")).when(dl).getTorrentHandle();

    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);

    assertEquals("torrent", ui.getPredominantFileExtension());
    assertEquals(-1, ui.getCachedFileCount());
    assertNull(ui.getCachedTorrentComment());
  }

  @Test
  public void aSizeThatCannotBeReadIsZero() throws Exception {
    BTDownload noSize = realDownload();
    doThrow(new IllegalStateException("handle gone")).when(noSize).getSize();

    assertEquals(0, new UIBittorrentDownload(manager, noSize).getSize());
  }

  @Test
  public void aFailedPartialCheckKeepsTheTorrentSize() throws Exception {
    BTDownload noPartial = realDownload();
    doThrow(new IllegalStateException("handle gone")).when(noPartial).isPartial();

    assertEquals(torrent.album.totalSize(), new UIBittorrentDownload(manager, noPartial).getSize());
  }

  @Test
  public void aPartialTransferWhoseItemsCannotBeReadKeepsTheTorrentSize() throws Exception {
    BTDownload dl = realDownload();
    org.mockito.Mockito.doReturn(true).when(dl).isPartial();
    doThrow(new IllegalStateException("handle gone")).when(dl).getItems();

    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);

    assertEquals(torrent.album.totalSize(), ui.getSize());
    assertTrue(ui.getItems().isEmpty());
  }

  @Test
  public void anItemThatCannotSayWhetherItIsSkippedIsLeftOutOfTheSizeAndTheItems()
      throws Exception {
    BTDownload dl = realDownload();
    List<TransferItem> real = dl.getItems();
    TransferItem broken = mock(TransferItem.class);
    doThrow(new IllegalStateException("item gone")).when(broken).isSkipped();
    org.mockito.Mockito.doReturn(true).when(dl).isPartial();
    org.mockito.Mockito.doReturn(List.of(real.get(0), broken)).when(dl).getItems();

    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);

    assertEquals(real.get(0).getSize(), ui.getSize());
    assertEquals(List.of(real.get(0)), ui.getItems());
  }

  @Test
  public void aFailureRemovingFromTheEngineStillRemovesTheTransferFromTheList() throws Exception {
    BTDownload dl = realDownload();
    doThrow(new IllegalStateException("engine busy")).when(dl).remove(anyBoolean(), anyBoolean());
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);

    ui.remove(true, true);

    org.mockito.Mockito.verify(manager).remove(ui);
    assertTrue(ui.isRemovedFromSharing());
  }

  @Test
  @Config(sdk = 29, application = Application.class)
  public void onAndroid10TheCompleteFilesAreRemovedFromTheMediaStoreAndDisk() throws Exception {
    BTDownload real = torrent.add(null, true);
    real.setListener(
        new com.frostwire.bittorrent.BTDownloadListener() {
          @Override
          public void finished(BTDownload finished) {}

          @Override
          public void removed(BTDownload removed, java.util.Set<File> incompleteFiles) {}
        });
    BTDownload dl = spy(real);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    TestEngine.await(
        () -> {
          dl.refreshStatusCache();
          ui.updateCachedState();
          return ui.isComplete();
        },
        "the album arrived");
    // the engine is left alone, so the files are the wrapper's to delete
    doNothing().when(dl).remove(anyBoolean(), anyBoolean());
    List<File> files = new java.util.ArrayList<>();
    for (TransferItem item : ui.getItems()) {
      assertTrue(item.getFile().isFile());
      files.add(item.getFile());
    }
    try (MockedStatic<Librarian> librarian = mockStatic(Librarian.class)) {
      ui.remove(new WeakReference<>(RuntimeEnvironment.getApplication()), false, true);

      for (File file : files) {
        librarian.verify(() -> Librarian.mediaStoreDeleteFromDownloads(file), atLeastOnce());
      }
    }
    for (File file : files) {
      assertFalse(file.exists());
    }
  }

  @Test
  public void aTransferWhoseContextIsGoneKeepsItsFilesWhenRemoved() throws Exception {
    BTDownload real = torrent.add(null, true);
    real.setListener(
        new com.frostwire.bittorrent.BTDownloadListener() {
          @Override
          public void finished(BTDownload finished) {}

          @Override
          public void removed(BTDownload removed, java.util.Set<File> incompleteFiles) {}
        });
    BTDownload dl = spy(real);
    UIBittorrentDownload ui = new UIBittorrentDownload(manager, dl);
    TestEngine.await(
        () -> {
          dl.refreshStatusCache();
          ui.updateCachedState();
          return ui.isComplete();
        },
        "the album arrived");
    doNothing().when(dl).remove(anyBoolean(), anyBoolean());
    File first = ui.getItems().get(0).getFile();

    ui.remove(new WeakReference<>(null), false, true);

    assertTrue(first.isFile());
  }
}
