/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.util;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.library.LibraryUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Background queues that the EDT feeds on paints, key presses and window events must keep their
 * worker across idle gaps. A queue whose worker dies after 5 idle seconds makes the next {@code
 * execute} from the EDT start an OS thread, and Thread.start can wait on the JVM and the OS for
 * seconds (#1314, frostwire/frostwire#1332).
 */
class EdtFedQueuesStayWarmTest {

  /** Longer than the 5 s idle timeout of the {@code ExecutorsHelper} pools. */
  private static final long IDLE_GAP_MS = 5_600;

  @Test
  void edtFedQueuesKeepTheirWorkerAcrossIdleGaps() throws Exception {
    Map<String, Executor> queues = new LinkedHashMap<>();
    queues.put(
        "IconLoader",
        (Executor)
            staticMethod("com.limegroup.gnutella.gui.NativeFileIconController", "newLookupQueue"));
    queues.put(
        "FileChooserDirectoryDisplayLoader",
        (Executor)
            staticField("com.frostwire.gui.theme.BaseFileChooserUI", "DIRECTORY_DISPLAY_LOADER"));
    queues.put("LibraryUtils-Executor", LibraryUtils.getExecutor());

    Map<String, Thread> before = new LinkedHashMap<>();
    for (Map.Entry<String, Executor> queue : queues.entrySet()) {
      before.put(queue.getKey(), workerOf(queue.getValue()));
    }

    Thread.sleep(IDLE_GAP_MS);

    for (Map.Entry<String, Executor> queue : queues.entrySet()) {
      Thread worker = before.get(queue.getKey());
      assertTrue(worker.isAlive(), queue.getKey() + "'s worker must survive an idle gap");
      assertSame(worker, workerOf(queue.getValue()), queue.getKey());
    }
  }

  private static Object staticMethod(String className, String name) throws Exception {
    Method method = Class.forName(className).getDeclaredMethod(name);
    method.setAccessible(true);
    return method.invoke(null);
  }

  private static Object staticField(String className, String name) throws Exception {
    Field field = Class.forName(className).getDeclaredField(name);
    field.setAccessible(true);
    return field.get(null);
  }

  private static Thread workerOf(Executor queue) throws Exception {
    CompletableFuture<Thread> worker = new CompletableFuture<>();
    queue.execute(() -> worker.complete(Thread.currentThread()));
    return worker.get(5, TimeUnit.SECONDS);
  }
}
