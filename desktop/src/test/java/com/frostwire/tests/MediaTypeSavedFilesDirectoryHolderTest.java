/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.library.MediaTypeSavedFilesDirectoryHolder;
import com.limegroup.gnutella.MediaType;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class MediaTypeSavedFilesDirectoryHolderTest {
  @Test
  void snapshotsDeduplicateFilesAndDoNotChangeAfterCacheMutation() {
    var holder = new MediaTypeSavedFilesDirectoryHolder(MediaType.getAudioMediaType());
    File first = new File("first.mp3");
    File second = new File("second.mp3");
    holder.addToCache(List.of(first, first));
    File[] snapshot = holder.getFiles();

    holder.getCache().add(second);
    assertEquals(Set.of(first, second), Set.copyOf(Arrays.asList(holder.getFiles())));
    holder.clearCache();
    assertEquals(0, holder.getFiles().length);
    assertEquals(List.of(first), Arrays.asList(snapshot));
  }

  @Test
  void snapshotsAndAllMutationPathsUseTheCacheMonitor() {
    var holder = new MediaTypeSavedFilesDirectoryHolder(MediaType.getAudioMediaType());
    File file = new File("cached.mp3");
    assertDoesNotThrow(
        () -> {
          waitsForCacheMonitor(holder, holder::getFiles);
          waitsForCacheMonitor(holder, () -> new ArrayList<>(holder.getCache()));
          waitsForCacheMonitor(holder, () -> holder.getCache().add(file));
          waitsForCacheMonitor(
              holder,
              () -> {
                holder.addToCache(List.of(file));
                return null;
              });
          waitsForCacheMonitor(
              holder,
              () -> {
                holder.clearCache();
                return null;
              });
        });
  }

  private static void waitsForCacheMonitor(
      MediaTypeSavedFilesDirectoryHolder holder, Callable<?> operation) throws Exception {
    var executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> result;
      synchronized (holder.getCache()) {
        CountDownLatch started = new CountDownLatch(1);
        result =
            executor.submit(
                () -> {
                  started.countDown();
                  return operation.call();
                });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));
      }
      result.get(5, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }
}
