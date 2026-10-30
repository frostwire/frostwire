/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1379: the Library cover art blocked the EDT in Toolkit.sync() and drew covers
 * loaded in the background into the image paint() reads, off the EDT.
 */
class LibraryCoverArtPanelEdtTest {

  @Test
  void coverLoadedOffEdtIsDrawnOnTheEdt() throws Exception {
    LibraryCoverArtPanel[] holder = new LibraryCoverArtPanel[1];
    SwingUtilities.invokeAndWait(
        () -> {
          holder[0] = new LibraryCoverArtPanel();
          holder[0].setSize(10, 10);
        });
    LibraryCoverArtPanel panel = holder[0];
    awaitDefaultCover(panel);
    BufferedImage cover = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = cover.createGraphics();
    g.setColor(Color.RED);
    g.fillRect(0, 0, 10, 10);
    g.dispose();

    CountDownLatch edtBusy = new CountDownLatch(1);
    CountDownLatch releaseEdt = new CountDownLatch(1);
    SwingUtilities.invokeLater(
        () -> {
          edtBusy.countDown();
          try {
            releaseEdt.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    assertTrue(edtBusy.await(10, TimeUnit.SECONDS));
    try {
      panel.showImage(cover);
      assertEquals(
          0,
          panel.background().getRGB(5, 5),
          "a cover from a background thread must not be drawn while the EDT is busy");
    } finally {
      releaseEdt.countDown();
    }
    SwingUtilities.invokeAndWait(() -> {});
    assertEquals(Color.RED.getRGB(), panel.background().getRGB(5, 5));
  }

  /**
   * A new panel loads the default cover art in the background and then draws it on the EDT. If that
   * lands after the test's cover it paints over it (a slow CI runner did), so let it land first:
   * the background stops being transparent, or the theme image isn't available here and nothing
   * will be drawn. Then clear the background so the busy-EDT check starts from a known state.
   */
  private static void awaitDefaultCover(LibraryCoverArtPanel panel) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    int[] pixel = new int[1];
    do {
      SwingUtilities.invokeAndWait(() -> pixel[0] = panel.background().getRGB(5, 5));
      if (pixel[0] != 0) {
        break;
      }
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    SwingUtilities.invokeAndWait(
        () -> {
          Graphics2D g = panel.background().createGraphics();
          g.setBackground(new Color(0, 0, 0, 0));
          g.clearRect(0, 0, 10, 10);
          g.dispose();
        });
  }

  @Test
  void desktopNeverCallsToolkitSync() throws IOException {
    Path sources = Path.of("src/main/java");
    try (Stream<Path> files = Files.walk(sources)) {
      for (Path file :
          (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        String code = Files.readString(file).replaceAll("\\s+", "");
        assertFalse(
            code.contains("Toolkit().sync()") || code.contains("getToolkit().sync()"),
            file + " calls Toolkit.sync(), which blocks the EDT until the GPU drains its queue");
      }
    }
  }
}
