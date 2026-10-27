/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.service.ErrorCallback;
import com.limegroup.gnutella.gui.bugs.BugManager;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Forwards error messages to Icebase and the BugManager on a background thread, so the failing
 * caller (often the EDT) never does the report's disk writes or stack-trace printing itself.
 */
public final class ErrorHandler implements ErrorCallback {
  private static final Executor REPORTER =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "ErrorReporter");
            thread.setDaemon(true);
            return thread;
          });

  private final Executor reporter;
  private final Report report;

  public ErrorHandler() {
    this(REPORTER, ErrorHandler::reportNow);
  }

  ErrorHandler(Executor reporter, Report report) {
    this.reporter = reporter;
    this.report = report;
  }

  /** Displays the error to the user. */
  public void error(Throwable problem) {
    error(problem, null);
  }

  /** Displays the error to the user with a specific message. */
  public void error(Throwable problem, String msg) {
    String threadName = Thread.currentThread().getName();
    try {
      reporter.execute(() -> report.report(problem, msg, threadName));
    } catch (Throwable ignored) {
      // Reporting must never break the failing caller.
    }
  }

  private static void reportNow(Throwable problem, String msg, String threadName) {
    CrashReportSpooler.record(problem);
    GUIMediator.safeInvokeLater(
        () -> {
          try {
            GUIMediator.closeStartupDialogs();
          } catch (Throwable ignored) {
          }
        });
    try {
      BugManager.instance().handleBug(problem, threadName, msg);
    } catch (Throwable ignored) {
    }
  }

  /** Files one error report. */
  interface Report {
    void report(Throwable problem, String msg, String threadName);
  }
}
