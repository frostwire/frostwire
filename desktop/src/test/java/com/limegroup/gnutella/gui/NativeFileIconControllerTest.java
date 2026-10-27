/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeFileIconControllerTest {
  private static final Icon PLACEHOLDER = new ImageIcon();
  private static final Function<String, Icon> PLACEHOLDERS = ext -> PLACEHOLDER;

  private final ExecutorService loader = Executors.newSingleThreadExecutor();

  @AfterEach
  void stopLoader() {
    loader.shutdownNow();
  }

  @Test
  void extensionLookupsReuseTheSingleNativeViewAndCacheByExtension() {
    CountingFileView view = new CountingFileView();
    NativeFileIconController controller =
        new NativeFileIconController(view, false, Runnable::run, PLACEHOLDERS);

    Icon first = controller.getIconForExtension("atlas");
    assertNotNull(first);
    assertSame(first, controller.getIconForExtension("ATLAS"));
    controller.getIconForExtension("torrent");

    assertEquals(2, view.iconRequests.get(), "One native lookup per distinct extension");
  }

  /** Icebase 156d0cb39356, 7e593a7c43d0, ca89d18667e0: preload ran each COM lookup on the EDT. */
  @Test
  void preloadNeverLooksUpIconsOnTheEdt() throws Exception {
    CountingFileView view = new CountingFileView();
    new NativeFileIconController(view, true, loader, PLACEHOLDERS);
    assertTrue(view.firstRequest.await(10, TimeUnit.SECONDS), "preload looked up icons");
    drain(loader);
    // Anything a preload pushed onto the EDT has run by now.
    SwingUtilities.invokeAndWait(() -> {});

    assertFalse(view.lookedUpOnEdt(), "native icon lookups ran on the EDT: " + view.threads);
  }

  /** Icebase 7e593a7c43d0: the Library table asked for a file's icon while painting. */
  @Test
  void edtRequestsReturnAPlaceholderAndLoadInTheBackground(@TempDir Path dir) throws Exception {
    File song = Files.createFile(dir.resolve("song.mp3")).toFile();
    CountDownLatch release = new CountDownLatch(1);
    CountingFileView view = new CountingFileView(release);
    NativeFileIconController controller =
        new NativeFileIconController(view, false, loader, PLACEHOLDERS);

    // The view blocks like a stalled shell COM call: the EDT must not wait for it.
    AtomicReference<Icon> forFile = new AtomicReference<>();
    AtomicReference<Icon> forExtension = new AtomicReference<>();
    try {
      onEdtWithin(
          2,
          () -> {
            forFile.set(controller.getIconForFile(song));
            forExtension.set(controller.getIconForExtension("ogg"));
            // asking again while the lookups are queued must not queue them twice
            controller.getIconForFile(song);
            controller.getIconForExtension("ogg");
          });
    } finally {
      release.countDown(); // never leave the EDT blocked for the other tests
    }
    assertSame(PLACEHOLDER, forFile.get());
    assertSame(PLACEHOLDER, forExtension.get());

    drain(loader);

    onEdtWithin(
        2,
        () -> {
          forFile.set(controller.getIconForFile(song));
          forExtension.set(controller.getIconForExtension("ogg"));
        });
    assertSame(view.icon, forFile.get(), "the native icon once it is cached");
    assertSame(view.icon, forExtension.get());
    assertEquals(2, view.iconRequests.get(), "one lookup per file and per extension");
    assertFalse(view.lookedUpOnEdt(), "native icon lookups ran on the EDT: " + view.threads);
  }

  @Test
  void aMissingFileOrFailedLookupIsNotRequeuedOnEveryPaint(@TempDir Path dir) throws Exception {
    File missing = dir.resolve("gone.flac").toFile();
    CountingFileView view = new CountingFileView();
    view.returnsNull = true;
    NativeFileIconController controller =
        new NativeFileIconController(view, false, loader, PLACEHOLDERS);

    for (int i = 0; i < 3; i++) {
      onEdtWithin(
          2,
          () -> {
            controller.getIconForFile(missing);
            controller.getIconForExtension("wav");
          });
      drain(loader);
    }

    // "flac" (for the missing file's type) and "wav", once each.
    assertEquals(2, view.iconRequests.get());
    assertFalse(view.lookedUpOnEdt());
  }

  /**
   * Icebase 8cba3fde74da, frostwire/frostwire#1320: on macOS lookups were deferred onto the EDT
   * (invokeLater) instead of the IconLoader thread, so each one still ran LaunchServices there.
   */
  @Test
  void theLookupQueueRunsOffTheEdtOnEveryPlatform() throws Exception {
    Executor queue = NativeFileIconController.newLookupQueue();
    AtomicReference<Boolean> onEdt = new AtomicReference<>();
    CountDownLatch ran = new CountDownLatch(1);
    queue.execute(
        () -> {
          onEdt.set(SwingUtilities.isEventDispatchThread());
          ran.countDown();
        });
    assertTrue(ran.await(10, TimeUnit.SECONDS), "the lookup ran");
    assertFalse(onEdt.get(), "native icon lookups ran on the EDT");
  }

  /**
   * Icebase 8cba3fde74da, frostwire/frostwire#1320: SmartChooserView only remembered which files it
   * had asked for, so a "cached" icon was fetched again from the chooser's view on the EDT, and
   * macOS's AquaFileView keeps only 256 files, asking LaunchServices again for the rest.
   */
  @Test
  void chooserViewIconsAreServedFromItsOwnCacheOnTheEdt(@TempDir Path dir) throws Exception {
    File song = Files.createFile(dir.resolve("song.m4a")).toFile();
    ForgetfulChooserView chooserView = new ForgetfulChooserView();
    NativeFileIconController controller =
        new NativeFileIconController(
            new NativeFileIconController.SmartChooserView(chooserView),
            false,
            loader,
            PLACEHOLDERS);

    AtomicReference<Icon> icon = new AtomicReference<>();
    onEdtWithin(2, () -> icon.set(controller.getIconForFile(song)));
    assertSame(PLACEHOLDER, icon.get());
    drain(loader);
    for (int refresh = 0; refresh < 3; refresh++) {
      onEdtWithin(2, () -> icon.set(controller.getIconForFile(song)));
      assertSame(chooserView.icon, icon.get(), "the native icon once it is cached");
    }

    assertEquals(1, chooserView.iconRequests.get(), "one native lookup for the file");
    assertFalse(chooserView.edt, "native icon lookups ran on the EDT: " + chooserView.threads);
  }

  /** AquaFileView's file cache is an unsynchronized LinkedHashMap: calls into it are serialized. */
  @Test
  void chooserViewLookupsNeverOverlap(@TempDir Path dir) throws Exception {
    ForgetfulChooserView chooserView = new ForgetfulChooserView();
    NativeFileIconController.SmartChooserView view =
        new NativeFileIconController.SmartChooserView(chooserView);
    ExecutorService callers = Executors.newFixedThreadPool(4);
    try {
      for (int i = 0; i < 40; i++) {
        File f = dir.resolve("file" + i + ".mp3").toFile();
        callers.execute(() -> view.getIcon(f));
      }
    } finally {
      callers.shutdown();
    }
    assertTrue(callers.awaitTermination(10, TimeUnit.SECONDS));
    assertEquals(40, chooserView.iconRequests.get());
    assertFalse(chooserView.overlapped, "two threads were inside the chooser's view at once");
  }

  private static void onEdtWithin(int seconds, Runnable r) throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    SwingUtilities.invokeLater(
        () -> {
          try {
            r.run();
          } finally {
            done.countDown();
          }
        });
    assertTrue(done.await(seconds, TimeUnit.SECONDS), "the EDT waited for a native icon lookup");
  }

  private static void drain(ExecutorService executor) throws Exception {
    executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
  }

  private static final class CountingFileView extends NativeFileIconController.SmartFileView {
    private final Icon icon = new ImageIcon();
    private final AtomicInteger iconRequests = new AtomicInteger();
    private final List<String> threads = new CopyOnWriteArrayList<>();
    private final Map<File, Icon> cache = new ConcurrentHashMap<>();
    private final CountDownLatch release;
    private final CountDownLatch firstRequest = new CountDownLatch(1);
    private volatile boolean edt;
    private volatile boolean returnsNull;

    CountingFileView() {
      this(new CountDownLatch(0));
    }

    CountingFileView(CountDownLatch release) {
      this.release = release;
    }

    boolean lookedUpOnEdt() {
      return edt;
    }

    @Override
    public Icon getIcon(File file) {
      Icon cached = cache.get(file);
      if (cached != null) {
        return cached;
      }
      iconRequests.incrementAndGet();
      threads.add(Thread.currentThread().getName());
      if (SwingUtilities.isEventDispatchThread()) {
        edt = true;
      }
      firstRequest.countDown();
      try {
        release.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      if (returnsNull) {
        return null;
      }
      cache.put(file, icon);
      return icon;
    }

    @Override
    public boolean isIconCached(File file) {
      return cache.containsKey(file);
    }

    @Override
    public boolean removeFromCache(File file) {
      return cache.remove(file) != null;
    }
  }

  /** A chooser FileView like AquaFileView once a file has left its cache: every call is native. */
  private static final class ForgetfulChooserView extends FileView {
    private final Icon icon = new ImageIcon();
    private final AtomicInteger iconRequests = new AtomicInteger();
    private final AtomicInteger inside = new AtomicInteger();
    private final List<String> threads = new CopyOnWriteArrayList<>();
    private volatile boolean edt;
    private volatile boolean overlapped;

    @Override
    public Icon getIcon(File file) {
      if (inside.incrementAndGet() > 1) {
        overlapped = true;
      }
      try {
        iconRequests.incrementAndGet();
        threads.add(Thread.currentThread().getName());
        if (SwingUtilities.isEventDispatchThread()) {
          edt = true;
        }
        Thread.sleep(2);
        return icon;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return icon;
      } finally {
        inside.decrementAndGet();
      }
    }
  }
}
