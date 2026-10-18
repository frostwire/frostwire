/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BTDownloadModelRemoveTest {

  private static Object defaultFor(Class<?> type) {
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == double.class) return 0d;
    if (type == float.class) return 0f;
    return null;
  }

  /** A download whose remove() reports the thread it ran on. */
  private static BTDownload download(AtomicReference<Thread> removedOn, CountDownLatch removed) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "remove" -> {
                  removedOn.set(Thread.currentThread());
                  removed.countDown();
                  return null;
                }
                case "getHash" -> {
                  return "0123456789abcdef0123456789abcdef01234567";
                }
                case "getDisplayName" -> {
                  return "fake";
                }
                case "hashCode" -> {
                  return System.identityHashCode(proxy);
                }
                case "equals" -> {
                  return proxy == args[0];
                }
                default -> {
                  return defaultFor(method.getReturnType());
                }
              }
            });
  }

  @Test
  void removingARowRemovesTheTransferOffTheCallingThread() throws Exception {
    AtomicReference<Thread> removedOn = new AtomicReference<>();
    CountDownLatch removed = new CountDownLatch(1);
    BTDownloadModel model = new BTDownloadModel();
    model.add(download(removedOn, removed));
    assertEquals(1, model.getRowCount());

    model.remove(0);

    assertEquals(0, model.getRowCount(), "the row disappears right away");
    assertTrue(removed.await(5, TimeUnit.SECONDS), "the transfer is still removed");
    assertNotSame(Thread.currentThread(), removedOn.get());
  }
}
