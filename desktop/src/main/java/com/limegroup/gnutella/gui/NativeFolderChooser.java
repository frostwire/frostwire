/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.util.WarmExecutors;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Picks a folder with Windows' own folder dialog instead of a JFileChooser.
 *
 * <p>On Windows a JFileChooser asks the shell about every folder it shows, over COM, on the EDT: a
 * slow, network or removable drive froze FrostWire until the shell answered (Icebase #45ae6d65f303,
 * #fbbcd419bafa, frostwire/frostwire#1564). AWT's {@code FileDialog} can't pick folders on Windows,
 * so the native dialog ({@link WindowsFolderPicker}) is shown on its own thread, owned by the app's
 * window, while the EDT keeps dispatching events in a secondary loop; the caller still gets the
 * folder back synchronously.
 *
 * <p>When the native dialog can't be used (JNA or its native library missing, a COM error), {@link
 * #choose} logs why once and returns null from then on, and the caller shows its JFileChooser.
 */
final class NativeFolderChooser {
  private static final Logger LOG = Logger.getLogger(NativeFolderChooser.class);

  /** Shows the operating system's folder dialog. */
  interface Picker {
    /**
     * Reads what the dialog needs from {@code owner} (null for none) on the calling thread, the
     * EDT, and returns the dialog. {@link #choose} calls it on the picker's own thread: it returns
     * the chosen folder, or null when the user cancels, and throws when the dialog can't be shown.
     */
    Callable<File> prepare(Window owner, String title, File initialDirectory) throws Exception;
  }

  /** A dialog that was shown: the chosen folder, or null when the user cancelled. */
  record Choice(File folder) {}

  /** The worker the dialogs run on, started by the first Windows folder pick. */
  private static final class Worker {
    static final Executor EXECUTOR = WarmExecutors.serialQueue("NativeFolderPicker");
  }

  private static final NativeFolderChooser INSTANCE =
      new NativeFolderChooser(
          OSUtils::isWindows,
          // A lambda, not WindowsFolderPicker::new: the class (and JNA, which isn't shipped
          // outside Windows) is only loaded when the first Windows folder pick calls it.
          () -> new WindowsFolderPicker(),
          () -> Worker.EXECUTOR,
          GUIMediator::getAppFrame);

  private final BooleanSupplier isWindows;
  private final Supplier<Picker> pickerFactory;
  private final Supplier<Executor> executor;
  private final Supplier<Window> appFrame;

  private volatile Picker picker;

  /** Set once the native dialog failed: every later pick uses the JFileChooser. */
  private volatile boolean unavailable;

  NativeFolderChooser(
      BooleanSupplier isWindows,
      Supplier<Picker> pickerFactory,
      Supplier<Executor> executor,
      Supplier<Window> appFrame) {
    this.isWindows = isWindows;
    this.pickerFactory = pickerFactory;
    this.executor = executor;
    this.appFrame = appFrame;
  }

  static NativeFolderChooser instance() {
    return INSTANCE;
  }

  /**
   * Shows the native folder dialog, owned by {@code parent}'s window (the app's window when it has
   * none), and waits for it without blocking the EDT. Returns null when there is no native dialog
   * (not Windows, or it failed): the caller then shows its own chooser.
   */
  Choice choose(Component parent, String title, File initialDirectory) {
    if (unavailable || !isWindows.getAsBoolean()) {
      return null;
    }
    try {
      Picker p = picker;
      if (p == null) {
        // Loading JNA unpacks and loads its native library: disk work, kept off the EDT too.
        p = callOffEdt(pickerFactory::get, executor.get());
        picker = p;
      }
      Callable<File> dialog = p.prepare(ownerOf(parent), title, initialDirectory);
      return new Choice(callOffEdt(dialog, executor.get()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new Choice(null);
    } catch (Exception | LinkageError e) {
      unavailable = true;
      LOG.warn("Native folder dialog unavailable, using JFileChooser instead: " + e, e);
      return null;
    }
  }

  private Window ownerOf(Component parent) {
    Window window =
        parent instanceof Window w
            ? w
            : parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
    return window != null ? window : appFrame.get();
  }

  /**
   * Runs {@code task} on {@code executor} and returns its result. On the EDT the wait is a
   * secondary loop, so the EDT keeps dispatching events until the task is done.
   */
  static <T> T callOffEdt(Callable<T> task, Executor executor) throws Exception {
    CompletableFuture<T> result = new CompletableFuture<>();
    SecondaryLoop loop =
        EventQueue.isDispatchThread()
            ? Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop()
            : null;
    executor.execute(
        () -> {
          try {
            result.complete(task.call());
          } catch (Throwable t) {
            result.completeExceptionally(t);
          } finally {
            // exit() before enter() makes enter() return at once.
            if (loop != null) {
              loop.exit();
            }
          }
        });
    if (loop != null) {
      // The event that asked for the dialog is done with the EDT, which now only dispatches
      // events; dispatching one right away shows the stall watchdog that, even before the dialog
      // takes the focus from the owner and its window events arrive.
      EventQueue.invokeLater(() -> {});
      loop.enter();
    }
    try {
      return result.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception ex) {
        throw ex;
      }
      if (cause instanceof Error err) {
        throw err;
      }
      throw e;
    }
  }
}
