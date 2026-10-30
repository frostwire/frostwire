/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.Bencode;
import com.frostwire.util.Hex;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The byte-level entry points added for frostwire/frostwire#1398: {@link
 * KarmaChainEntry#fromBencode}, {@link KarmaChain#parseManifest(byte[], byte[])} and {@link
 * IdentityRecord#fromBencode}, which read untrusted bytes without jlibtorrent.
 */
class RelayRecordFromBencodeTest {

  private static KeyPair keyPair;
  private static byte[] owner;
  private static List<KarmaChainEntry> chain;

  @BeforeAll
  static void setUp() throws Exception {
    keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    owner = IdentityRecord.extractRawEd25519(keyPair.getPublic());
    KarmaChain karma = new KarmaChain(owner);
    karma.commitEpoch(new BitcoinBlockReference(144, new byte[32]), keyPair.getPrivate());
    karma.endorse(
        new byte[32],
        new byte[20],
        new BitcoinBlockReference(145, new byte[32]),
        keyPair.getPrivate());
    chain = karma.entries();
  }

  /** A manifest entry dictionary as {@link KarmaChainPublisher} writes it (no per-entry pub). */
  private static Map<String, Object> manifestDict(KarmaChainEntry e) {
    Map<String, Object> m = new TreeMap<>();
    m.put("k", e.kind().code());
    m.put("seq", e.seq());
    m.put("bh", e.blockHeight());
    m.put("bkh", Hex.encode(e.blockHash()));
    m.put("ph", Hex.encode(e.prevHash()));
    if (e.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
      m.put("ep", e.epoch());
      m.put("en", String.format(java.util.Locale.ROOT, "%.3f", e.energy()));
    } else {
      m.put("pp", Base64.getUrlEncoder().withoutPadding().encodeToString(e.peerPub()));
      m.put("ih", Hex.encode(e.infoHash()));
    }
    m.put("s", Base64.getUrlEncoder().withoutPadding().encodeToString(e.signature()));
    return m;
  }

  private static byte[] manifest(List<KarmaChainEntry> entries) {
    Map<String, Object> m = new TreeMap<>();
    List<Object> dicts = new ArrayList<>();
    for (KarmaChainEntry e : entries) {
      dicts.add(manifestDict(e));
    }
    m.put("entries", dicts);
    m.put("v", 1L);
    return Bencode.encode(m);
  }

  @Test
  void karmaEntryFromBencodeReadsPublishedEntries() {
    for (KarmaChainEntry e : chain) {
      KarmaChainEntry parsed = KarmaChainEntry.fromBencode(Bencode.encode(manifestDict(e)), owner);
      assertNotNull(parsed);
      assertEquals(e, parsed);
      assertArrayEquals(e.canonicalBytes(), parsed.canonicalBytes());
      assertTrue(parsed.verifySignature());
    }
    // Without the owner fallback the entry needs its own pub.
    assertNull(KarmaChainEntry.fromBencode(Bencode.encode(manifestDict(chain.get(0)))));
  }

  @Test
  void karmaEntryFromBencodeReturnsNullForTheWrongShape() {
    Map<String, Object> wrongType = manifestDict(chain.get(0));
    wrongType.put("seq", "zero");
    assertNull(KarmaChainEntry.fromBencode(Bencode.encode(wrongType), owner));
    Map<String, Object> unknownKind = manifestDict(chain.get(0));
    unknownKind.put("k", "XX");
    assertNull(KarmaChainEntry.fromBencode(Bencode.encode(unknownKind), owner));
    Map<String, Object> missing = manifestDict(chain.get(1));
    missing.remove("ih");
    assertNull(KarmaChainEntry.fromBencode(Bencode.encode(missing), owner));
  }

  @Test
  void karmaEntryFromBencodeRejectsMalformedBytes() {
    Map<String, Object> badSig = manifestDict(chain.get(0));
    badSig.put("s", "!!!");
    assertThrows(
        IllegalArgumentException.class,
        () -> KarmaChainEntry.fromBencode(Bencode.encode(badSig), owner));
    assertThrows(
        IllegalArgumentException.class,
        () -> KarmaChainEntry.fromBencode("d1:k".getBytes(StandardCharsets.US_ASCII), owner));
    assertThrows(
        IllegalArgumentException.class,
        () -> KarmaChainEntry.fromBencode("le".getBytes(StandardCharsets.US_ASCII), owner));
  }

  @Test
  void parseManifestVerifiesTheChain() {
    assertEquals(chain, KarmaChain.parseManifest(manifest(chain), owner));
    // Out of order: the hash links break.
    List<KarmaChainEntry> reversed = new ArrayList<>(chain);
    java.util.Collections.reverse(reversed);
    assertNull(KarmaChain.parseManifest(manifest(reversed), owner));
    // Fetched under another key.
    assertNull(KarmaChain.parseManifest(manifest(chain), new byte[32]));
    assertNull(KarmaChain.parseManifest("le".getBytes(StandardCharsets.US_ASCII), owner));
    assertNull(KarmaChain.parseManifest("de".getBytes(StandardCharsets.US_ASCII), owner));
    assertNull(KarmaChain.parseManifest(new byte[KarmaChain.MAX_REMOTE_MANIFEST_BYTES + 1], owner));
  }

  @Test
  void identityRecordFromBencodeRoundTripsAndVerifies() {
    IdentityRecord record =
        IdentityRecord.createSigned(new byte[20], keyPair, new byte[32], 6881, 6882, "BOTH");
    assertEquals(record, IdentityRecord.fromBencode(record.toBencode()));
  }

  @Test
  void identityRecordFromBencodeRejectsBadRecords() {
    IdentityRecord record =
        IdentityRecord.createSigned(new byte[20], keyPair, new byte[32], 6881, 6882, "BOTH");
    Map<String, Object> dict = Bencode.decodeDictionary(record.toBencode());

    Map<String, Object> missing = new TreeMap<>(dict);
    missing.remove("node_id");
    assertRejected(missing);

    Map<String, Object> wrongType = new TreeMap<>(dict);
    wrongType.put("utp_port", "6881");
    assertRejected(wrongType);

    Map<String, Object> tampered = new TreeMap<>(dict);
    tampered.put("utp_port", 6883L);
    assertRejected(tampered);

    Map<String, Object> future = new TreeMap<>(dict);
    future.put("v", (long) IdentityRecord.VERSION + 1);
    assertRejected(future);

    assertThrows(
        IllegalArgumentException.class,
        () -> IdentityRecord.fromBencode("le".getBytes(StandardCharsets.US_ASCII)));
    assertThrows(IllegalArgumentException.class, () -> IdentityRecord.fromBencode(new byte[0]));
  }

  /**
   * frostwire/frostwire#1401: energy "NaN" passed the range check (both comparisons are false) and
   * then lifted KarmaChain.verify's endorsement budget, which compares against floor(energy).
   */
  @Test
  void karmaCommitmentWithNonFiniteEnergyIsRejected() {
    for (String energy : new String[] {"NaN", "Infinity", "-Infinity"}) {
      Map<String, Object> dict = manifestDict(chain.get(0));
      dict.put("en", energy);
      assertNull(KarmaChainEntry.fromBencode(Bencode.encode(dict), owner), energy);
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            KarmaChainEntry.createEpochCommitment(
                new byte[32],
                0,
                owner,
                new BitcoinBlockReference(144, new byte[32]),
                Double.NaN,
                keyPair.getPrivate()));
  }

  /** frostwire/frostwire#1401: wire integers are range-checked, not truncated by (int). */
  @Test
  void outOfRangeWireIntegersAreRejectedNotTruncated() {
    Map<String, Object> endorsement = manifestDict(chain.get(1));
    endorsement.put("sd", (1L << 32) + 1);
    assertNull(KarmaChainEntry.fromBencode(Bencode.encode(endorsement), owner));

    IdentityRecord record =
        IdentityRecord.createSigned(new byte[20], keyPair, new byte[32], 6881, 6882, "BOTH");
    Map<String, Object> dict = Bencode.decodeDictionary(record.toBencode());
    long wrappingVersion = (1L << 32) + ((Number) dict.get("v")).longValue();
    Map<String, Object> wrapped = new TreeMap<>(dict);
    wrapped.put("v", wrappingVersion);
    assertRejected(wrapped);
    for (long port : new long[] {(1L << 32) + 6881, Integer.MIN_VALUE - 1L}) {
      Map<String, Object> badPort = new TreeMap<>(dict);
      badPort.put("utp_port", port);
      assertRejected(badPort);
    }
  }

  private static void assertRejected(Map<String, Object> dict) {
    byte[] bytes = Bencode.encode(dict);
    assertThrows(IllegalArgumentException.class, () -> IdentityRecord.fromBencode(bytes));
  }
}
