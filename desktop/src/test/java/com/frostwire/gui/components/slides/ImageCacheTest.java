/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.slides;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression test for Icebase #463c8cab5e88 / GitHub #1316: building a SlidePanel on the EDT
 * downloaded its https slide image synchronously on the EDT.
 */
class ImageCacheTest {
  private static final String HTTPS_IMAGE =
      "https://static.frostwire.com/images/overlays/regression-1316.png";

  @TempDir File cacheDir;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AtomicInteger fetches = new AtomicInteger();
  private final CopyOnWriteArrayList<Boolean> fetchedOnEdt = new CopyOnWriteArrayList<>();
  private final LinkedBlockingQueue<Result> results = new LinkedBlockingQueue<>();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void httpsImageRequestedOnEdtIsFetchedOffEdt() throws Exception {
    ImageCache cache = new ImageCache(executor, recordingFetcher(pngBytes()), () -> cacheDir);
    URL url = urlOf(HTTPS_IMAGE);

    SwingUtilities.invokeAndWait(() -> cache.getImage(url, this::record));

    Result result = awaitResult();
    assertEquals(1, fetches.get());
    assertFalse(fetchedOnEdt.get(0), "the slide image must not be downloaded on the EDT");
    assertFalse(result.onEdt, "listener is called from the background executor");
    assertNotNull(result.image);
    assertFalse(result.fromCache);
    assertFalse(result.fail);
    assertTrue(
        new File(cacheDir, "static.frostwire.com/images/overlays/regression-1316.png").isFile());
  }

  @Test
  void cachedImageIsReadOffEdtWithoutRefetching() throws Exception {
    ImageCache cache = new ImageCache(executor, recordingFetcher(pngBytes()), () -> cacheDir);
    URL url = urlOf(HTTPS_IMAGE);
    cache.getImage(url, this::record);
    awaitResult();

    SwingUtilities.invokeAndWait(() -> cache.getImage(url, this::record));

    Result cached = awaitResult();
    assertEquals(1, fetches.get(), "second load is served from the disk cache");
    assertTrue(cached.fromCache);
    assertFalse(cached.onEdt);
    assertNotNull(cached.image);
    assertEquals(4, cached.image.getWidth());
  }

  @Test
  void failedDownloadReportsFailure() throws Exception {
    ImageCache cache = new ImageCache(executor, recordingFetcher(null), () -> cacheDir);

    SwingUtilities.invokeAndWait(() -> cache.getImage(urlOf(HTTPS_IMAGE), this::record));

    Result result = awaitResult();
    assertTrue(result.fail);
    assertNull(result.image);
    assertFalse(fetchedOnEdt.get(0));
  }

  private ImageCache.Fetcher recordingFetcher(byte[] data) {
    return url -> {
      fetches.incrementAndGet();
      fetchedOnEdt.add(EventQueue.isDispatchThread());
      return data;
    };
  }

  private void record(URL url, BufferedImage image, boolean fromCache, boolean fail) {
    results.add(new Result(image, fromCache, fail, EventQueue.isDispatchThread()));
  }

  private Result awaitResult() throws InterruptedException {
    Result result = results.poll(10, TimeUnit.SECONDS);
    assertNotNull(result, "listener was never called");
    return result;
  }

  private static URL urlOf(String spec) {
    try {
      return URI.create(spec).toURL();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static byte[] pngBytes() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  private record Result(BufferedImage image, boolean fromCache, boolean fail, boolean onEdt) {}
}
