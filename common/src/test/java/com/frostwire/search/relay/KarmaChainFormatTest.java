/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static com.frostwire.search.relay.KarmaChainEntry.FORMAT_DECAY_PER_COMMITMENT;
import static com.frostwire.search.relay.KarmaChainEntry.FORMAT_DECAY_PER_EPOCH;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.Bencode;
import com.frostwire.util.Hex;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Chain format 2 (frostwire/frostwire#1416, question 7): an epoch commitment's carried energy
 * decays ×0.5 for every epoch that passed, not once per commitment, and the commitment says so in
 * its signed form. Format 1 chains, which FrostWire up to 7.1.3 published, keep verifying under
 * their own rule.
 */
class KarmaChainFormatTest {

  private static final long EPOCH = 6700;
  private static final byte[] PEER = filled(32, 7);

  private static byte[] owner;
  private static PrivateKey key;

  @BeforeAll
  static void identity() throws Exception {
    IdentityKeys id = IdentityKeys.generate(0);
    owner = id.ed25519PubRaw();
    key = id.ed25519().getPrivate();
  }

  @Test
  void theWriterDecaysTheCarriedEnergyOncePerSkippedEpoch() {
    KarmaChain chain = new KarmaChain(owner);
    chain.commitEpoch(block(EPOCH), key);
    KarmaChainEntry later = chain.commitEpoch(block(EPOCH + 3), key);

    assertEquals(FORMAT_DECAY_PER_EPOCH, later.formatVersion());
    // 5 fresh + 5 unused halved three times, not 5 + 5 * 0.5 = 7.5.
    assertEquals(5.625, later.energy(), 1e-9);
    assertTrue(KarmaChain.verify(chain.entries()));
  }

  @Test
  void aFormat2CommitmentThatDecaysOnlyOnceFailsVerification() {
    KarmaChainEntry first = commitment(KarmaChainEntry.GENESIS_PREV_HASH, 0, EPOCH, 5, 2);
    KarmaChainEntry hoarded = commitment(first.entryHash(), 1, EPOCH + 3, 7.5, 2);

    assertFalse(KarmaChain.verify(List.of(first, hoarded)));
  }

  @Test
  void aFormat1ChainKeepsVerifyingUnderItsOwnRule() {
    KarmaChainEntry first = commitment(KarmaChainEntry.GENESIS_PREV_HASH, 0, EPOCH, 5, 1);
    KarmaChainEntry once = commitment(first.entryHash(), 1, EPOCH + 3, 7.5, 1);
    KarmaChainEntry perEpoch = commitment(first.entryHash(), 1, EPOCH + 3, 5.625, 1);

    assertTrue(KarmaChain.verify(List.of(first, once)));
    assertFalse(KarmaChain.verify(List.of(first, perEpoch)), "format 1 decays once");
  }

  @Test
  void aFormat1ChainMovesToFormat2AtItsNextCommitment() {
    KarmaChainEntry first = commitment(KarmaChainEntry.GENESIS_PREV_HASH, 0, EPOCH, 5, 1);
    KarmaChainEntry endorsement =
        KarmaChainEntry.createEndorsement(
            first.entryHash(), 1, owner, block(EPOCH), PEER, filled(20, 1), 1, key);
    KarmaChain chain = KarmaChain.load(owner, List.of(first, endorsement));
    assertNotNull(chain);

    KarmaChainEntry next = chain.commitEpoch(block(EPOCH + 2), key);

    assertEquals(FORMAT_DECAY_PER_EPOCH, next.formatVersion());
    assertEquals(5 + 4 * 0.25, next.energy(), 1e-9);
    assertTrue(KarmaChain.verify(chain.entries()));
  }

  @Test
  void aChainNeverGoesBackToFormat1() {
    KarmaChainEntry first = commitment(KarmaChainEntry.GENESIS_PREV_HASH, 0, EPOCH, 5, 2);
    KarmaChainEntry back = commitment(first.entryHash(), 1, EPOCH + 1, 7.5, 1);

    assertFalse(KarmaChain.verify(List.of(first, back)));
  }

  @Test
  void theFormatIsSigned() {
    KarmaChainEntry v2 = commitment(KarmaChainEntry.GENESIS_PREV_HASH, 0, EPOCH, 5, 2);
    KarmaChainEntry relabeled =
        KarmaChainEntry.fromStoredFields(
            v2.kind(),
            FORMAT_DECAY_PER_COMMITMENT,
            v2.prevHash(),
            v2.seq(),
            v2.endorserPub(),
            v2.timestamp(),
            v2.blockHeight(),
            v2.blockHash(),
            v2.epoch(),
            v2.energy(),
            null,
            null,
            null,
            v2.signature());

    assertTrue(v2.verifySignature());
    assertFalse(relabeled.verifySignature());
  }

  @Test
  void format1CanonicalBytesAreUnchanged() {
    KarmaChainEntry v1 = commitment(KarmaChainEntry.GENESIS_PREV_HASH, 0, EPOCH, 5, 1);

    Map<String, Object> signed = Bencode.decodeDictionary(v1.canonicalBytes());
    assertFalse(signed.containsKey("fv"));
    assertEquals(
        2L,
        Bencode.decodeDictionary(commitment(v1.prevHash(), 0, EPOCH, 5, 2).canonicalBytes())
            .get("fv"));
  }

  @Test
  void manifestEntriesCarryTheirFormat() {
    KarmaChainEntry v2 = commitment(KarmaChainEntry.GENESIS_PREV_HASH, 0, EPOCH, 5, 2);
    Map<String, Object> dict = manifestDict(v2);

    KarmaChainEntry parsed = KarmaChainEntry.fromDictionary(dict, owner);
    assertEquals(FORMAT_DECAY_PER_EPOCH, parsed.formatVersion());
    assertTrue(parsed.verifySignature());
    assertArrayEquals(v2.entryHash(), parsed.entryHash());

    dict.put("fv", 3L);
    assertNull(KarmaChainEntry.fromDictionary(dict, owner), "an unknown format is refused");
    dict.put("fv", "2");
    assertNull(KarmaChainEntry.fromDictionary(dict, owner));
  }

  @Test
  void endorsementsHaveNoFormat() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            KarmaChainEntry.fromStoredFields(
                KarmaChainEntry.Kind.ENDORSEMENT,
                FORMAT_DECAY_PER_EPOCH,
                new byte[32],
                1,
                owner,
                0,
                EPOCH * KarmaConstants.BLOCKS_PER_EPOCH,
                new byte[32],
                null,
                null,
                PEER,
                new byte[20],
                1,
                new byte[64]));
  }

  @Test
  void energyAtEpochHalvesTheCarriedEnergyEachEpoch() {
    assertEquals(7.5, KarmaConstants.energyAtEpoch(5, 1), 1e-12);
    assertEquals(5.625, KarmaConstants.energyAtEpoch(5, 3), 1e-12);
    assertEquals(5.0, KarmaConstants.energyAtEpoch(5, 5000), 1e-12);
    assertThrows(IllegalArgumentException.class, () -> KarmaConstants.energyAtEpoch(5, 0));
  }

  private static Map<String, Object> manifestDict(KarmaChainEntry e) {
    Map<String, Object> m = new TreeMap<>();
    m.put("k", e.kind().code());
    m.put("seq", e.seq());
    m.put("bh", e.blockHeight());
    m.put("bkh", Hex.encode(e.blockHash()));
    m.put("ph", Hex.encode(e.prevHash()));
    m.put("ep", e.epoch());
    m.put("en", String.format(java.util.Locale.ROOT, "%.2f", e.energy()));
    m.put("fv", (long) e.formatVersion());
    m.put("s", Base64.getUrlEncoder().withoutPadding().encodeToString(e.signature()));
    return m;
  }

  private static KarmaChainEntry commitment(
      byte[] prevHash, long seq, long epoch, double energy, int format) {
    return KarmaChainEntry.createEpochCommitment(
        prevHash, seq, owner, block(epoch), energy, format, key);
  }

  private static BitcoinBlockReference block(long epoch) {
    long height = epoch * KarmaConstants.BLOCKS_PER_EPOCH;
    byte[] hash = new byte[32];
    hash[0] = (byte) epoch;
    hash[1] = (byte) (epoch >> 8);
    return new BitcoinBlockReference(height, hash);
  }

  private static byte[] filled(int n, int v) {
    byte[] b = new byte[n];
    java.util.Arrays.fill(b, (byte) v);
    return b;
  }
}
