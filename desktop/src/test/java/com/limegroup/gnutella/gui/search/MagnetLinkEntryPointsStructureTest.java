/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Every place that decides whether text is a magnet link uses {@code UrlUtils.isMagnetLink} or
 * {@code UrlUtils.isTorrentLink}, which read every valid magnet form. The search box (through
 * {@code FileMenuActions.openMagnetOrTorrent}), the clipboard listener and the macOS link handler
 * used to test the exact prefix {@code magnet:?xt=urn:btih}, so a magnet whose {@code dn} or {@code
 * tr} came first ran a search or was ignored (frostwire/frostwire#1513). The link forms are tested
 * in {@code UrlUtilsTorrentLinkTest}; these classes need the GUI, so this checks their source.
 */
class MagnetLinkEntryPointsStructureTest {

  private static final String[] SOURCES = {
    "src/main/java/com/limegroup/gnutella/gui/actions/FileMenuActions.java",
    "src/main/java/com/limegroup/gnutella/gui/search/MagnetClipboardListener.java",
    "src/main/java/com/limegroup/gnutella/gui/MacEventHandler.java",
    "src/main/java/com/limegroup/gnutella/ExternalControl.java",
  };

  @Test
  void magnetChecksUseTheSharedHelper() throws IOException {
    for (String path : SOURCES) {
      String code = stripped(path);
      assertFalse(
          code.contains("startsWith(\"magnet:?xt=urn:btih"),
          path + " still tests for the exact prefix magnet:?xt=urn:btih");
      assertTrue(
          code.contains("UrlUtils.isMagnetLink(") || code.contains("UrlUtils.isTorrentLink("),
          path + " must use UrlUtils.isMagnetLink or isTorrentLink");
    }
  }

  /** Source without whitespace, so formatting changes don't break the checks. */
  private static String stripped(String path) throws IOException {
    Path file = Path.of(path);
    if (!Files.isRegularFile(file)) {
      file = Path.of("desktop", path);
    }
    assertTrue(Files.isRegularFile(file), path + " not found");
    return Files.readString(file, StandardCharsets.UTF_8).replaceAll("\\s+", "");
  }
}
