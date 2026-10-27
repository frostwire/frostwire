/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SplashWindowIconCacheTest {

  /**
   * The splash's paint reads the OS icon cache under its monitor on the EDT; the preload thread
   * held that monitor while decoding each PNG, so the paint waited for the decode (Icebase
   * #764edb6b2f73, GitHub #1341).
   */
  @Test
  void cacheIsReadableWhileAnIconDecodes() throws Exception {
    Map<String, BufferedImage> cache = new HashMap<>();
    BufferedImage icon = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    CountDownLatch decoding = new CountDownLatch(1);
    CountDownLatch finishDecode = new CountDownLatch(1);
    Thread preload =
        new Thread(
            () ->
                SplashWindow.cacheIfAbsent(
                    cache,
                    "linux_on",
                    () -> {
                      decoding.countDown();
                      try {
                        finishDecode.await();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return icon;
                    }));
    preload.start();
    assertTrue(decoding.await(5, TimeUnit.SECONDS));

    CountDownLatch read = new CountDownLatch(1);
    Thread paint =
        new Thread(
            () -> {
              synchronized (cache) {
                cache.get("linux_on");
              }
              read.countDown();
            });
    paint.start();
    try {
      assertTrue(read.await(5, TimeUnit.SECONDS), "paint blocked on the decode");
    } finally {
      finishDecode.countDown();
      preload.join();
      paint.join();
    }
    assertSame(icon, cache.get("linux_on"));
  }

  @Test
  void cachedIconsAreNotDecodedAgainAndMissingOnesAreNotStored() {
    Map<String, BufferedImage> cache = new HashMap<>();
    BufferedImage icon = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    AtomicInteger decodes = new AtomicInteger();

    SplashWindow.cacheIfAbsent(cache, "mac_off", () -> null);
    SplashWindow.cacheIfAbsent(
        cache,
        "mac_on",
        () -> {
          decodes.incrementAndGet();
          return icon;
        });
    SplashWindow.cacheIfAbsent(
        cache,
        "mac_on",
        () -> {
          decodes.incrementAndGet();
          return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        });

    assertNull(cache.get("mac_off"));
    assertSame(icon, cache.get("mac_on"));
    assertEquals(1, decodes.get());
  }
}
