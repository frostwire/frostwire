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
import java.util.List;

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
    // Overflows give us no debugging information. OutOfMemoryErrors go on, and are reported like
    // anything else when an app frame is on their stack: it shows where the allocation failed.
    if (bug instanceof StackOverflowError) return true;
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
    // Exceptions only in JDK code are not ours to debug.
    return !hasAppFrame(bug);
  }

  /** FrostWire's own code, including the jlibtorrent bindings. */
  private static final List<String> APP_PACKAGES =
      List.of("com.limegroup.", "org.limewire.", "com.frostwire.");

  /**
   * App frames that events only pass through: the EDT's event queue is on every EDT stack, and
   * LimeJTable hands every table mouse event to Swing. On their own they don't make an exception
   * ours.
   */
  private static final List<String> PASS_THROUGH_CLASS_PREFIXES =
      List.of("com.frostwire.util.StrictEdtMode");

  private static final String PASS_THROUGH_TABLE_CLASS =
      "com.limegroup.gnutella.gui.tables.LimeJTable";

  private static final String PASS_THROUGH_TABLE_METHOD = "processMouseEvent";

  /** Bounds the walk down a cause chain, which may loop. */
  private static final int MAX_CAUSES = 16;

  /**
   * True if the bug or one of its causes has a frame of FrostWire code that isn't a pass-through.
   */
  static boolean hasAppFrame(Throwable bug) {
    Throwable current = bug;
    for (int depth = 0; current != null && depth < MAX_CAUSES; depth++) {
      for (StackTraceElement frame : current.getStackTrace()) {
        if (isAppFrame(frame)) {
          return true;
        }
      }
      current = current.getCause() == current ? null : current.getCause();
    }
    return false;
  }

  private static boolean isAppFrame(StackTraceElement frame) {
    String className = frame.getClassName();
    if (APP_PACKAGES.stream().noneMatch(className::startsWith)) {
      return false;
    }
    if (PASS_THROUGH_CLASS_PREFIXES.stream().anyMatch(className::startsWith)) {
      return false;
    }
    return !(className.equals(PASS_THROUGH_TABLE_CLASS)
        && frame.getMethodName().equals(PASS_THROUGH_TABLE_METHOD));
  }
}
