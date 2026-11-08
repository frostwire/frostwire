/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The file chooser's list renderer asked the Windows shell for each file's icon on the EDT, over
 * COM, on every cache miss (Icebase #e51928be4469). A miss must return a placeholder at once and
 * load the icon on the cache's worker.
 */
class AsyncFileLookupCacheTest {

  private final List<Runnable> queued = new ArrayList<>();
  private final AtomicInteger lookups = new AtomicInteger();
  private final AtomicInteger loaded = new AtomicInteger();
  private final Icon systemIcon = new ImageIcon(new java.awt.image.BufferedImage(16, 16, 2));

  @Test
  void aMissReturnsAPlaceholderAndLooksUpOnTheWorker() throws Exception {
    AsyncFileLookupCache<Icon> cache = cache();
    File file = new File("some.torrent");
    AtomicReference<Icon> first = new AtomicReference<>();

    SwingUtilities.invokeAndWait(() -> first.set(cache.get(file, loaded::incrementAndGet)));

    assertNotSame(systemIcon, first.get(), "the EDT must not wait for the shell");
    assertEquals(16, first.get().getIconWidth());
    assertEquals(0, lookups.get(), "the lookup must not run on the calling thread");
    assertEquals(1, queued.size());

    queued.remove(0).run(); // the worker
    SwingUtilities.invokeAndWait(() -> {}); // the result is stored on the EDT

    assertEquals(1, lookups.get());
    assertEquals(1, loaded.get(), "the chooser is told to repaint");
    AtomicReference<Icon> second = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> second.set(cache.get(file, loaded::incrementAndGet)));
    assertSame(systemIcon, second.get());
    assertTrue(queued.isEmpty(), "a cached icon is not looked up again");
  }

  @Test
  void aPendingLookupIsNotQueuedTwice() throws Exception {
    AsyncFileLookupCache<Icon> cache = cache();
    File file = new File("movie.mkv");
    SwingUtilities.invokeAndWait(
        () -> {
          cache.get(file, loaded::incrementAndGet);
          cache.get(file, loaded::incrementAndGet);
          cache.get(file, loaded::incrementAndGet);
        });
    assertEquals(1, queued.size());
  }

  @Test
  void clearingForgetsIcons() throws Exception {
    AsyncFileLookupCache<Icon> cache = cache();
    File file = new File("song.mp3");
    SwingUtilities.invokeAndWait(() -> cache.get(file, loaded::incrementAndGet));
    queued.remove(0).run();
    SwingUtilities.invokeAndWait(cache::clear);
    SwingUtilities.invokeAndWait(() -> cache.get(file, loaded::incrementAndGet));
    assertEquals(1, queued.size(), "after clear the icon is looked up again");
  }

  private AsyncFileLookupCache<Icon> cache() {
    return new AsyncFileLookupCache<>(
        queued::add,
        f -> {
          lookups.incrementAndGet();
          return systemIcon;
        },
        f -> AsyncFileLookupCache.BLANK_ICON);
  }

  /**
   * Display names go through the same cache: the shell's name (Icebase #0d182adae29c,
   * frostwire/frostwire#1428) loads on the worker while the plain file name shows, and a lookup
   * that finds nothing keeps the plain name.
   */
  @Test
  void aNameMissShowsThePlainNameAndLooksUpOnTheWorker() throws Exception {
    AsyncFileLookupCache<String> names =
        new AsyncFileLookupCache<>(
            queued::add,
            f -> {
              lookups.incrementAndGet();
              return f.getName().equals("report.pdf") ? "report" : null;
            },
            File::getName);
    File named = new File("report.pdf");
    File unnamed = new File("notes.txt");
    AtomicReference<String> first = new AtomicReference<>();

    SwingUtilities.invokeAndWait(() -> first.set(names.get(named, loaded::incrementAndGet)));
    SwingUtilities.invokeAndWait(() -> names.get(unnamed, loaded::incrementAndGet));

    assertEquals("report.pdf", first.get(), "the EDT must not wait for the shell");
    assertEquals(0, lookups.get());
    queued.remove(0).run();
    queued.remove(0).run();
    SwingUtilities.invokeAndWait(() -> {});
    assertEquals("report", names.cached(named));
    assertEquals("notes.txt", names.cached(unnamed), "no display name keeps the plain name");
  }
}
