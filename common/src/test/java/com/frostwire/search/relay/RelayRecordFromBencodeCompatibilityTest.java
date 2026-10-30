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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.Bencode;
import com.frostwire.util.Hex;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Compatibility fixtures for frostwire/frostwire#1401 (non-finite karma energy and truncated wire
 * integers). The fix rejects only NaN/±Infinity energy and wire integers outside the int range;
 * everything on the valid side of those boundaries must still decode to the same values, and the
 * per-epoch endorsement budget must still be enforced for every finite energy.
 */
class RelayRecordFromBencodeCompatibilityTest {

  private static final long TWO_TO_31 = 1L << 31;

  private static KeyPair keyPair;
  private static byte[] owner;
  private static Map<String, Object> commitmentDict;
  private static Map<String, Object> endorsementDict;

  @BeforeAll
  static void setUp() throws Exception {
    keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    owner = IdentityRecord.extractRawEd25519(keyPair.getPublic());
    List<KarmaChainEntry> chain = chain(2.0, 1);
    commitmentDict = manifestDict(chain.get(0));
    endorsementDict = manifestDict(chain.get(1));
  }

  // --- Karma energy: the wire "en" string ---

  @Test
  void finiteInRangeEnergiesDecodeToTheSameValue() {
    Object[][] cases = {
      {"0.000", 0.0},
      {"0", 0.0},
      {"0.001", 0.001},
      {"1.000", 1.0},
      {"2.500", 2.5},
      {"10.000", KarmaConstants.MAX_ENERGY},
      // The parser tolerates 0.01 above MAX_ENERGY for rounding.
      {"10.010", 10.01},
      {"4.9E-324", Double.MIN_VALUE},
      {"1e-400", 0.0},
      {"1e1", 10.0},
      // Double.parseDouble's wider language, accepted before the fix and still accepted.
      {"0x1p1", 2.0},
      {"1.5d", 1.5},
      {" 2.5 ", 2.5},
      {"+3", 3.0},
    };
    for (Object[] c : cases) {
      KarmaChainEntry e = commitmentWithEnergy((String) c[0]);
      assertNotNull(e, (String) c[0]);
      assertEquals((double) c[1], e.energy().doubleValue(), (String) c[0]);
      assertEquals(1L, e.epoch().longValue());
    }
  }

  @Test
  void negativeZeroEnergyIsAcceptedAndKeepsItsSign() {
    for (String en : new String[] {"-0.000", "-0", "-1e-400"}) {
      KarmaChainEntry e = commitmentWithEnergy(en);
      assertNotNull(e, en);
      assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(e.energy()), en);
    }
  }

  @Test
  void outOfRangeFiniteEnergiesAreRejectedAsBefore() {
    // The range check predates #1401; these were rejected before the fix too.
    for (String en :
        new String[] {
          "-0.001",
          "-1",
          "10.011",
          "11",
          "1e300",
          String.valueOf(Double.MAX_VALUE),
          String.valueOf(-Double.MAX_VALUE),
        }) {
      assertNull(commitmentWithEnergy(en), en);
    }
  }

  @Test
  void nonFiniteEnergiesAreRejected() {
    // Deliberately rejected by #1401 (accepted before the fix).
    for (String en : new String[] {"NaN", "Infinity", "-Infinity", "1e400"}) {
      assertNull(commitmentWithEnergy(en), en);
    }
  }

  @Test
  void createEpochCommitmentAcceptsTheWholeFiniteRange() throws Exception {
    for (double energy :
        new double[] {0.0, -0.0, Double.MIN_VALUE, 2.5, KarmaConstants.MAX_ENERGY}) {
      KarmaChainEntry e = commitment(energy);
      assertEquals(
          Double.doubleToRawLongBits(energy), Double.doubleToRawLongBits(e.energy()), "" + energy);
      assertTrue(e.verifySignature());
    }
    for (double energy :
        new double[] {
          -Double.MIN_VALUE,
          -1.0,
          Math.nextUp(KarmaConstants.MAX_ENERGY),
          1e300,
          Double.MAX_VALUE,
          -Double.MAX_VALUE,
        }) {
      assertThrows(IllegalArgumentException.class, () -> commitment(energy), "" + energy);
    }
  }

  // --- Endorsement budget: floor(energy) endorsements per commitment ---

  @Test
  void theEndorsementBudgetIsEnforcedForFiniteEnergies() throws Exception {
    double[] energies = {0.0, -0.0, 0.5, 0.999, 1.0, 2.5, 3.0, KarmaConstants.MAX_ENERGY};
    for (double energy : energies) {
      int budget = (int) Math.floor(energy);
      List<KarmaChainEntry> full = chain(energy, budget);
      assertTrue(KarmaChain.verify(full), "at budget, energy " + energy);
      assertTrue(KarmaChain.verifyTail(full, KarmaChainEntry.GENESIS_PREV_HASH, 0));
      List<KarmaChainEntry> parsed = KarmaChain.parseManifest(manifest(full), owner);
      assertNotNull(parsed, "manifest at budget, energy " + energy);
      assertEquals(budget + 1, parsed.size());
      assertEquals(
          String.format(Locale.ROOT, "%.3f", energy),
          String.format(Locale.ROOT, "%.3f", parsed.get(0).energy()));

      List<KarmaChainEntry> over = chain(energy, budget + 1);
      assertFalse(KarmaChain.verify(over), "over budget, energy " + energy);
      assertFalse(KarmaChain.verifyTail(over, KarmaChainEntry.GENESIS_PREV_HASH, 0));
      assertNull(KarmaChain.parseManifest(manifest(over), owner), "manifest over, " + energy);
    }
  }

  // --- Endorsement score delta "sd" ---

  @Test
  void scoreDeltasInTheIntRangeDecodeToTheSameValue() {
    long[] values = {0, 1, -1, 2, Integer.MAX_VALUE, Integer.MIN_VALUE};
    for (long sd : values) {
      Map<String, Object> dict = new TreeMap<>(endorsementDict);
      dict.put("sd", sd);
      KarmaChainEntry e = KarmaChainEntry.fromBencode(Bencode.encode(dict), owner);
      assertNotNull(e, "sd " + sd);
      assertEquals((int) sd, e.scoreDelta().intValue(), "sd " + sd);
    }
    Map<String, Object> absent = new TreeMap<>(endorsementDict);
    absent.remove("sd");
    assertEquals(1, KarmaChainEntry.fromBencode(Bencode.encode(absent), owner).scoreDelta());
  }

  @Test
  void scoreDeltasBeyondTheIntRangeAreRejected() {
    // Deliberately rejected by #1401 (truncated by (int) before the fix).
    for (long sd : new long[] {TWO_TO_31, Integer.MIN_VALUE - 1L, (1L << 32) + 1, Long.MAX_VALUE}) {
      Map<String, Object> dict = new TreeMap<>(endorsementDict);
      dict.put("sd", sd);
      assertNull(KarmaChainEntry.fromBencode(Bencode.encode(dict), owner), "sd " + sd);
    }
  }

  // --- Identity record: ports and v ---

  @Test
  void anyIntPortDecodesToTheSameValue() throws Exception {
    long[] ports = {0, 1, 6881, 65535, 65536, -1, Integer.MAX_VALUE, Integer.MIN_VALUE};
    for (long port : ports) {
      for (String key : new String[] {"utp_port", "rudp_port"}) {
        Map<String, Object> dict = baseIdentity();
        dict.put(key, port);
        IdentityRecord r = IdentityRecord.fromBencode(signedIdentity(dict, dict));
        int decoded = "utp_port".equals(key) ? r.utpPort() : r.rudpPort();
        assertEquals((int) port, decoded, key + " " + port);
        assertEquals(IdentityRecord.VERSION, r.version());
      }
    }
    // createSigned round-trips the same ports.
    for (int port : new int[] {0, 65535, 65536, -1, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
      IdentityRecord created =
          IdentityRecord.createSigned(new byte[20], keyPair, new byte[32], port, port, "BOTH");
      IdentityRecord parsed = IdentityRecord.fromBencode(created.toBencode());
      assertEquals(created, parsed);
      assertEquals(port, parsed.utpPort());
      assertEquals(port, parsed.rudpPort());
    }
  }

  @Test
  void portsBeyondTheIntRangeAreRejectedEvenWhenTheTruncatedValueIsSigned() throws Exception {
    // Deliberately rejected by #1401: before the fix, (int) truncated these to the signed value
    // and the record verified.
    long[][] cases = {
      {TWO_TO_31, Integer.MIN_VALUE},
      {Integer.MIN_VALUE - 1L, Integer.MAX_VALUE},
      {(1L << 32) + 6881, 6881},
    };
    for (long[] c : cases) {
      for (String key : new String[] {"utp_port", "rudp_port"}) {
        Map<String, Object> signedAs = baseIdentity();
        signedAs.put(key, c[1]);
        Map<String, Object> wire = new TreeMap<>(signedAs);
        wire.put(key, c[0]);
        byte[] bytes = signedIdentity(signedAs, wire);
        assertThrows(
            IllegalArgumentException.class, () -> IdentityRecord.fromBencode(bytes), key + c[0]);
      }
    }
  }

  @Test
  void everySupportedVersionStillVerifies() throws Exception {
    for (int v = 1; v <= IdentityRecord.VERSION; v++) {
      Map<String, Object> dict = baseIdentity();
      dict.put("v", (long) v);
      if (v < 4) dict.remove("ib_ver");
      if (v < 3) dict.remove("caps");
      if (v < 2) {
        dict.remove("rudp_port");
        dict.remove("role");
      }
      IdentityRecord r = IdentityRecord.fromBencode(signedIdentity(dict, dict));
      assertEquals(v, r.version());
      assertEquals(6881, r.utpPort());
      assertEquals(v >= 2 ? 6882 : 0, r.rudpPort(), "v" + v);
    }
  }

  @Test
  void unsupportedVersionsAreRejected() throws Exception {
    // Rejected before the fix too: they narrow to themselves and fail the 1..VERSION check.
    long[] versions = {0, -1, IdentityRecord.VERSION + 1, Integer.MAX_VALUE, Integer.MIN_VALUE};
    for (long v : versions) {
      Map<String, Object> dict = baseIdentity();
      dict.put("v", v);
      byte[] bytes = signedIdentity(dict, dict);
      assertThrows(IllegalArgumentException.class, () -> IdentityRecord.fromBencode(bytes), "" + v);
    }
  }

  @Test
  void versionsThatTruncateToASupportedVersionAreRejected() throws Exception {
    // Deliberately rejected by #1401: before the fix these narrowed to VERSION and verified.
    for (long v :
        new long[] {(1L << 32) + IdentityRecord.VERSION, Long.MIN_VALUE + IdentityRecord.VERSION}) {
      Map<String, Object> signedAs = baseIdentity();
      Map<String, Object> wire = new TreeMap<>(signedAs);
      wire.put("v", v);
      byte[] bytes = signedIdentity(signedAs, wire);
      assertThrows(IllegalArgumentException.class, () -> IdentityRecord.fromBencode(bytes), "" + v);
    }
  }

  // --- helpers ---

  /** A v{@link IdentityRecord#VERSION} record's dictionary without its signature. */
  private static Map<String, Object> baseIdentity() {
    IdentityRecord record =
        IdentityRecord.createSigned(new byte[20], keyPair, new byte[32], 6881, 6882, "BOTH");
    Map<String, Object> dict = new TreeMap<>(Bencode.decodeDictionary(record.toBencode()));
    dict.remove("sig");
    return dict;
  }

  /** Signs {@code signedAs} and returns {@code wire} with that signature, as bencode. */
  private static byte[] signedIdentity(Map<String, Object> signedAs, Map<String, Object> wire)
      throws Exception {
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(keyPair.getPrivate());
    signer.update(Bencode.encode(signedAs));
    Map<String, Object> out = new TreeMap<>(wire);
    out.put("sig", Base64.getEncoder().withoutPadding().encodeToString(signer.sign()));
    return Bencode.encode(out);
  }

  private static KarmaChainEntry commitmentWithEnergy(String en) {
    Map<String, Object> dict = new TreeMap<>(commitmentDict);
    dict.put("en", en);
    return KarmaChainEntry.fromBencode(Bencode.encode(dict), owner);
  }

  private static KarmaChainEntry commitment(double energy) {
    return KarmaChainEntry.createEpochCommitment(
        KarmaChainEntry.GENESIS_PREV_HASH,
        0,
        owner,
        new BitcoinBlockReference(144, new byte[32]),
        energy,
        keyPair.getPrivate());
  }

  /** An epoch-1 commitment with {@code energy} followed by {@code endorsements} endorsements. */
  private static List<KarmaChainEntry> chain(double energy, int endorsements) {
    List<KarmaChainEntry> entries = new ArrayList<>();
    entries.add(commitment(energy));
    for (int i = 0; i < endorsements; i++) {
      KarmaChainEntry prev = entries.get(entries.size() - 1);
      byte[] peer = new byte[32];
      peer[0] = (byte) (i + 1);
      entries.add(
          KarmaChainEntry.createEndorsement(
              prev.entryHash(),
              entries.size(),
              owner,
              new BitcoinBlockReference(145 + i, new byte[32]),
              peer,
              new byte[20],
              1,
              keyPair.getPrivate()));
    }
    return entries;
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
      m.put("en", String.format(Locale.ROOT, "%.3f", e.energy()));
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
}
