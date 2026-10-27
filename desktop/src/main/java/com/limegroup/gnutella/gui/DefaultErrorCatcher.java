/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.service.ErrorService;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Catches every uncaught exception, including those thrown by listeners on the AWT event dispatch
 * thread, and forwards the app's own failures to {@link ErrorService} (and so to Icebase).
 */
class DefaultErrorCatcher implements Thread.UncaughtExceptionHandler {
  /**
   * Installs the catcher as the JVM-wide default uncaught exception handler. The EDT has no handler
   * of its own, so {@code EventDispatchThread.processException} reaches this through its {@link
   * ThreadGroup}. The legacy {@code sun.awt.exception.handler} property is no longer read by the
   * JDK.
   */
  static void install() {
    Thread.setDefaultUncaughtExceptionHandler(new DefaultErrorCatcher());
  }

  /**
   * Never prints on the EDT: on a console-less javaw the stderr write can block event dispatch for
   * seconds. Other threads keep the JDK's stderr trace for anything not reported.
   */
  @Override
  public void uncaughtException(Thread thread, Throwable bug) {
    if (report(bug) || EventQueue.isDispatchThread()) {
      return;
    }
    try {
      System.err.print("Exception in thread \"" + thread.getName() + "\" ");
      bug.printStackTrace(System.err);
    } catch (Throwable ignored) {
      // Nothing left to do with it.
    }
  }

  /**
   * Trivial exceptions are dropped; everything else is forwarded to the ErrorService, which queues
   * a silent Icebase report off the calling thread.
   */
  public void handle(Throwable bug) {
    report(bug);
  }

  /** Returns true if the bug was forwarded to the ErrorService. */
  private boolean report(Throwable bug) {
    try {
      if (bug == null || isIgnorable(bug, stackTraceToString(bug))) {
        return false;
      }
      ErrorService.error(bug);
      return true;
    } catch (Throwable ignored) {
      // Reporting must never break event dispatch.
      return true;
    }
  }

  private static String stackTraceToString(Throwable bug) {
    StringWriter out = new StringWriter();
    bug.printStackTrace(new PrintWriter(out));
    return out.toString();
  }

  /** Determines if the message can be ignored. */
  private boolean isIgnorable(Throwable bug, String msg) {
    // ignore all overflows & out of memory errors,
    // since they'll give us absolutely no debugging information
    if (bug instanceof StackOverflowError) return true;
    if (bug instanceof OutOfMemoryError) return true;
    // no bug?  kinda impossible, but shouldn't report.
    if (msg == null) return true;
    // frickin' repaint manager stinks.
    if (msg.contains("javax.swing.RepaintManager")) return true;
    if (msg.contains("sun.awt.RepaintArea.paint")) return true;
    // display manager on OSX goes out of whack
    if (bug instanceof ArrayIndexOutOfBoundsException) {
      if (msg.contains("apple.awt.CWindow.displayChanged")) return true;
      if (msg.contains("javax.swing.plaf.basic.BasicTabbedPaneUI.getTabBounds")) return true;
    }
    // system clipboard can be held, preventing us from getting.
    // throws a RuntimeException through stuff we don't control...
    if (bug instanceof IllegalStateException) {
      if (msg.contains("cannot open system clipboard")) return true;
    }
    // odd component exception
    if (bug instanceof IllegalComponentStateException) {
      if (msg.contains("component must be showing on the screen to determine its location"))
        return true;
    }
    // various NPEs we can ignore:
    if (bug instanceof NullPointerException) {
      if (msg.contains("MetalFileChooserUI")) return true;
      if (msg.contains("WindowsFileChooserUI")) return true;
      if (msg.contains("AquaDirectoryModel")) return true;
      if (msg.contains("SizeRequirements.calculateAlignedPositions")) return true;
      if (msg.contains("BasicTextUI.damageRange")) return true;
      if (msg.contains("null pData")) return true;
      if (msg.contains("disposed component")) return true;
      if (msg.contains("javax.swing.JComponent.repaint")
          && msg.contains("com.limegroup.gnutella.gui.FileChooserHandler.getSaveAsFile")) {
        return true;
      }
      if (msg.contains("javax.swing.JComponent.repaint")
          && msg.contains("com.limegroup.gnutella.gui.FileChooserHandler.getInput")) {
        return true;
      }
    }
    if (bug instanceof IndexOutOfBoundsException) {
      if (msg.contains("Invalid index")
          && msg.contains("com.limegroup.gnutella.gui.FileChooserHandler.getSaveAsFile")) {
        return true;
      }
      if (msg.contains("Invalid index")
          && msg.contains("com.limegroup.gnutella.gui.FileChooserHandler.getInput")) {
        return true;
      }
    }
    // various InternalErrors we can ignore.
    if (bug instanceof InternalError) {
      if (msg.contains("getGraphics not implemented for this component")) return true;
    }
    // if we're not somewhere in the bug, ignore it.
    // no need for us to debug sun's internal errors.
    if (!msg.contains("com.limegroup.gnutella") && !msg.contains("org.limewire")) return true;
    // we intercept calls in various places -- check if the only
    // com.limegroup.gnutella is from an intercepted call.
    if (intercepts(msg, "com.limegroup.gnutella.tables.MouseEventConsumptionChecker")) return true;
    return intercepts(msg, "com.limegroup.gnutella.gui.tables.LimeJTable.processMouseEvent");
  }

  /** Determines if the given string is the only place where 'com.limegroup.gnutella' exists. */
  private boolean intercepts(String msg, String inter) {
    int i = msg.indexOf(inter);
    // not intercepted at all?
    if (i == -1) return false;
    // something before it?
    if (msg.lastIndexOf("com.limegroup.gnutella", i) != -1
        && msg.lastIndexOf("org.limewire", i) != -1) return false;
    i += inter.length();
    if (i >= msg.length()) return false;
    // something after it?
    return msg.indexOf("com.limegroup.gnutella", i) == -1
        || msg.lastIndexOf("org.limewire", i) == -1;
    // yup, it's the only com.limegroup.gnutella in there.
  }
}
