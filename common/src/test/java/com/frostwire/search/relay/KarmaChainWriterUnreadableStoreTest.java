/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * When the store can't hand the writer its chain (it can't be read, or a rejected chain can't be
 * set aside, say because the database is busy), the writer must not throw: its constructor runs
 * during relay start-up, which would stop. It must not write either, because its appends would
 * overwrite the chain it couldn't load. The next start tries again.
 */
class KarmaChainWriterUnreadableStoreTest {

  private static final byte[] PEER = new byte[32];
  private static final byte[] INFO_HASH = new byte[20];

  static {
    PEER[0] = 7;
    INFO_HASH[0] = 9;
  }

  @Test
  void theWriterStartsAndWritesNothing() throws Exception {
    UnreadableStore store = new UnreadableStore();

    KarmaChainWriter writer =
        new KarmaChainWriter(IdentityKeys.generate(4), new FixedTip(144L * 3 + 10), store);
    writer.commitEpochIfNeeded();

    assertFalse(writer.canEndorseSourcePeer(PEER, INFO_HASH));
    assertFalse(writer.endorseSourcePeer(PEER, INFO_HASH));
    assertEquals(List.of(), store.appended);
    assertEquals(List.of(), writer.chain().entries());
  }

  private static final class UnreadableStore implements KarmaChainStore {
    final List<KarmaChainEntry> appended = new ArrayList<>();

    @Override
    public void append(KarmaChainEntry entry) {
      appended.add(entry);
    }

    @Override
    public KarmaChain loadChain(byte[] ownerPub) {
      return new KarmaChain(ownerPub);
    }

    @Override
    public KarmaChain loadChainForWriting(byte[] ownerPub) {
      throw new IllegalStateException("database is locked");
    }

    @Override
    public void close() {}
  }

  private static final class FixedTip implements BlockHeaderSource {
    private final long tip;

    FixedTip(long tip) {
      this.tip = tip;
    }

    @Override
    public BitcoinBlockReference getBlock(long height) {
      byte[] hash = new byte[32];
      hash[0] = (byte) height;
      hash[1] = (byte) (height >>> 8);
      return new BitcoinBlockReference(height, hash);
    }

    @Override
    public long getChainTipHeight() {
      return tip;
    }
  }
}
