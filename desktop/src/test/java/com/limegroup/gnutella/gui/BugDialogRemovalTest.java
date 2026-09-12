/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Structural regression test: the legacy doctor-servlet bug dialogs are gone. Non-fatal errors
 * report silently through {@link CrashReportSpooler} (Icebase); nothing may reference the dead
 * {@code doctor.frostwire.com} endpoint anymore.
 */
class BugDialogRemovalTest {

  @Test
  void bugManagerHasNoReviewDialogOrServletSend() throws Exception {
    String source =
        readSource("desktop/src/main/java/com/limegroup/gnutella/gui/bugs/BugManager.java");
    assertFalse(source.contains("reviewBug"), "BugManager must not show a review dialog");
    assertFalse(source.contains("JDialog"), "BugManager must not build Swing dialogs");
    assertFalse(source.contains("sendToServlet"), "BugManager must not POST to any servlet");
    assertFalse(source.contains("ServletSender"), "BugManager must not keep a servlet sender");
    assertFalse(
        source.contains("BUG_REPORT_SERVER"), "BugManager must not reference the dead endpoint");
    assertTrue(source.contains("handleBug"), "BugManager.handleBug entry point must remain");
    assertTrue(source.contains("logBugToDisk"), "BugManager must still log locally");
  }

  @Test
  void fatalDialogDoesNotPostToDeadServlet() throws Exception {
    String source =
        readSource("desktop/src/main/java/com/limegroup/gnutella/gui/bugs/FatalBugManager.java");
    assertFalse(
        source.contains("BUG_REPORT_SERVER"),
        "FatalBugManager must not reference the dead endpoint");
    assertFalse(
        source.contains("HttpClientFactory"), "FatalBugManager must not POST anywhere directly");
    assertTrue(
        source.contains("recordSync"), "FatalBugManager must still spool the Icebase report");
    assertTrue(source.contains("System.exit"), "FatalBugManager must still exit");
  }

  @Test
  void deadlockReportsRouteToIcebaseSpooler() throws Exception {
    String source =
        readSource("desktop/src/main/java/com/limegroup/gnutella/gui/bugs/DeadlockBugManager.java");
    assertTrue(
        source.contains("CrashReportSpooler.record("),
        "DeadlockBugManager must route reports through CrashReportSpooler");
    assertFalse(
        source.contains("BUG_REPORT_SERVER"),
        "DeadlockBugManager must not reference the dead endpoint");
  }

  @Test
  void spoolerRecordIsPublicForCrossPackageCallers() throws Exception {
    String source =
        readSource("desktop/src/main/java/com/limegroup/gnutella/gui/CrashReportSpooler.java");
    assertTrue(
        source.contains("public static void record("),
        "CrashReportSpooler.record must be public (gui.bugs calls it)");
  }

  @Test
  void bugReportsOptionsPaneIsGone() throws Exception {
    assertFalse(
        sourceExists(
            "desktop/src/main/java/com/limegroup/gnutella/gui/options/panes/BugsPaneItem.java"),
        "BugsPaneItem must be deleted with the old dialog settings");
    String options =
        readSource(
            "desktop/src/main/java/com/limegroup/gnutella/gui/options/OptionsConstructor.java");
    assertFalse(options.contains("BugsPaneItem"), "Options must not register BugsPaneItem");
  }

  private static String readSource(String moduleRelative) throws Exception {
    Path file = locate(moduleRelative);
    return Files.readString(file);
  }

  private static boolean sourceExists(String moduleRelative) {
    try {
      locate(moduleRelative);
      return true;
    } catch (IllegalStateException notFound) {
      return false;
    }
  }

  private static Path locate(String moduleRelative) {
    String stripped =
        moduleRelative.startsWith("desktop/")
            ? moduleRelative.substring("desktop/".length())
            : moduleRelative;
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      for (String candidate : new String[] {moduleRelative, stripped}) {
        Path file = dir.resolve(candidate);
        if (Files.exists(file)) {
          return file;
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("source not found: " + moduleRelative);
  }
}
