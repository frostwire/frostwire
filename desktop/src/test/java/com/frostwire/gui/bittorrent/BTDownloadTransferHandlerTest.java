/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.limegroup.gnutella.gui.dnd.FileTransferable;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BTDownloadTransferHandlerTest {

  @TempDir Path dir;

  private static BTDownload savedAt(File saveLocation, AtomicInteger diskTouches) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) -> {
              if ("getSaveLocation".equals(method.getName())) {
                diskTouches.incrementAndGet();
                return saveLocation;
              }
              return null;
            });
  }

  /**
   * Dragging a transfer used to walk its whole folder on the EDT when the drag started (Icebase
   * #f8ab9e82200f). The walk now waits until a drop target asks for the files.
   */
  @Test
  void filesAreListedWhenDroppedNotWhenTheDragStarts() throws Exception {
    Path album = Files.createDirectories(dir.resolve("album/disc 1"));
    File track = Files.createFile(album.resolve("01.mp3")).toFile();
    File partial = Files.createFile(album.resolve("02.mp3")).toFile();
    File single = Files.createFile(dir.resolve("movie.mp4")).toFile();
    AtomicInteger diskTouches = new AtomicInteger();
    AtomicInteger ignorableLookups = new AtomicInteger();

    FileTransferable transferable =
        BTDownloadTransferHandler.transferableFor(
            new BTDownload[] {
              savedAt(dir.resolve("album").toFile(), diskTouches), savedAt(single, diskTouches)
            },
            () -> {
              ignorableLookups.incrementAndGet();
              return new HashSet<>(Set.of(partial));
            });
    transferable.getTransferDataFlavors();

    assertEquals(0, diskTouches.get(), "starting the drag must not touch the downloads");
    assertEquals(0, ignorableLookups.get());

    @SuppressWarnings("unchecked")
    List<File> dropped = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
    assertEquals(List.of(track, single), dropped);

    transferable.getTransferData(DataFlavor.javaFileListFlavor);
    assertEquals(2, diskTouches.get(), "listed once");
    assertEquals(1, ignorableLookups.get());
  }
}
