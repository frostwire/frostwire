/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * IceBridge white paper §11.3: the writer anchored karma entries to the Bitcoin tip, the block a
 * reorganization replaces most often, so a published chain could name a block that is no longer in
 * the chain. It now anchors {@link KarmaConstants#ANCHOR_DEPTH_BLOCKS} below the tip.
 */
class KarmaChainWriterAnchorDepthTest {

  private static final byte[] PEER = new byte[32];
  private static final byte[] INFO_HASH = new byte[20];

  static {
    PEER[0] = 7;
  }

  @TempDir Path tempDir;

  private KarmaChainTable table;
  private IdentityKeys identity;
  private AllBlocks blocks;

  @BeforeEach
  void setUp() throws Exception {
    table = KarmaChainTable.open(tempDir.resolve("karma-depth.db").toFile());
    identity = IdentityKeys.generate(4);
    blocks = new AllBlocks();
  }

  @AfterEach
  void tearDown() {
    table.close();
  }

  @Test
  void commitmentIsAnchoredBelowTheTip() {
    blocks.tip = 150;
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, table);

    writer.commitEpochIfNeeded();

    assertEquals(1, writer.chain().entries().size());
    assertEquals(150 - KarmaConstants.ANCHOR_DEPTH_BLOCKS, writer.chain().head().blockHeight());
    assertEquals(1L, writer.chain().currentEpoch());
    assertNoBlockAskedAbove(150 - KarmaConstants.ANCHOR_DEPTH_BLOCKS);
  }

  @Test
  void endorsementIsAnchoredBelowTheTip() {
    blocks.tip = 200;
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, table);

    assertTrue(writer.endorseSourcePeer(PEER, INFO_HASH));

    for (KarmaChainEntry e : writer.chain().entries()) {
      assertEquals(200 - KarmaConstants.ANCHOR_DEPTH_BLOCKS, e.blockHeight(), e.kind().name());
    }
    assertNoBlockAskedAbove(200 - KarmaConstants.ANCHOR_DEPTH_BLOCKS);
  }

  @Test
  void aNewEpochIsNotCommittedUntilItsFirstBlockIsDeepEnough() {
    blocks.tip = 140;
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, table);
    writer.commitEpochIfNeeded();
    assertEquals(0L, writer.chain().currentEpoch());

    // Block 144 opens epoch 1, but with the tip at 145 it has only two confirmations.
    blocks.tip = 145;
    writer.commitEpochIfNeeded();
    assertEquals(0L, writer.chain().currentEpoch(), "epoch 1's first block is too recent");
    assertNoBlockAskedAbove(145 - KarmaConstants.ANCHOR_DEPTH_BLOCKS);

    blocks.tip = 144 + KarmaConstants.ANCHOR_DEPTH_BLOCKS;
    writer.commitEpochIfNeeded();
    assertEquals(1L, writer.chain().currentEpoch());
    assertEquals(144L, writer.chain().head().blockHeight());
  }

  @Test
  void aTipShallowerThanTheDepthAnchorsNothing() {
    blocks.tip = KarmaConstants.ANCHOR_DEPTH_BLOCKS - 1;
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, table);

    writer.commitEpochIfNeeded();
    assertFalse(writer.endorseSourcePeer(PEER, INFO_HASH));

    assertEquals(0, writer.chain().entries().size());
    assertTrue(blocks.asked.isEmpty(), "asked for " + blocks.asked);
  }

  @Test
  void anAnchoredChainVerifies() {
    blocks.tip = 300;
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, table);
    assertTrue(writer.endorseSourcePeer(PEER, INFO_HASH));

    assertTrue(KarmaChain.verify(writer.chain().entries(), blocks));
  }

  private void assertNoBlockAskedAbove(long height) {
    for (long h : blocks.asked) {
      assertTrue(h <= height, "asked for block " + h + " above " + height);
    }
  }

  /** Knows every block up to its tip, and records which heights it was asked for. */
  private static final class AllBlocks implements BlockHeaderSource {
    volatile long tip = -1;
    final List<Long> asked = new ArrayList<>();

    @Override
    public synchronized BitcoinBlockReference getBlock(long height) {
      asked.add(height);
      if (height < 0 || height > tip) {
        return null;
      }
      byte[] hash = new byte[32];
      for (int i = 0; i < 8; i++) {
        hash[i] = (byte) (height >>> (8 * (7 - i)));
      }
      return new BitcoinBlockReference(height, hash);
    }

    @Override
    public long getChainTipHeight() {
      return tip;
    }
  }
}
