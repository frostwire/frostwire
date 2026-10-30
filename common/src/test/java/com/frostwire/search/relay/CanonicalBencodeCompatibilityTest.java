/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Bencode;
import com.frostwire.util.Hex;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Signatures on karma chain entries and identity records cover canonical bytes that used to come
 * from jlibtorrent's native {@code Entry.bencode()} and now come from the pure-Java {@link Bencode}
 * (frostwire/frostwire#1398). This test keeps the old {@code Entry}-based canonicalization, copied
 * verbatim from before the change, and checks for many records that:
 *
 * <ul>
 *   <li>the new canonical bytes equal the old ones, byte for byte;
 *   <li>a record signed over the old bytes verifies through the new code;
 *   <li>a record signed by the new code verifies over the old bytes.
 * </ul>
 *
 * <p>Strings are kept in the Basic Multilingual Plane without U+0000: there jlibtorrent 2.0.12.9's
 * modified-UTF-8 JNI strings and real UTF-8 are the same bytes. Outside it (U+0000, emoji) that
 * jlibtorrent wrote CESU-8, which frostwire/frostwire-jlibtorrent#336 replaced with real UTF-8, the
 * form {@link Bencode} writes. Every field the protocol signs is ASCII (hex, base64, kind codes,
 * decimals) except an identity record's {@code role} and {@code ib_ver}, which FrostWire sets to
 * ASCII values.
 */
class CanonicalBencodeCompatibilityTest {

  private static KeyPair keyPair;
  private static byte[] pubRaw;
  private static final Random RANDOM = new Random(1398);

  private static final long[] LONGS = {
    0,
    1,
    -1,
    143,
    144,
    850_000,
    Integer.MAX_VALUE,
    Integer.MIN_VALUE,
    1L << 40,
    Long.MAX_VALUE,
    Long.MIN_VALUE,
    Long.MAX_VALUE - 1,
    Long.MIN_VALUE + 1,
    1_000_000_000_000L
  };
  private static final int[] INTS = {
    0, 1, -1, 2, 65535, 65536, Integer.MAX_VALUE, Integer.MIN_VALUE
  };
  private static final double[] ENERGIES = {
    0.0,
    0.004,
    0.005,
    0.015,
    1.0,
    5.0,
    9.999,
    12.345,
    KarmaConstants.MAX_ENERGY / 2,
    KarmaConstants.MAX_ENERGY
  };
  private static final String[] STRINGS = {
    "",
    "BOTH",
    "FORWARDER",
    "CLIENT",
    "client",
    "1.1.0",
    "2.0.0-βeta",
    "rôle",
    "日本語",
    "Ünïcødé-ß",
    "�",
    "￯",
    "a b\tc\nd",
    "x".repeat(300),
    "中".repeat(100)
  };

  @BeforeAll
  static void setUp() throws Exception {
    keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    pubRaw = IdentityRecord.extractRawEd25519(keyPair.getPublic());
  }

  // --- the canonicalization before frostwire/frostwire#1398, kept verbatim ---

  private static byte[] legacyKarmaCanonicalBytes(
      KarmaChainEntry.Kind kind,
      byte[] prevHash,
      long seq,
      byte[] endorserPub,
      long blockHeight,
      byte[] blockHash,
      Long epoch,
      Double energy,
      byte[] peerPub,
      byte[] infoHash,
      Integer scoreDelta) {
    Map<String, Object> map = new TreeMap<>();
    map.put("bh", new Entry(blockHeight));
    map.put("bk", new Entry(Hex.encode(blockHash)));
    map.put("k", new Entry(kind.code()));
    map.put("ph", new Entry(Hex.encode(prevHash)));
    map.put("pub", new Entry(Base64.getUrlEncoder().withoutPadding().encodeToString(endorserPub)));
    map.put("seq", new Entry(seq));
    if (kind == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
      map.put("e", new Entry(String.format(Locale.ROOT, "%.2f", energy)));
      map.put("ep", new Entry(epoch));
    } else {
      map.put("ih", new Entry(Hex.encode(infoHash)));
      map.put("pp", new Entry(Base64.getUrlEncoder().withoutPadding().encodeToString(peerPub)));
      map.put("sd", new Entry(scoreDelta));
    }
    return Entry.fromMap(map).bencode();
  }

  private static byte[] legacyKarmaCanonicalBytes(KarmaChainEntry e) {
    return legacyKarmaCanonicalBytes(
        e.kind(),
        e.prevHash(),
        e.seq(),
        e.endorserPub(),
        e.blockHeight(),
        e.blockHash(),
        e.epoch(),
        e.energy(),
        e.peerPub(),
        e.infoHash(),
        e.scoreDelta());
  }

  private static Map<String, Object> legacyIdentityCanonicalMap(
      byte[] nodeId,
      byte[] ed25519Pub,
      byte[] x25519Pub,
      int utpPort,
      int rudpPort,
      String role,
      long capabilities,
      String icebridgeVersion,
      int wireVersion,
      long firstSeen,
      long lastSeen) {
    Map<String, Object> map = new TreeMap<>();
    map.put("ed25519_pub", new Entry(b64(ed25519Pub)));
    map.put("first_seen", new Entry(firstSeen));
    if (wireVersion >= 4) {
      map.put("ib_ver", new Entry(icebridgeVersion != null ? icebridgeVersion : ""));
    }
    map.put("last_seen", new Entry(lastSeen));
    map.put("node_id", new Entry(Hex.encode(nodeId)));
    if (wireVersion >= 2) {
      map.put("rudp_port", new Entry((long) rudpPort));
      map.put("role", new Entry(role));
    }
    if (wireVersion >= 3) {
      map.put("caps", new Entry(capabilities));
    }
    map.put("utp_port", new Entry((long) utpPort));
    map.put("v", new Entry((long) wireVersion));
    map.put("x25519_pub", new Entry(b64(x25519Pub)));
    return map;
  }

  private static Map<String, Object> legacyIdentityCanonicalMap(IdentityRecord r) {
    return legacyIdentityCanonicalMap(
        r.nodeId(),
        r.ed25519Pub(),
        r.x25519Pub(),
        r.utpPort(),
        r.rudpPort(),
        r.role(),
        r.capabilities(),
        r.icebridgeVersion(),
        r.version(),
        r.firstSeen(),
        r.lastSeen());
  }

  // --- karma chain entries ---

  @Test
  void karmaEntriesMatchTheEntryEncodingAndSignaturesCrossVerify() throws Exception {
    int checked = 0;
    for (int i = 0; i < 400; i++) {
      KarmaChainEntry.Kind kind =
          i % 2 == 0 ? KarmaChainEntry.Kind.EPOCH_COMMITMENT : KarmaChainEntry.Kind.ENDORSEMENT;
      byte[] prevHash = i % 7 == 0 ? KarmaChainEntry.GENESIS_PREV_HASH : pattern(32, i);
      long seq = pick(LONGS, i);
      long blockHeight = pick(LONGS, i / 3);
      byte[] blockHash = pattern(32, i + 1);
      Long epoch = kind == KarmaChainEntry.Kind.EPOCH_COMMITMENT ? pick(LONGS, i / 5) : null;
      Double energy =
          kind == KarmaChainEntry.Kind.EPOCH_COMMITMENT ? ENERGIES[i % ENERGIES.length] : null;
      byte[] peerPub = kind == KarmaChainEntry.Kind.ENDORSEMENT ? pattern(32, i + 2) : null;
      byte[] infoHash = kind == KarmaChainEntry.Kind.ENDORSEMENT ? pattern(20, i + 3) : null;
      Integer scoreDelta = kind == KarmaChainEntry.Kind.ENDORSEMENT ? INTS[i % INTS.length] : null;

      byte[] legacy =
          legacyKarmaCanonicalBytes(
              kind,
              prevHash,
              seq,
              pubRaw,
              blockHeight,
              blockHash,
              epoch,
              energy,
              peerPub,
              infoHash,
              scoreDelta);
      byte[] legacySignature = sign(legacy);
      KarmaChainEntry entry =
          KarmaChainEntry.fromStoredFields(
              kind,
              prevHash,
              seq,
              pubRaw,
              RANDOM.nextLong(),
              blockHeight,
              blockHash,
              epoch,
              energy,
              peerPub,
              infoHash,
              scoreDelta,
              legacySignature);

      assertArrayEquals(legacy, entry.canonicalBytes(), "canonical bytes of " + entry);
      assertArrayEquals(sha256(legacy), entry.entryHash());
      assertTrue(entry.verifySignature(), "old-path signature must verify: " + entry);
      checked++;
    }
    assertEquals(400, checked);
  }

  @Test
  void karmaEntriesSignedByTheNewCodeVerifyOverTheEntryEncoding() throws Exception {
    for (int i = 0; i < 60; i++) {
      long height = Math.abs(pick(LONGS, i) % (Long.MAX_VALUE / 2));
      BitcoinBlockReference block = new BitcoinBlockReference(height, pattern(32, i));
      byte[] prevHash = i % 5 == 0 ? KarmaChainEntry.GENESIS_PREV_HASH : pattern(32, i + 9);
      long seq = pick(LONGS, i + 1);
      KarmaChainEntry entry =
          i % 2 == 0
              ? KarmaChainEntry.createEpochCommitment(
                  prevHash, seq, pubRaw, block, ENERGIES[i % ENERGIES.length], keyPair.getPrivate())
              : KarmaChainEntry.createEndorsement(
                  prevHash,
                  seq,
                  pubRaw,
                  block,
                  pattern(32, i + 4),
                  pattern(20, i + 5),
                  INTS[i % INTS.length],
                  keyPair.getPrivate());
      byte[] legacy = legacyKarmaCanonicalBytes(entry);
      assertArrayEquals(legacy, entry.canonicalBytes());
      assertTrue(verify(legacy, entry.signature()), "new-path signature over old bytes: " + entry);
    }
  }

  // --- identity records ---

  @Test
  void identityRecordsSignedOverTheEntryEncodingVerifyThroughTheNewCode() throws Exception {
    int checked = 0;
    for (int version = 1; version <= IdentityRecord.VERSION; version++) {
      for (int i = 0; i < 60; i++) {
        byte[] nodeId = pattern(20, i);
        byte[] x25519 = pattern(32, i + 1);
        int utpPort = INTS[i % INTS.length];
        int rudpPort = version >= 2 ? INTS[(i / 2) % INTS.length] : 0;
        String role = version >= 2 ? STRINGS[i % STRINGS.length] : "BOTH";
        long caps =
            version >= 3
                // Long.MIN_VALUE can't travel: libtorrent's bdecode reads it as 0 and Bencode
                // rejects it (see BencodeTest.decodesLongExtremes).
                ? Math.max(pick(LONGS, i), Long.MIN_VALUE + 1)
                : version == 2 ? NodeCapabilities.fromRole(role) : NodeCapabilities.DEFAULT_BOTH;
        String ibVer = version >= 4 ? STRINGS[(i + 3) % STRINGS.length] : "";
        long firstSeen = 1 + Math.abs(pick(LONGS, i) % (Long.MAX_VALUE - 1));
        long lastSeen = i % 3 == 0 ? firstSeen : Long.MAX_VALUE;

        Map<String, Object> legacyMap =
            legacyIdentityCanonicalMap(
                nodeId, pubRaw, x25519, utpPort, rudpPort, role, caps, ibVer, version, firstSeen,
                lastSeen);
        byte[] legacyCanonical = Entry.fromMap(legacyMap).bencode();
        byte[] signature = sign(legacyCanonical);
        legacyMap.put("sig", new Entry(b64(signature)));
        Entry legacyRecord = Entry.fromMap(legacyMap);
        byte[] legacyRecordBytes = legacyRecord.bencode();

        IdentityRecord viaBytes = IdentityRecord.fromBencode(legacyRecordBytes);
        IdentityRecord viaEntry = IdentityRecord.fromEntry(legacyRecord);
        assertEquals(viaBytes, viaEntry);
        assertEquals(version, viaBytes.version());
        assertEquals(role, viaBytes.role());
        assertEquals(ibVer, viaBytes.icebridgeVersion());
        assertEquals(caps, viaBytes.capabilities());
        assertArrayEquals(legacyCanonical, viaBytes.canonicalBytes());
        assertArrayEquals(legacyRecordBytes, viaBytes.toBencode());
        assertArrayEquals(legacyRecordBytes, viaBytes.toEntry().bencode());
        assertArrayEquals(legacyCanonical, viaBytes.toCanonicalEntry().bencode());
        assertTrue(viaBytes.verifySignature());

        // The other direction: re-sign through the new code, verify over the old bytes.
        IdentityRecord resigned = viaBytes.withUpdatedLastSeen(lastSeen, keyPair.getPrivate());
        byte[] legacyResigned = Entry.fromMap(legacyIdentityCanonicalMap(resigned)).bencode();
        assertArrayEquals(legacyResigned, resigned.canonicalBytes());
        assertTrue(verify(legacyResigned, resigned.signature()));
        checked++;
      }
    }
    assertEquals(60 * IdentityRecord.VERSION, checked);
  }

  @Test
  void identityRecordsCreatedByTheNewCodeVerifyOverTheEntryEncoding() throws Exception {
    for (String role : STRINGS) {
      for (long caps : new long[] {0, -1, NodeCapabilities.fromRole(role), Long.MAX_VALUE}) {
        IdentityRecord record =
            IdentityRecord.createSigned(
                pattern(20, role.length()), keyPair, pattern(32, 7), 6881, 6882, role, caps);
        byte[] legacy = Entry.fromMap(legacyIdentityCanonicalMap(record)).bencode();
        assertArrayEquals(legacy, record.canonicalBytes());
        assertTrue(verify(legacy, record.signature()));
        assertEquals(record, IdentityRecord.fromEntry(Entry.bdecode(record.toBencode())));
      }
    }
  }

  // --- generic values: Bencode.encode against Entry.fromMap/fromList ---

  @Test
  void genericValuesEncodeLikeEntry() {
    for (int i = 0; i < 200; i++) {
      Map<String, Object> neutral = new TreeMap<>();
      Map<String, Object> legacy = new TreeMap<>();
      int fields = RANDOM.nextInt(8);
      for (int f = 0; f < fields; f++) {
        String key = STRINGS[RANDOM.nextInt(STRINGS.length)] + f;
        if (RANDOM.nextBoolean()) {
          long n = pick(LONGS, RANDOM.nextInt(LONGS.length));
          neutral.put(key, n);
          legacy.put(key, new Entry(n));
        } else if (RANDOM.nextBoolean()) {
          String s = STRINGS[RANDOM.nextInt(STRINGS.length)];
          neutral.put(key, s);
          legacy.put(key, new Entry(s));
        } else {
          List<Object> list = new ArrayList<>();
          List<Object> legacyList = new ArrayList<>();
          for (int k = 0; k < RANDOM.nextInt(4); k++) {
            String s = STRINGS[RANDOM.nextInt(STRINGS.length)];
            list.add(s);
            legacyList.add(new Entry(s));
          }
          Map<String, Object> inner = new TreeMap<>();
          inner.put("z", -5L);
          inner.put("é", "x");
          list.add(inner);
          Map<String, Object> legacyInner = new TreeMap<>();
          legacyInner.put("z", new Entry(-5L));
          legacyInner.put("é", new Entry("x"));
          legacyList.add(Entry.fromMap(legacyInner));
          neutral.put(key, list);
          legacy.put(key, Entry.fromList(legacyList));
        }
      }
      byte[] expected = Entry.fromMap(legacy).bencode();
      assertArrayEquals(expected, Bencode.encode(neutral), neutral.keySet().toString());
      // Bencode writes Long.MIN_VALUE like Entry does but won't read it (libtorrent can't).
      if (!new String(expected, StandardCharsets.ISO_8859_1).contains("i-9223372036854775808e")) {
        assertArrayEquals(expected, Bencode.encode(Bencode.decode(expected)));
      }
    }
  }

  // --- helpers ---

  private static long pick(long[] values, int i) {
    return values[Math.floorMod(i, values.length)];
  }

  private static byte[] pattern(int length, int seed) {
    byte[] b = new byte[length];
    if (seed % 11 == 0) {
      Arrays.fill(b, (byte) 0xff);
    } else if (seed % 13 != 0) {
      new Random(seed).nextBytes(b);
    }
    return b;
  }

  private static byte[] sign(byte[] data) throws Exception {
    PrivateKey key = keyPair.getPrivate();
    Signature signer = IdentityKeys.softwareSignature("Ed25519");
    signer.initSign(key);
    signer.update(data);
    return signer.sign();
  }

  private static boolean verify(byte[] data, byte[] signature) throws Exception {
    Signature verifier = IdentityKeys.softwareSignature("Ed25519");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(data);
    return verifier.verify(signature);
  }

  private static byte[] sha256(byte[] data) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(data);
  }

  private static String b64(byte[] bytes) {
    return Base64.getEncoder().withoutPadding().encodeToString(bytes);
  }
}
