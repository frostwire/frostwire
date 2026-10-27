/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Removing a transfer from the Transfers table removes it exactly once, off the EDT. */
class BTDownloadMediatorRemoveTest {

  @Test
  void removingAShownTransferRemovesItOnceOffTheEdt() throws Exception {
    List<Thread> removedOn = new CopyOnWriteArrayList<>();
    BTDownload shown = download("shown", "aa", removedOn);
    AtomicReference<BTDownloadRowFilteredModel> modelRef = new AtomicReference<>();
    AtomicReference<Thread> edt = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () -> {
          edt.set(Thread.currentThread());
          BTDownloadRowFilteredModel model = new BTDownloadRowFilteredModel(line -> true);
          modelRef.set(model);
          model.add(shown);
          BTDownloadMediator.removeTransfer(model, shown);
        });
    drainBackgroundQueue();

    SwingUtilities.invokeAndWait(
        () -> {
          assertEquals(0, modelRef.get().getRowCount());
          assertFalse(modelRef.get().isDownloading("aa"));
        });
    assertEquals(1, removedOn.size(), "transfer removed exactly once");
    assertNotSame(edt.get(), removedOn.get(0));
  }

  @Test
  void removingAFilteredOutTransferDropsItsHiddenRowAndRemovesItOnce() throws Exception {
    List<Thread> removedOn = new CopyOnWriteArrayList<>();
    BTDownload shown = download("shown", "aa", removedOn);
    BTDownload hidden = download("hidden", "bb", removedOn);
    AtomicReference<BTDownloadRowFilteredModel> modelRef = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () -> {
          BTDownloadRowFilteredModel model =
              new BTDownloadRowFilteredModel(
                  line -> !"hidden".equals(line.getInitializeObject().getDisplayName()));
          modelRef.set(model);
          model.add(shown);
          model.add(hidden);
          assertEquals(2, model.snapshotAllDownloads().size());
          BTDownloadMediator.removeTransfer(model, hidden);
        });
    drainBackgroundQueue();

    SwingUtilities.invokeAndWait(
        () -> {
          BTDownloadRowFilteredModel model = modelRef.get();
          assertEquals(List.of(shown), model.snapshotAllDownloads());
          assertFalse(model.isDownloading("bb"));
          assertTrue(model.isDownloading("aa"));
        });
    assertEquals(1, removedOn.size(), "transfer removed exactly once");
  }

  @Test
  void droppingTheRowOfARemovedTransferDoesNotRemoveItAgain() throws Exception {
    List<Thread> removedOn = new CopyOnWriteArrayList<>();
    BTDownload shown = download("shown", "aa", removedOn);
    AtomicReference<BTDownloadRowFilteredModel> modelRef = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () -> {
          BTDownloadRowFilteredModel model = new BTDownloadRowFilteredModel(line -> true);
          modelRef.set(model);
          model.add(shown);
          model.removeLine(shown);
        });
    drainBackgroundQueue();

    SwingUtilities.invokeAndWait(() -> assertEquals(0, modelRef.get().getRowCount()));
    assertTrue(removedOn.isEmpty());
  }

  private static void drainBackgroundQueue() throws Exception {
    BackgroundQueuedExecutorService.submit(() -> null).get(5, TimeUnit.SECONDS);
  }

  private static BTDownload download(String name, String hash, List<Thread> removedOn) {
    return (BTDownload)
        Proxy.newProxyInstance(
            BTDownload.class.getClassLoader(),
            new Class<?>[] {BTDownload.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "remove" -> {
                  removedOn.add(Thread.currentThread());
                  return null;
                }
                case "getHash" -> {
                  return hash;
                }
                case "getDisplayName" -> {
                  return name;
                }
                case "equals" -> {
                  return proxy == args[0];
                }
                case "hashCode" -> {
                  return System.identityHashCode(proxy);
                }
                case "toString" -> {
                  return name;
                }
                default -> {
                  Class<?> type = method.getReturnType();
                  if (type == boolean.class) return false;
                  if (type == int.class) return 0;
                  if (type == long.class) return 0L;
                  if (type == double.class) return 0d;
                  if (type == float.class) return 0f;
                  return null;
                }
              }
            });
  }
}
