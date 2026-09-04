/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IceBridgeHostCacheTest {

  @TempDir
  File tempDir;

  private IceBridgeHostCache cache(String name) {
    return new IceBridgeHostCache(new File(tempDir, name));
  }

  @Test
  void evictsAfterMaxConsecutiveFailures() {
    IceBridgeHostCache cache = cache("evict.txt");
    cache.markSuccess("10.9.9.9", 6888, "BOTH");
    for (int i = 1; i < IceBridgeHostCache.MAX_CONSECUTIVE_FAILURES; i++) {
      assertFalse(cache.markFailure("10.9.9.9", 6888));
      assertEquals(1, cache.getAll().size());
    }
    assertTrue(cache.markFailure("10.9.9.9", 6888));
    assertTrue(cache.getAll().isEmpty());
  }

  @Test
  void successResetsFailureStreak() {
    IceBridgeHostCache cache = cache("reset.txt");
    cache.markSuccess("10.9.9.9", 6888, "BOTH");
    cache.markFailure("10.9.9.9", 6888);
    cache.markFailure("10.9.9.9", 6888);
    cache.markSuccess("10.9.9.9", 6888, "BOTH");
    for (int i = 0; i < IceBridgeHostCache.MAX_CONSECUTIVE_FAILURES - 1; i++) {
      assertFalse(cache.markFailure("10.9.9.9", 6888));
    }
    assertEquals(1, cache.getAll().size());
  }

  @Test
  void failureOnUnknownHostIsNoop() {
    IceBridgeHostCache cache = cache("noop.txt");
    assertFalse(cache.markFailure("10.9.9.9", 6888));
    assertTrue(cache.getAll().isEmpty());
  }

  @Test
  void legacyThreeColumnFilesLoadWithZeroFailures() throws Exception {
    File f = new File(tempDir, "legacy.txt");
    Files.write(f.toPath(),
        "# comment\n10.9.9.9:6888,BOTH,1712345678900\n".getBytes(StandardCharsets.UTF_8));
    IceBridgeHostCache cache = new IceBridgeHostCache(f);
    List<IceBridgeHostCache.Entry> entries = cache.getAll();
    assertEquals(1, entries.size());
    assertEquals(0, entries.get(0).consecutiveFailures);
    assertEquals(1712345678900L, entries.get(0).lastSuccessfulPingMs);
  }

  @Test
  void failureStreakSurvivesSaveLoadRoundTrip() {
    IceBridgeHostCache cache = cache("roundtrip.txt");
    cache.markSuccess("10.9.9.9", 6888, "FORWARDER");
    cache.markFailure("10.9.9.9", 6888);
    cache.markFailure("10.9.9.9", 6888);
    IceBridgeHostCache reloaded =
        new IceBridgeHostCache(new File(tempDir, "roundtrip.txt"));
    List<IceBridgeHostCache.Entry> entries = reloaded.getAll();
    assertEquals(1, entries.size());
    assertEquals(2, entries.get(0).consecutiveFailures);
    assertEquals("FORWARDER", entries.get(0).role);
  }
}
