/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.util.Launcher;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Opening a web page or a folder asks the OS shell to do it (Desktop.browse, explorer, osascript)
 * and can block for seconds. Icebase build 333 caught the EDT stuck in Launcher.openURL,
 * Launcher.launchExplorer (getCanonicalPath, FWProcess.exec) and ExploreAction's File.exists. Every
 * Launcher entry point must return to the EDT at once and do its file system and shell work, and
 * report its failure, on a pool thread.
 */
class LauncherOffEdtTest {

  @Test
  void openURLCalledOnTheEdtBrowsesOnAPoolThread() throws Exception {
    CompletableFuture<Thread> failureThread = new CompletableFuture<>();
    AtomicReference<Exception> failure = new AtomicReference<>();

    // An unparsable url (or a headless Desktop) fails inside the browse call, which tells us the
    // thread the browse call ran on without opening a real browser.
    SwingUtilities.invokeAndWait(
        () ->
            Launcher.openURL(
                "not a uri ::",
                e -> {
                  failure.set(e);
                  failureThread.complete(Thread.currentThread());
                }));

    Thread thread = failureThread.get(5, TimeUnit.SECONDS);
    assertNotNull(failure.get());
    assertFalse(isEdt(thread), "Desktop.browse ran on the EDT: " + thread.getName());
  }

  @Test
  void launchExplorerCalledOnTheEdtPicksTheFileOnAPoolThread() throws Exception {
    CompletableFuture<Thread> targetThread = new CompletableFuture<>();

    SwingUtilities.invokeAndWait(
        () ->
            Launcher.launchExplorer(
                () -> {
                  targetThread.complete(Thread.currentThread());
                  return null; // nothing to show, so no file manager is started
                },
                e -> targetThread.completeExceptionally(e)));

    Thread thread = targetThread.get(5, TimeUnit.SECONDS);
    assertFalse(isEdt(thread), "the file existence check ran on the EDT: " + thread.getName());
  }

  @Test
  void launchExplorerOnAMissingFileStartsNothingAndReportsNothing() throws Exception {
    File missing = Files.createTempFile("frostwire-explore-", ".txt").toFile();
    assertTrue(missing.delete());
    CompletableFuture<Exception> failure = new CompletableFuture<>();

    SwingUtilities.invokeAndWait(() -> Launcher.launchExplorer(missing, failure::complete));
    Thread.sleep(500); // the launch runs asynchronously; give it time to report a failure

    assertFalse(failure.isDone(), "a missing file must be skipped, not reported");
  }

  @Test
  void launchFileCalledOnTheEdtChecksTheExtensionOnAPoolThread() throws Exception {
    File forbidden = Files.createTempFile("frostwire-launch-", ".exe").toFile();
    forbidden.deleteOnExit();
    CompletableFuture<Thread> failureThread = new CompletableFuture<>();
    AtomicReference<Exception> failure = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () ->
            Launcher.launchFile(
                forbidden,
                e -> {
                  failure.set(e);
                  failureThread.complete(Thread.currentThread());
                }));

    Thread thread = failureThread.get(5, TimeUnit.SECONDS);
    assertInstanceOf(SecurityException.class, failure.get());
    assertFalse(isEdt(thread), "the extension check ran on the EDT: " + thread.getName());
    assertTrue(forbidden.delete());
  }

  private static boolean isEdt(Thread thread) {
    return thread.getName().startsWith("AWT-EventQueue");
  }
}
