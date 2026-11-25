/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.dnd.FileTransferable;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BTDownloadTransferHandlerTest {

  @TempDir Path dir;

  private static BTDownload savedAt(File saveLocation, AtomicInteger diskTouches) {
    return savedAt(saveLocation, diskTouches, ConcurrentHashMap.newKeySet());
  }

  private static BTDownload savedAt(
      File saveLocation, AtomicInteger diskTouches, Set<Thread> callers) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) -> {
              if ("getSaveLocation".equals(method.getName())) {
                diskTouches.incrementAndGet();
                callers.add(Thread.currentThread());
                return saveLocation;
              }
              return null;
            });
  }

  /**
   * Dragging a transfer used to walk its whole folder on the EDT when the drag started (Icebase
   * #f8ab9e82200f), and later when a drop target asked for the files, which macOS does on the EDT
   * (Icebase #5ca136ac5d64). The walk now starts on a background thread as the drag begins, so the
   * target finds it done.
   */
  @Test
  void filesAreListedInTheBackgroundFromTheStartOfTheDrag() throws Exception {
    Path album = Files.createDirectories(dir.resolve("album/disc 1"));
    File track = Files.createFile(album.resolve("01.mp3")).toFile();
    File partial = Files.createFile(album.resolve("02.mp3")).toFile();
    File single = Files.createFile(dir.resolve("movie.mp4")).toFile();
    AtomicInteger diskTouches = new AtomicInteger();
    AtomicInteger ignorableLookups = new AtomicInteger();
    Thread dragThread = Thread.currentThread();
    Set<Thread> walkers = ConcurrentHashMap.newKeySet();
    CountDownLatch walked = new CountDownLatch(1);

    FileTransferable transferable =
        BTDownloadTransferHandler.transferableFor(
            new BTDownload[] {
              savedAt(dir.resolve("album").toFile(), diskTouches, walkers),
              savedAt(single, diskTouches, walkers)
            },
            () -> {
              ignorableLookups.incrementAndGet();
              walked.countDown();
              return new HashSet<>(Set.of(partial));
            });
    transferable.getTransferDataFlavors();

    assertTrue(walked.await(10, TimeUnit.SECONDS), "the walk never started on its own");
    @SuppressWarnings("unchecked")
    List<File> dropped = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
    assertEquals(List.of(track, single), dropped);
    assertFalse(walkers.contains(dragThread), "the walk ran on the thread that started the drag");

    transferable.getTransferData(DataFlavor.javaFileListFlavor);
    assertEquals(2, diskTouches.get(), "listed once");
    assertEquals(1, ignorableLookups.get());
  }

  /**
   * Dropping a transfer back on the transfers list listed every file in its folder on the EDT,
   * because the default handlers asked for the files to see whether they were torrents (Icebase
   * #ea5453ae3ccf, GitHub #1511). The list turns down its own drags from the flavors alone.
   */
  @Test
  void theTransfersListTurnsDownItsOwnDragsWithoutListingFiles() throws Exception {
    Path album = Files.createDirectories(dir.resolve("album/disc 1"));
    Files.createFile(album.resolve("01.mp3"));
    AtomicInteger diskTouches = new AtomicInteger();
    Set<Thread> walkers = ConcurrentHashMap.newKeySet();
    FileTransferable transferable =
        BTDownloadTransferHandler.transferableFor(
            new BTDownload[] {savedAt(dir.resolve("album").toFile(), diskTouches, walkers)},
            HashSet::new);
    BTDownloadTransferHandler handler = new BTDownloadTransferHandler();

    assertFalse(handler.canImport(null, transferable.getTransferDataFlavors()));
    assertFalse(handler.importData(null, transferable));
    // The drag's own background walk may touch the disk, but never this (EDT) thread.
    assertFalse(
        walkers.contains(Thread.currentThread()), "the drop listed the transfer's files itself");
  }

  /** Files dragged from other applications still drop on the transfers list. */
  @Test
  void theTransfersListStillAcceptsFilesFromOtherApplications() throws Exception {
    File torrent = Files.createFile(dir.resolve("ubuntu.torrent")).toFile();
    FileTransferable transferable = new FileTransferable(List.of(torrent));
    assertFalse(
        List.of(transferable.getTransferDataFlavors())
            .contains(FileTransferable.LISTED_ON_DROP_FLAVOR));
    assertTrue(
        new BTDownloadTransferHandler().canImport(null, transferable.getTransferDataFlavors()));
  }
}
