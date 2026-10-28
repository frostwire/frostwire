/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.service.ErrorCallback;
import com.frostwire.service.ErrorService;
import java.awt.EventQueue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DefaultErrorCatcherTest {
  @Test
  void uncaughtAppExceptionReachesErrorService() throws Exception {
    RecordingCallback callback = new RecordingCallback();
    ErrorCallback previous = swapErrorCallback(callback);
    try {
      new DefaultErrorCatcher().handle(new RuntimeException("error-catcher probe"));
      assertEquals(1, callback.seen.size());
      assertEquals("error-catcher probe", callback.seen.get(0).getMessage());
    } finally {
      ErrorService.setErrorCallback(previous);
    }
  }

  @Test
  void installedCatcherReceivesUncaughtEdtExceptionsWithoutPrinting() throws Exception {
    RecordingCallback callback = new RecordingCallback();
    ErrorCallback previous = swapErrorCallback(callback);
    Thread.UncaughtExceptionHandler previousDefault = Thread.getDefaultUncaughtExceptionHandler();
    PrintStream previousErr = System.err;
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    try {
      DefaultErrorCatcher.install();
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      EventQueue.invokeLater(
          () -> {
            throw new IllegalStateException("edt uncaught probe");
          });
      assertTrue(callback.received.await(10, TimeUnit.SECONDS), "handler never saw the bug");
      // A later event runs on the replacement EDT once the failed one has been fully processed.
      EventQueue.invokeAndWait(() -> {});
      assertEquals("edt uncaught probe", callback.seen.get(0).getMessage());
      assertFalse(
          err.toString(StandardCharsets.UTF_8).contains("edt uncaught probe"),
          "uncaught EDT exception was printed through ThreadGroup.uncaughtException");
    } finally {
      System.setErr(previousErr);
      Thread.setDefaultUncaughtExceptionHandler(previousDefault);
      ErrorService.setErrorCallback(previous);
    }
  }

  @Test
  void trivialExceptionsAreDroppedSilently() throws Exception {
    RecordingCallback callback = new RecordingCallback();
    ErrorCallback previous = swapErrorCallback(callback);
    try {
      assertDoesNotThrow(() -> new DefaultErrorCatcher().handle(null));
      new DefaultErrorCatcher().handle(new IllegalStateException("cannot open system clipboard"));
      new DefaultErrorCatcher().handle(new StackOverflowError("overflow"));
      new DefaultErrorCatcher().handle(new OutOfMemoryError("oom"));
      assertEquals(0, callback.seen.size());
    } finally {
      ErrorService.setErrorCallback(previous);
    }
  }

  @Test
  void exceptionsInFrostWireUiCodeAreReported() throws Exception {
    RecordingCallback callback = new RecordingCallback();
    ErrorCallback previous = swapErrorCallback(callback);
    try {
      IllegalStateException bug = new IllegalStateException("frostwire ui probe");
      bug.setStackTrace(
          edtStack(frame("com.frostwire.gui.library.LibraryFilesTableMediator", "updateRow")));
      new DefaultErrorCatcher().handle(bug);
      RuntimeException wrapper = new RuntimeException("wrapper", bug);
      wrapper.setStackTrace(edtStack());
      new DefaultErrorCatcher().handle(wrapper);
      assertEquals(List.of(bug, wrapper), callback.seen);
    } finally {
      ErrorService.setErrorCallback(previous);
    }
  }

  @Test
  void exceptionsThatOnlyPassThroughAppFramesAreDropped() throws Exception {
    RecordingCallback callback = new RecordingCallback();
    ErrorCallback previous = swapErrorCallback(callback);
    try {
      IllegalStateException jdkOnly = new IllegalStateException("jdk probe");
      jdkOnly.setStackTrace(edtStack());
      new DefaultErrorCatcher().handle(jdkOnly);
      IllegalStateException tableMouse = new IllegalStateException("table mouse probe");
      tableMouse.setStackTrace(
          edtStack(frame("com.limegroup.gnutella.gui.tables.LimeJTable", "processMouseEvent")));
      new DefaultErrorCatcher().handle(tableMouse);
      assertEquals(0, callback.seen.size());
    } finally {
      ErrorService.setErrorCallback(previous);
    }
  }

  /** A Swing call failing inside the given app frames, dispatched through StrictEdtMode's queue. */
  private static StackTraceElement[] edtStack(StackTraceElement... appFrames) {
    List<StackTraceElement> stack = new ArrayList<>();
    stack.add(frame("javax.swing.JTable", "getValueAt"));
    stack.addAll(List.of(appFrames));
    stack.add(frame("java.awt.EventQueue", "dispatchEvent"));
    stack.add(
        frame("com.frostwire.util.StrictEdtMode$ProductionTimingEventQueue", "dispatchEvent"));
    stack.add(frame("java.awt.EventDispatchThread", "run"));
    return stack.toArray(StackTraceElement[]::new);
  }

  private static StackTraceElement frame(String className, String method) {
    return new StackTraceElement(className, method, null, 1);
  }

  private static ErrorCallback swapErrorCallback(ErrorCallback next) throws Exception {
    Field field = ErrorService.class.getDeclaredField("_errorCallback");
    field.setAccessible(true);
    ErrorCallback previous = (ErrorCallback) field.get(null);
    ErrorService.setErrorCallback(next);
    return previous;
  }

  private static final class RecordingCallback implements ErrorCallback {
    private final List<Throwable> seen = Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch received = new CountDownLatch(1);

    @Override
    public void error(Throwable t) {
      seen.add(t);
      received.countDown();
    }

    @Override
    public void error(Throwable t, String msg) {
      error(t);
    }
  }
}
