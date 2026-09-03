/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.service.ErrorCallback;
import com.frostwire.service.ErrorService;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
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

  private static ErrorCallback swapErrorCallback(ErrorCallback next) throws Exception {
    Field field = ErrorService.class.getDeclaredField("_errorCallback");
    field.setAccessible(true);
    ErrorCallback previous = (ErrorCallback) field.get(null);
    ErrorService.setErrorCallback(next);
    return previous;
  }

  private static final class RecordingCallback implements ErrorCallback {
    private final List<Throwable> seen = new ArrayList<>();

    @Override
    public void error(Throwable t) {
      seen.add(t);
    }

    @Override
    public void error(Throwable t, String msg) {
      seen.add(t);
    }
  }
}
