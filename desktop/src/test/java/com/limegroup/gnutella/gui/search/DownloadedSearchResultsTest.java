/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DownloadedSearchResultsTest {

  private static final Executor DIRECT = Runnable::run;

  @TempDir Path dir;

  private DownloadedSearchResults open() {
    return new DownloadedSearchResults(dir.resolve("downloaded.bloom").toFile(), DIRECT);
  }

  @Test
  void addedResultsAreMarkedAndOthersAreNot() {
    DownloadedSearchResults results = open();
    for (int i = 0; i < 1000; i++) {
      results.add("hash:downloaded-" + i);
    }
    for (int i = 0; i < 1000; i++) {
      assertTrue(results.mightContain("hash:downloaded-" + i));
    }
    for (int i = 0; i < 100_000; i++) {
      assertFalse(results.mightContain("hash:never-downloaded-" + i));
    }
  }

  @Test
  void marksSurviveARestart() {
    open().add("url:https://archive.org/details/foo");
    assertTrue(open().mightContain("url:https://archive.org/details/foo"));
    assertFalse(open().mightContain("url:https://archive.org/details/bar"));
  }

  /** Privacy: the file is a fixed-size bit array, not a readable list of downloads. */
  @Test
  void savedFileHasAFixedSizeAndNoNames() throws Exception {
    DownloadedSearchResults results = open();
    String key = "url:https://example.com/a-very-recognizable-name";
    results.add(key);
    File file = dir.resolve("downloaded.bloom").toFile();
    byte[] bytes = Files.readAllBytes(file.toPath());
    assertEquals(DownloadedSearchResults.BYTES, bytes.length);
    assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("recognizable"));
    for (int i = 0; i < 5000; i++) {
      results.add("hash:" + i);
    }
    assertEquals(DownloadedSearchResults.BYTES, file.length());
  }

  @Test
  void aFileOfTheWrongSizeIsIgnored() throws Exception {
    Files.write(dir.resolve("downloaded.bloom"), new byte[] {-1, -1, -1, -1});
    assertFalse(open().mightContain("hash:anything"));
  }

  @Test
  void torrentsAreKeyedByInfoHashOtherResultsByDetailsUrl() {
    assertEquals("hash:abcdef", DownloadedSearchResults.keyOf(result("ABCDEF", "https://x/1")));
    assertEquals("url:https://x/2", DownloadedSearchResults.keyOf(result(null, "https://x/2")));
    assertEquals("url:https://x/3", DownloadedSearchResults.keyOf(result("", "https://x/3")));
    assertNull(DownloadedSearchResults.keyOf(result(null, null)));
  }

  @Test
  void resultsWithoutAKeyAreNeverMarked() {
    DownloadedSearchResults results = open();
    results.add(null);
    assertFalse(results.mightContain(null));
  }

  private static UISearchResult result(String hash, String detailsUrl) {
    return (UISearchResult)
        Proxy.newProxyInstance(
            UISearchResult.class.getClassLoader(),
            new Class<?>[] {UISearchResult.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getHash":
                  return hash;
                case "getDetailsUrl":
                  return detailsUrl;
                default:
                  throw new UnsupportedOperationException(method.getName());
              }
            });
  }
}
