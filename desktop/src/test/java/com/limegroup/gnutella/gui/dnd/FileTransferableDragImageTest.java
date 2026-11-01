/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.dnd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Dragging a transfer listed its whole folder tree and asked jlibtorrent for its incomplete files
 * on the EDT as the drag started: the files were only listed on drop, but drawing the drag image
 * asked for them (Icebase #fb9dd9c71f5d, #953a726c625d; GitHub #1459). The drag image now shows a
 * preview that needs no listing.
 */
class FileTransferableDragImageTest {

  @Test
  void theDragImageShowsThePreviewWithoutListingTheFiles() throws Exception {
    AtomicInteger listings = new AtomicInteger();
    File folder = new File("album");
    File track = new File("album/01.mp3");
    FileTransferable transferable =
        FileTransferable.listedOnDrop(
            () -> {
              listings.incrementAndGet();
              return List.of(track);
            },
            () -> List.of(folder));

    assertEquals(List.of(folder), transferable.filesForDragImage());
    assertEquals(0, listings.get(), "drawing the drag image must not list the files");

    @SuppressWarnings("unchecked")
    List<File> dropped = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
    assertEquals(List.of(track), dropped);
    assertEquals(1, listings.get());
    assertEquals(List.of(track), transferable.filesForDragImage(), "once listed, the real files");
  }

  @Test
  void transferVisualizerAsksForTheDragImageFiles() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve(
                        "src/main/java/com/limegroup/gnutella/gui/dnd/TransferVisualizer.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertTrue(source.contains(".filesForDragImage()"), "the image must not list pending files");
  }
}
