/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/**
 * The search box and the Transfers "add URL" box decide whether pasted text is a torrent link
 * through {@code UrlUtils.isTorrentLink}, which reads every valid magnet form. They used to test
 * the exact prefix {@code magnet:?xt=urn:btih:}, so a magnet whose {@code dn} or {@code tr} came
 * first ran a Distributed search in the search box and was dropped in the Transfers box
 * (frostwire/frostwire#1513). The link forms themselves are tested in common's {@code
 * UrlUtilsTorrentLinkTest}.
 */
public final class MagnetTextEntryStructureTest {

  private static final String[] SOURCES = {
    "src/main/java/com/frostwire/android/gui/fragments/SearchFragment.java",
    "src/main/java/com/frostwire/android/gui/fragments/TransfersFragment.java",
  };

  @Test
  public void textEntryUsesTheSharedTorrentLinkCheck() throws IOException {
    for (String path : SOURCES) {
      String code = stripped(path);
      assertTrue(
          path + " must call UrlUtils.isTorrentLink", code.contains("UrlUtils.isTorrentLink("));
      assertTrue(
          path + " must hand the download a normalized link",
          code.contains("UrlUtils.normalizeTorrentLink("));
    }
  }

  @Test
  public void textEntryDoesNotTestForOneMagnetPrefix() throws IOException {
    for (String path : SOURCES) {
      String code = stripped(path);
      assertFalse(
          path + " still tests for the exact prefix magnet:?xt=urn:btih:",
          code.contains("startsWith(\"magnet:?xt=urn:btih:\")"));
    }
  }

  /** Source without whitespace, so formatting changes don't break the checks. */
  private static String stripped(String path) throws IOException {
    File file = new File(path);
    if (!file.isFile()) {
      file = new File("android", path);
    }
    assertTrue(path + " not found", file.isFile());
    return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
        .replaceAll("\\s+", "");
  }
}
