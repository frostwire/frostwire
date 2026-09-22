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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndexDigestTest {

  @Test
  void repeatedTokensDoNotConsumeTheDistinctTokenBudget() {
    // Regression: duplicates used to count toward MAX_TOKENS, so a holder whose names/file lists
    // repeat words would stop indexing early and become a false negative for its real keywords.
    List<String> texts = new ArrayList<>();
    for (int i = 0; i < IndexDigest.MAX_TOKENS * 2; i++) {
      texts.add("duplicate words here");
    }
    texts.add("20B Fund Margin Called");
    IndexDigest digest = IndexDigest.build(texts);
    assertTrue(digest.mightContain("20b"), "keyword after many duplicates must be indexed");
    assertTrue(digest.mightContain("margin"));
    assertTrue(digest.mightContain("duplicate"));
  }

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
    assertNull(IndexDigest.fromBytes(new byte[IndexDigest.MIN_BYTES]));
    assertNull(IndexDigest.fromBytes(new byte[IndexDigest.MAX_BYTES + 2]));
    byte[] versioned = new byte[1 + IndexDigest.MIN_BYTES];
    versioned[0] = (byte) IndexDigest.VERSION;
    assertNotNull(IndexDigest.fromBytes(versioned));
  }

  @Test
  void stopwordsAreNotIndexedAndDoNotCountAsHits() {
    IndexDigest digest = IndexDigest.build(List.of("the beatles live mp4"));
    assertFalse(digest.mightContain("the"));
    assertFalse(digest.mightContain("mp4"));
    assertTrue(digest.mightContain("beatles"));
    assertTrue(digest.routes(List.of("beatles")));
    assertFalse(digest.routes(List.of("the")));
  }

  @Test
  void twoThirdsOfALongQueryMustHit() {
    IndexDigest digest = IndexDigest.build(List.of("alpha beta gamma"));
    assertTrue(digest.routes(List.of("alpha", "beta")));
    assertFalse(digest.routes(List.of("alpha", "missing", "absent")));
    assertTrue(digest.routes(List.of("alpha", "beta", "missing")));
    assertEquals(2, IndexDigest.requiredHits(3));
    assertEquals(3, IndexDigest.requiredHits(4));
  }

  @Test
  void clusterOrPreservesALeafToken() {
    IndexDigest leaf = IndexDigest.build(List.of("beatles anthology"));
    IndexDigest cluster = IndexDigest.aggregate(List.of(leaf));
    assertEquals(IndexDigest.CLUSTER_BYTES, cluster.byteLength());
    assertTrue(cluster.routes(List.of("beatles")));
    assertFalse(cluster.routes(List.of("zzzznotpresent")));
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
  void largeInputIsBounded() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20_000; i++) {
      sb.append("token").append(i).append(" /path/to/file").append(i).append(".mp4\n");
    }
    String huge = sb.toString();
    assertTrue(IndexDigest.tokenize(huge).size() <= IndexDigest.MAX_TOKENS);
    IndexDigest digest = IndexDigest.build(List.of(huge));
    assertEquals(IndexDigest.DEFAULT_BYTES, digest.byteLength());
    assertTrue(digest.population() > 0);
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
