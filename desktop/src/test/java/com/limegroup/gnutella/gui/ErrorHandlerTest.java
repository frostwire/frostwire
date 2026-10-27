/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ErrorHandlerTest {
  @Test
  void errorsRaisedOnTheEdtAreReportedOffTheEdt() throws Exception {
    ExecutorService reporter = Executors.newSingleThreadExecutor();
    try {
      AtomicBoolean reportedOnEdt = new AtomicBoolean(true);
      AtomicReference<Throwable> reported = new AtomicReference<>();
      AtomicReference<String> reportedMsg = new AtomicReference<>();
      AtomicReference<String> reportedThreadName = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      ErrorHandler handler =
          new ErrorHandler(
              reporter,
              (problem, msg, threadName) -> {
                reportedOnEdt.set(EventQueue.isDispatchThread());
                reported.set(problem);
                reportedMsg.set(msg);
                reportedThreadName.set(threadName);
                done.countDown();
              });
      RuntimeException problem = new RuntimeException("error-handler probe");
      AtomicReference<String> edtName = new AtomicReference<>();
      EventQueue.invokeAndWait(
          () -> {
            edtName.set(Thread.currentThread().getName());
            handler.error(problem, "detail");
          });
      assertTrue(done.await(10, TimeUnit.SECONDS), "error was never reported");
      assertFalse(reportedOnEdt.get(), "error report ran on the EDT");
      assertSame(problem, reported.get());
      assertEquals("detail", reportedMsg.get());
      assertEquals(edtName.get(), reportedThreadName.get());
    } finally {
      reporter.shutdownNow();
    }
  }
}
