/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RemoteSearchResponseStreamTest {

  @Test
  void collectorRequiresContiguousChunksAndDeduplicates() {
    RemoteSearchResponse.Collector collector = new RemoteSearchResponse.Collector(3, 2048);
    RemoteSearchResponse last = chunk(2, true, "last");
    assertTrue(collector.add(last, 200));
    assertTrue(collector.add(last, 200));
    assertEquals(200, collector.byteCount());
    assertFalse(collector.isComplete());
    assertTrue(collector.responses().isEmpty());
    assertTrue(collector.add(chunk(0, false, "first"), 200));
    assertFalse(collector.isComplete());
    assertTrue(collector.add(chunk(1, false, "middle"), 200));
    assertTrue(collector.isComplete());
    assertEquals(0, collector.responses().get(0).chunkIndex());
    assertEquals(3, collector.rowCount());
  }

  @Test
  void collectorRejectsConflictsAndRowByteOverflow() {
    RemoteSearchResponse.Collector conflict = new RemoteSearchResponse.Collector(3, 2048);
    conflict.add(chunk(0, false, "first"), 200);
    assertFalse(conflict.add(chunk(0, false, "different"), 200));
    assertTrue(conflict.isFailed());
    assertFalse(conflict.add(chunk(1, true, "last"), 200));
    assertTrue(conflict.responses().isEmpty());

    RemoteSearchResponse.Collector rows = new RemoteSearchResponse.Collector(1, 2048);
    rows.add(chunk(0, false, "first"), 200);
    assertFalse(rows.add(chunk(1, true, "last"), 200));
    assertEquals(1, rows.rowCount());

    RemoteSearchResponse.Collector bytes = new RemoteSearchResponse.Collector(3, 300);
    bytes.add(chunk(0, false, "first"), 200);
    assertFalse(bytes.add(chunk(1, true, "last"), 200));
    assertEquals(200, bytes.byteCount());

    RemoteSearchResponse.Collector finals = new RemoteSearchResponse.Collector(3, 2048);
    finals.add(chunk(2, true, "last"), 200);
    assertFalse(finals.add(chunk(1, true, "earlier final"), 200));
  }

  @Test
  void rejectsUnboundedAndNonIntegralStreamIndices() {
    RemoteSearchResponse response = chunk(0, true, "single");
    for (Object index :
        new Object[] {-1, RemoteSearchResponse.MAX_STREAM_CHUNKS, Long.MAX_VALUE, 0.5, "0"}) {
      Map<String, Object> map = response.toBencodeableMap();
      map.put("chunk", index);
      org.junit.jupiter.api.Assertions.assertNull(RemoteSearchResponse.fromBencodeableMap(map));
    }
    Map<String, Object> map = response.toBencodeableMap();
    map.put("rows", "not-a-row-list");
    org.junit.jupiter.api.Assertions.assertNull(RemoteSearchResponse.fromBencodeableMap(map));
  }

  @Test
  void contiguousEmptyChunksStillRespectChunkBudget() {
    RemoteSearchResponse.Collector collector = new RemoteSearchResponse.Collector(1, 2048);
    for (int i = 0; i < RemoteSearchResponse.MAX_STREAM_CHUNKS; i++) {
      assertTrue(
          collector.add(
              RemoteSearchResponse.builder()
                  .nonce(new byte[32])
                  .chunkIndex(i)
                  .finalChunk(i == RemoteSearchResponse.MAX_STREAM_CHUNKS - 1)
                  .signature(new byte[64])
                  .build(),
              1));
    }
    assertTrue(collector.isComplete());
    assertEquals(RemoteSearchResponse.MAX_STREAM_CHUNKS, collector.responses().size());
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () -> chunk(RemoteSearchResponse.MAX_STREAM_CHUNKS, true, "over budget"));
  }

  private static RemoteSearchResponse chunk(int index, boolean last, String name) {
    return RemoteSearchResponse.builder()
        .nonce(new byte[32])
        .timestamp(1)
        .chunkIndex(index)
        .finalChunk(last)
        .addRow(new byte[20], name, 1, 1, new byte[32])
        .signature(new byte[64])
        .build();
  }

  @Test
  void singleFrameFinalDefaultsPreservedInMapRoundTrip() throws Exception {
    byte[] nonce = new byte[32];
    nonce[0] = 7;
    byte[] ih = new byte[20];
    ih[0] = 1;
    byte[] pub = new byte[32];
    pub[0] = 2;

    RemoteSearchResponse.Builder b =
        RemoteSearchResponse.builder()
            .nonce(nonce)
            .timestamp(1_700_000_000L)
            .addRow(ih, "name", 100L, 1, pub)
            .signature(new byte[64]);
    RemoteSearchResponse unsigned = b.build();
    assertTrue(unsigned.isFinalChunk());
    assertEquals(0, unsigned.chunkIndex());

    KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(kp.getPrivate());
    signer.update(unsigned.canonicalBytes());
    RemoteSearchResponse signed =
        RemoteSearchResponse.builder()
            .nonce(nonce)
            .timestamp(1_700_000_000L)
            .addRow(ih, "name", 100L, 1, pub)
            .signature(signer.sign())
            .build();

    Map<String, Object> map = signed.toBencodeableMap();
    RemoteSearchResponse parsed = RemoteSearchResponse.fromBencodeableMap(map);
    assertTrue(parsed.isFinalChunk());
    assertEquals(0, parsed.chunkIndex());
    assertEquals(1, parsed.rows().size());
    assertEquals(RemoteSearchResponse.VERSION, parsed.version());
    assertEquals(
        3, RemoteSearchResponse.VERSION, "wire v3 rows may carry optional bt seeder endpoints");
    assertEquals(
        2,
        RemoteSearchResponse.VERSION_2,
        "wire v2 always covers chunk+final in the signature domain");
  }

  @Test
  void streamChunkChangesCanonicalDomain() throws Exception {
    byte[] nonce = new byte[32];
    byte[] ih = new byte[20];
    byte[] pub = new byte[32];

    RemoteSearchResponse partial =
        RemoteSearchResponse.builder()
            .nonce(nonce)
            .timestamp(100L)
            .chunkIndex(0)
            .finalChunk(false)
            .addRow(ih, "a", 1L, 1, pub)
            .signature(new byte[64])
            .build();
    RemoteSearchResponse bulk =
        RemoteSearchResponse.builder()
            .nonce(nonce)
            .timestamp(100L)
            .chunkIndex(0)
            .finalChunk(true)
            .addRow(ih, "a", 1L, 1, pub)
            .signature(new byte[64])
            .build();
    assertFalse(java.util.Arrays.equals(partial.canonicalBytes(), bulk.canonicalBytes()));
    assertFalse(partial.isFinalChunk());
  }
}
