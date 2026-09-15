/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Coalescing gate: at most one work item runs at a time; requests that arrive while one is running
 * are collapsed into a single follow-up run (latest-wins). Used to stop a burst of events (e.g.
 * every completed download) from flooding a bounded executor with duplicate library rescans.
 */
class CoalescingGateTest {

  @Test
  void firstRequestRunsImmediately() {
    CoalescingGate gate = new CoalescingGate();
    assertTrue(gate.begin(), "first begin must grant the run");
  }

  @Test
  void overlappingRequestsCollapseIntoOneFollowUp() {
    CoalescingGate gate = new CoalescingGate();
    assertTrue(gate.begin());
    for (int i = 0; i < 10; i++) {
      assertFalse(gate.begin(), "while running, further requests must not start a run");
    }
    assertTrue(gate.finish(), "one collapsed follow-up must be requested");
    assertFalse(gate.finish(), "the follow-up is consumed exactly once");
  }

  @Test
  void noPendingRequestMeansNoFollowUp() {
    CoalescingGate gate = new CoalescingGate();
    assertTrue(gate.begin());
    assertFalse(gate.finish(), "a lone run must not schedule a follow-up");
  }

  @Test
  void gateIsReusableAfterFinishing() {
    CoalescingGate gate = new CoalescingGate();
    assertTrue(gate.begin());
    assertFalse(gate.finish());
    assertTrue(gate.begin(), "after finishing, the gate must accept a new run");
  }

  @Test
  void libraryExplorerCoalescesMediaTypeScans() throws Exception {
    String source =
        readSource("desktop/src/main/java/com/frostwire/gui/library/LibraryExplorer.java");
    assertTrue(
        source.contains("mediaTypeSearchGate"), "LibraryExplorer must gate media-type scans");
    assertTrue(
        source.contains("requestMediaTypeSearch("),
        "media-type scans must go through the coalescing request path");
    assertFalse(
        source.contains("DesktopParallelExecutor.execute(new SearchByMediaTypeRunnable"),
        "raw media-type scans must not be submitted directly to the bounded executor");
  }

  private static String readSource(String moduleRelative) throws Exception {
    String stripped =
        moduleRelative.startsWith("desktop/")
            ? moduleRelative.substring("desktop/".length())
            : moduleRelative;
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      for (String candidate : new String[] {moduleRelative, stripped}) {
        Path file = dir.resolve(candidate);
        if (Files.exists(file)) {
          return Files.readString(file);
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("source not found: " + moduleRelative);
  }
}
