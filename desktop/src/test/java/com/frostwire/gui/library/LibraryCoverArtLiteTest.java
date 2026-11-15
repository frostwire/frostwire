/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.PerformanceMode;
import com.frostwire.gui.PerformanceMode.Choice;
import com.frostwire.gui.library.tags.TagsReader;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** In Lite the Library reads a file's cover only when asked (frostwire/frostwire#1555). */
class LibraryCoverArtLiteTest {
  @AfterEach
  void backToFull() {
    PerformanceMode.resolve(Choice.FULL, null);
  }

  @Test
  void liteReadsTheCoverOnlyOnceTheUserClicks() throws Exception {
    PerformanceMode.resolve(Choice.LITE, null);
    LibraryCoverArtPanel panel = newPanel();
    CountingReader reader = new CountingReader();
    SwingUtilities.invokeAndWait(() -> panel.showCoverFor(reader));
    assertFalse(reader.read.await(300, TimeUnit.MILLISECONDS), "Lite read the cover unasked");

    SwingUtilities.invokeAndWait(panel::coverRequested);
    assertTrue(reader.read.await(10, TimeUnit.SECONDS), "the click didn't read the cover");
    SwingUtilities.invokeAndWait(panel::coverRequested);
    Thread.sleep(100);
    assertEquals(1, reader.reads, "a second click read it again");
  }

  @Test
  void fullReadsTheCoverWhenTheFileIsSelected() throws Exception {
    LibraryCoverArtPanel panel = newPanel();
    CountingReader reader = new CountingReader();
    SwingUtilities.invokeAndWait(() -> panel.showCoverFor(reader));
    assertTrue(reader.read.await(10, TimeUnit.SECONDS), "Full didn't read the cover");
  }

  private static LibraryCoverArtPanel newPanel() throws Exception {
    LibraryCoverArtPanel[] holder = new LibraryCoverArtPanel[1];
    SwingUtilities.invokeAndWait(
        () -> {
          holder[0] = new LibraryCoverArtPanel();
          holder[0].setSize(10, 10);
        });
    return holder[0];
  }

  private static final class CountingReader extends TagsReader {
    final CountDownLatch read = new CountDownLatch(1);
    volatile int reads;

    CountingReader() {
      super(new File("song.mp3"));
    }

    @Override
    public BufferedImage getArtwork() {
      reads++;
      read.countDown();
      return null;
    }
  }
}
