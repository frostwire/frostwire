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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Karma chain verification must hold every chain to the per-epoch endorsement budget: an epoch
 * commitment's epoch follows from its Bitcoin height, its energy from the previous commitment, and
 * no one endorses themselves (frostwire/frostwire#1415).
 *
 * <p>Compatibility fixtures (valid before and after): chains {@link KarmaChainWriter} produces,
 * across epochs, with skipped epochs and fractional energies, verify as a whole, as a published
 * manifest and as a trimmed tail; commitments at either side of an epoch boundary; energy within
 * the two-decimal rounding of the signed form.
 */
class KarmaChainVerifyHardeningTest {

  private static final byte[] PEER = key(7);
  private static IdentityKeys id;
  private static byte[] owner;

  @BeforeAll
  static void identity() throws Exception {
    id = IdentityKeys.generate(4);
    owner = id.ed25519PubRaw();
  }

  // ---- compatibility fixtures: valid chains keep verifying ----

  @Test
  void writerChainAcrossEpochsVerifies() throws Exception {
    WriterRun run = writerChainAcrossEpochs();

    List<KarmaChainEntry> entries = run.writer.chain().entries();
    assertTrue(KarmaChain.verify(entries));
    assertTrue(KarmaChain.verify(entries, run.blocks), "with the real blocks available");
    assertNotNull(KarmaChain.load(owner, entries));
    assertEquals(5, countCommitments(entries));
  }

  @Test
  void writerChainVerifiesAsAPublishedManifest() throws Exception {
    WriterRun run = writerChainAcrossEpochs();
    KarmaChainPublisher publisher = new KarmaChainPublisher(run.writer, id);

    byte[] manifest = publisher.buildManifest(run.writer.chain().entries()).bencode();

    List<KarmaChainEntry> parsed = KarmaChain.parseManifest(manifest, owner);
    assertNotNull(parsed, "the published (trimmed, two-decimal) form verifies");
    assertNotNull(KarmaChain.parseManifest(manifest, owner, run.blocks));
  }

  @Test
  void writerChainTailsVerify() throws Exception {
    WriterRun run = writerChainAcrossEpochs();
    List<KarmaChainEntry> entries = run.writer.chain().entries();

    for (int i = 1; i < entries.size(); i++) {
      if (entries.get(i).kind() != KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
        continue;
      }
      List<KarmaChainEntry> tail = entries.subList(i, entries.size());
      assertTrue(
          KarmaChain.verifyTail(tail, entries.get(i - 1).entryHash(), i), "tail from seq " + i);
    }
  }

  @Test
  void commitmentsOnBothSidesOfAnEpochBoundaryVerify() throws Exception {
    Chain chain = new Chain().ec(144 * 4 - 1, 3, 5.0).ec(144 * 4, 4, 7.5);

    assertTrue(KarmaChain.verify(chain.entries));
  }

  @Test
  void skippedEpochsDecayTheCarryOnceAsTheWriterDoes() throws Exception {
    Chain chain = new Chain().ec(144 * 2, 2, 5.0).en(144 * 2 + 3).ec(144 * 9 + 70, 9, 7.0);

    assertTrue(KarmaChain.verify(chain.entries));
  }

  @Test
  void energyWithinTheSignedRoundingVerifies() throws Exception {
    // 5 -> 2 used -> 6.5 -> 5 used -> 5.75 -> none used -> 7.875, signed as "7.88".
    Chain chain = new Chain().ec(144 * 3, 3, 5.0).en(144 * 3).en(144 * 3);
    chain.ec(144 * 4, 4, 6.5);
    for (int i = 0; i < 5; i++) {
      chain.en(144 * 4 + 1);
    }
    chain.ec(144 * 5, 5, 5.75).ec(144 * 6, 6, 7.88).ec(144 * 7, 7, 8.94);

    assertTrue(KarmaChain.verify(chain.entries));
  }

  @Test
  void energyOneHundredthAboveTheRuleVerifies() throws Exception {
    Chain chain = new Chain().ec(144 * 3, 3, 5.0).ec(144 * 4, 4, 7.51);

    assertTrue(KarmaChain.verify(chain.entries));
  }

  @Test
  void aTailsFirstCommitmentCannotBeRecomputedAndIsOnlyBounded() throws Exception {
    Chain chain = new Chain(5, key(9)).ec(144 * 6, 6, 10.0).en(144 * 6);

    assertTrue(KarmaChain.verifyTail(chain.entries, key(9), 5));
  }

  @Test
  void offlineBlockSourceFallsBackToTheStructuralChecks() throws Exception {
    WriterRun run = writerChainAcrossEpochs();
    BlockHeaderSource offline =
        new BlockHeaderSource() {
          @Override
          public BitcoinBlockReference getBlock(long height) {
            return null;
          }

          @Override
          public long getChainTipHeight() {
            return -1;
          }
        };

    assertTrue(KarmaChain.verify(run.writer.chain().entries(), offline));
  }

  // ---- forged chains: rejected ----

  @Test
  void commitmentsStackedAtOneHeightAreRejected() throws Exception {
    // Endorsements must sit in their commitment's epoch, so the stacked budget is spent at a
    // later height; each stacked commitment claims a full 10.
    Chain chain = new Chain().ec(144 * 5, 5, 5.0);
    for (int i = 0; i < 5; i++) {
      chain.en(144 * 5);
    }
    chain.ec(144 * 5, 6, 10.0).ec(144 * 5, 7, 10.0);
    for (int i = 0; i < 10; i++) {
      chain.en(144 * 7);
    }

    assertFalse(KarmaChain.verify(chain.entries));
  }

  @Test
  void commitmentWithTheWrongEpochForItsHeightIsRejected() throws Exception {
    assertFalse(KarmaChain.verify(new Chain().ec(144 * 5, 7, 5.0).entries));
    assertFalse(KarmaChain.verify(new Chain().ec(144 * 5 - 1, 5, 5.0).entries));
  }

  @Test
  void inflatedFirstEnergyIsRejected() throws Exception {
    Chain chain = new Chain().ec(144 * 5, 5, 10.0);
    for (int i = 0; i < 10; i++) {
      chain.en(144 * 5);
    }

    assertFalse(KarmaChain.verify(chain.entries));
  }

  @Test
  void energyAboveTheDecayRuleIsRejected() throws Exception {
    assertFalse(
        KarmaChain.verify(new Chain().ec(144 * 3, 3, 5.0).ec(144 * 4, 4, 10.0).entries),
        "expected 7.5");
    assertFalse(
        KarmaChain.verify(new Chain().ec(144 * 3, 3, 5.0).ec(144 * 4, 4, 7.52).entries),
        "beyond the rounding of the signed form");
  }

  @Test
  void energyBelowTheDecayRuleIsRejected() throws Exception {
    assertFalse(KarmaChain.verify(new Chain().ec(144 * 3, 3, 5.0).ec(144 * 4, 4, 6.0).entries));
  }

  @Test
  void selfEndorsementIsRejected() throws Exception {
    Chain chain = new Chain().ec(144 * 5, 5, 5.0).en(144 * 5, owner);

    assertFalse(KarmaChain.verify(chain.entries));
  }

  @Test
  void forgedTailsAreRejected() throws Exception {
    byte[] base = key(9);
    assertFalse(
        KarmaChain.verifyTail(new Chain(5, base).ec(144 * 6, 8, 5.0).entries, base, 5),
        "wrong epoch");
    assertFalse(
        KarmaChain.verifyTail(
            new Chain(5, base).ec(144 * 6, 6, 5.0).ec(144 * 7, 7, 10.0).entries, base, 5),
        "inflated energy after the first commitment");
    assertFalse(
        KarmaChain.verifyTail(
            new Chain(5, base).ec(144 * 6, 6, 5.0).en(144 * 6, owner).entries, base, 5),
        "self-endorsement");
  }

  /** A "tail" from seq 0 is the whole chain: its first commitment has nothing carried over. */
  @Test
  void aTailFromTheFirstEntryIsHeldToTheFullChainRules() throws Exception {
    Chain inflated = new Chain().ec(144 * 5, 5, 10.0);
    for (int i = 0; i < 10; i++) {
      inflated.en(144 * 5);
    }

    assertFalse(
        KarmaChain.verifyTail(inflated.entries, KarmaChainEntry.GENESIS_PREV_HASH, 0),
        "a published base of seq 0 must not lift the first commitment's energy");
    assertFalse(
        KarmaChain.verifyTail(new Chain(0, key(9)).ec(144 * 5, 5, 5.0).entries, key(9), 0),
        "seq 0 links to genesis");
    assertTrue(
        KarmaChain.verifyTail(
            new Chain().ec(144 * 5, 5, 5.0).en(144 * 5).entries,
            KarmaChainEntry.GENESIS_PREV_HASH,
            0));
  }

  @Test
  void chainRefusesToEndorseItsOwner() throws Exception {
    KarmaChain chain = new KarmaChain(owner);
    BitcoinBlockReference block = block(144 * 5);
    chain.commitEpoch(block, id.ed25519().getPrivate());

    assertThrows(
        IllegalStateException.class,
        () -> chain.endorse(owner, new byte[20], block, id.ed25519().getPrivate()));
  }

  @Test
  void blockHashThatDiffersFromBitcoinIsRejected() throws Exception {
    WriterRun run = writerChainAcrossEpochs();
    FakeBlocks other = new FakeBlocks(run.blocks.tip, (byte) 0x77);

    assertFalse(KarmaChain.verify(run.writer.chain().entries(), other));
  }

  @Test
  void heightsBeyondTheBitcoinTipAreRejected() throws Exception {
    WriterRun run = writerChainAcrossEpochs();
    List<KarmaChainEntry> entries = run.writer.chain().entries();
    long highest = entries.get(entries.size() - 1).blockHeight();
    FakeBlocks behind =
        new FakeBlocks(highest - KarmaConstants.EPOCH_TOLERANCE_BLOCKS - 1, (byte) 0x11);
    FakeBlocks justWithin =
        new FakeBlocks(highest - KarmaConstants.EPOCH_TOLERANCE_BLOCKS, (byte) 0x11);

    assertFalse(KarmaChain.verify(entries, behind));
    assertTrue(KarmaChain.verify(entries, justWithin));
  }

  // ---- helpers ----

  private static final class WriterRun {
    final KarmaChainWriter writer;
    final FakeBlocks blocks;

    WriterRun(KarmaChainWriter writer, FakeBlocks blocks) {
      this.writer = writer;
      this.blocks = blocks;
    }
  }

  /** Epochs 3, 4, 5, 8 (skipping 6 and 7) and 9, with 2, 5, 0, 3 and 1 endorsements. */
  private static WriterRun writerChainAcrossEpochs() throws Exception {
    FakeBlocks blocks = new FakeBlocks(0, (byte) 0x11);
    KarmaChainWriter writer = new KarmaChainWriter(id, blocks, new MemoryStore());
    long[] heights = {144 * 3 + 5, 144 * 4 + 1, 144 * 5 + 100, 144 * 8 + 7, 144 * 9 + 143};
    int[] endorsements = {2, 5, 0, 3, 1};
    int torrent = 0;
    for (int e = 0; e < heights.length; e++) {
      blocks.tip = heights[e];
      writer.commitEpochIfNeeded();
      for (int i = 0; i < endorsements[e]; i++) {
        byte[] infoHash = new byte[20];
        infoHash[0] = (byte) ++torrent;
        assertTrue(writer.endorseSourcePeer(PEER, infoHash), "epoch " + e + " endorsement " + i);
      }
    }
    return new WriterRun(writer, blocks);
  }

  private static int countCommitments(List<KarmaChainEntry> entries) {
    int n = 0;
    for (KarmaChainEntry e : entries) {
      if (e.kind() == KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
        n++;
      }
    }
    return n;
  }

  /** Builds signed chains entry by entry, including ones the writer would never produce. */
  private static final class Chain {
    final List<KarmaChainEntry> entries = new ArrayList<>();
    private long seq;
    private byte[] prev;
    private int torrent;

    Chain() {
      this(0, KarmaChainEntry.GENESIS_PREV_HASH.clone());
    }

    Chain(long baseSeq, byte[] basePrevHash) {
      this.seq = baseSeq;
      this.prev = basePrevHash.clone();
    }

    Chain ec(long height, long epoch, double energy) throws Exception {
      BitcoinBlockReference block = block(height);
      KarmaChainEntry unsigned =
          KarmaChainEntry.fromStoredFields(
              KarmaChainEntry.Kind.EPOCH_COMMITMENT,
              prev,
              seq,
              owner,
              0,
              height,
              block.hash(),
              epoch,
              energy,
              null,
              null,
              null,
              new byte[64]);
      Signature signer = IdentityKeys.softwareSignature("Ed25519");
      signer.initSign(id.ed25519().getPrivate());
      signer.update(unsigned.canonicalBytes());
      return add(
          KarmaChainEntry.fromStoredFields(
              KarmaChainEntry.Kind.EPOCH_COMMITMENT,
              prev,
              seq,
              owner,
              0,
              height,
              block.hash(),
              epoch,
              energy,
              null,
              null,
              null,
              signer.sign()));
    }

    Chain en(long height) {
      return en(height, PEER);
    }

    Chain en(long height, byte[] peer) {
      byte[] infoHash = new byte[20];
      infoHash[0] = (byte) ++torrent;
      infoHash[1] = (byte) seq;
      return add(
          KarmaChainEntry.createEndorsement(
              prev, seq, owner, block(height), peer, infoHash, 1, id.ed25519().getPrivate()));
    }

    private Chain add(KarmaChainEntry entry) {
      entries.add(entry);
      prev = entry.entryHash();
      seq++;
      return this;
    }
  }

  private static BitcoinBlockReference block(long height) {
    return new BitcoinBlockReference(height, blockHash(height, (byte) 0x11));
  }

  private static byte[] blockHash(long height, byte salt) {
    byte[] hash = new byte[32];
    hash[0] = salt;
    for (int i = 0; i < 8; i++) {
      hash[24 + i] = (byte) (height >>> (8 * (7 - i)));
    }
    return hash;
  }

  private static byte[] key(int n) {
    byte[] key = new byte[32];
    key[0] = (byte) n;
    key[31] = 1;
    return key;
  }

  /**
   * Bitcoin as the tests see it: a tip and a hash per height (salted, to fake a different chain).
   */
  private static final class FakeBlocks implements BlockHeaderSource {
    volatile long tip;
    private final byte salt;
    final Map<Long, Integer> lookups = Collections.synchronizedMap(new HashMap<>());

    FakeBlocks(long tip, byte salt) {
      this.tip = tip;
      this.salt = salt;
    }

    @Override
    public BitcoinBlockReference getBlock(long height) {
      lookups.merge(height, 1, Integer::sum);
      return height < 0 || height > tip
          ? null
          : new BitcoinBlockReference(height, blockHash(height, salt));
    }

    @Override
    public long getChainTipHeight() {
      return tip;
    }
  }

  private static final class MemoryStore implements KarmaChainStore {
    final List<KarmaChainEntry> entries = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void append(KarmaChainEntry entry) {
      entries.add(entry);
    }

    @Override
    public KarmaChain loadChain(byte[] ownerPub) {
      return new KarmaChain(ownerPub);
    }

    @Override
    public void close() {}
  }
}
