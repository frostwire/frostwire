/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IndexDigestTest {

  @Test
  void tokenizesAndNormalizesDiacritics() {
    List<String> tokens = IndexDigest.tokenize("Inglés En Miami (audio).webm");
    assertTrue(tokens.contains("ingles"));
    assertTrue(tokens.contains("miami"));
    assertTrue(tokens.contains("audio"));
    assertFalse(tokens.contains("en"));
  }

  @Test
  void neverMissesAnIndexedToken() {
    IndexDigest digest = IndexDigest.build(List.of("Inglés En Miami (audio).webm"));
    assertTrue(digest.mightContain("miami"));
    assertTrue(digest.mightContain("MIAMI"));
    assertTrue(digest.mightContain("Inglés"));
    assertTrue(digest.mightContainAll(List.of("miami", "audio")));
    assertEquals(2, digest.matchCount(List.of("miami", "audio", "unrelatedtoken")));
  }

  @Test
  void matchesTokenSpanningNameAndFilePath() {
    IndexDigest digest =
        IndexDigest.build(List.of("Some Album", "[/downloads/miami/live-set.flac]"));
    assertTrue(digest.mightContain("miami"));
  }

  @Test
  void rejectsMalformedFrames() {
    assertNull(IndexDigest.fromBytes(null));
    assertNull(IndexDigest.fromBytes(new byte[IndexDigest.MIN_BYTES - 1]));
    assertNull(IndexDigest.fromBytes(new byte[IndexDigest.MAX_BYTES + 1]));
    assertNotNull(IndexDigest.fromBytes(new byte[IndexDigest.MIN_BYTES]));
  }

  @Test
  void roundTripsBytes() {
    IndexDigest digest = IndexDigest.build(List.of("miami"));
    IndexDigest parsed = IndexDigest.fromBytes(digest.toBytes());
    assertNotNull(parsed);
    assertTrue(parsed.mightContain("miami"));
    assertEquals(digest.byteLength(), parsed.byteLength());
  }

  @Test
  void boundedSizeAndLowFalsePositiveRate() {
    IndexDigest digest = IndexDigest.build(List.of("miami"));
    assertEquals(IndexDigest.DEFAULT_BYTES, digest.byteLength());
    int falsePositives = 0;
    for (int i = 0; i < 500; i++) {
      if (digest.mightContain("zzz" + i)) {
        falsePositives++;
      }
    }
    assertTrue(falsePositives < 25, "bloom false positives too high: " + falsePositives);
  }
}
