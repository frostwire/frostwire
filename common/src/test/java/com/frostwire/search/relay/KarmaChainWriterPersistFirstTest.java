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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * KarmaChainWriter must persist an entry before adding it to the in-memory chain: a failed store
 * write must leave memory and the database holding the same entries (frostwire/frostwire#1413).
 */
class KarmaChainWriterPersistFirstTest {

  private static final byte[] PEER = new byte[32];
  private static final byte[] INFO_HASH = new byte[20];

  static {
    PEER[0] = 7;
    INFO_HASH[0] = 9;
  }

  private IdentityKeys identity;
  private BlockHeaderSource blocks;

  @BeforeEach
  void setUp() throws Exception {
    identity = IdentityKeys.generate(4);
    blocks = fixedTip(144L * 3 + 10);
  }

  @Test
  void failedCommitmentWriteLeavesTheChainEmpty() {
    FailingStore store = new FailingStore();
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, store);
    store.failNextAppends.set(1);

    writer.commitEpochIfNeeded();

    assertEquals(List.of(), store.entries);
    assertEquals(store.entries, writer.chain().entries());
    assertEquals(-1, writer.chain().currentEpoch());
  }

  @Test
  void failedCommitmentWriteIsRetriedOnTheNextCommit() {
    FailingStore store = new FailingStore();
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, store);
    store.failNextAppends.set(1);
    writer.commitEpochIfNeeded();

    writer.commitEpochIfNeeded();

    assertEquals(1, store.entries.size());
    assertEquals(store.entries, writer.chain().entries());
    assertEquals(3, writer.chain().currentEpoch());
  }

  @Test
  void failedEndorsementWriteKeepsMemoryAndStoreInStep() {
    FailingStore store = new FailingStore();
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, store);
    writer.commitEpochIfNeeded();
    int energy = writer.chain().availableEnergy();
    store.failNextAppends.set(1);

    assertFalse(writer.endorseSourcePeer(PEER, INFO_HASH));

    assertEquals(store.entries, writer.chain().entries());
    assertFalse(writer.chain().hasEndorsement(PEER, INFO_HASH));
    assertEquals(energy, writer.chain().availableEnergy(), "no energy spent on a lost entry");
    assertTrue(writer.canEndorseSourcePeer(PEER, INFO_HASH));
  }

  @Test
  void endorsementAfterAFailedWriteLinksToThePersistedHead() {
    FailingStore store = new FailingStore();
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, store);
    writer.commitEpochIfNeeded();
    store.failNextAppends.set(1);
    writer.endorseSourcePeer(PEER, INFO_HASH);

    assertTrue(writer.endorseSourcePeer(PEER, INFO_HASH));

    assertEquals(2, store.entries.size());
    assertEquals(store.entries, writer.chain().entries());
    assertTrue(KarmaChain.verify(store.entries), "the stored chain still verifies");
    KarmaChain reloaded = KarmaChain.load(identity.ed25519PubRaw(), store.entries);
    assertEquals(writer.chain().availableEnergy(), reloaded.availableEnergy());
  }

  @Test
  void failedEndorsementWriteInANewEpochKeepsTheCommitmentThatWasStored() {
    FailingStore store = new FailingStore();
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, store);
    // The commitment is stored, the endorsement after it isn't.
    store.failAppendNumber.set(2);

    assertFalse(writer.endorseSourcePeer(PEER, INFO_HASH));

    assertEquals(1, store.entries.size());
    assertEquals(store.entries, writer.chain().entries());
    assertEquals(3, writer.chain().currentEpoch());
  }

  private static BlockHeaderSource fixedTip(long height) {
    return new BlockHeaderSource() {
      @Override
      public BitcoinBlockReference getBlock(long h) {
        byte[] hash = new byte[32];
        hash[0] = (byte) h;
        hash[1] = (byte) (h >>> 8);
        return new BitcoinBlockReference(h, hash);
      }

      @Override
      public long getChainTipHeight() {
        return height;
      }
    };
  }

  /** In-memory store whose appends can be made to fail, like a full disk or a locked database. */
  private static final class FailingStore implements KarmaChainStore {
    final List<KarmaChainEntry> entries = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger failNextAppends = new AtomicInteger();
    final AtomicInteger failAppendNumber = new AtomicInteger();
    private final AtomicInteger appendCalls = new AtomicInteger();

    @Override
    public void append(KarmaChainEntry entry) {
      int call = appendCalls.incrementAndGet();
      if (failNextAppends.getAndUpdate(n -> Math.max(0, n - 1)) > 0
          || call == failAppendNumber.get()) {
        throw new IllegalStateException("disk full");
      }
      entries.add(entry);
    }

    @Override
    public KarmaChain loadChain(byte[] ownerPub) {
      KarmaChain loaded = KarmaChain.load(ownerPub, new ArrayList<>(entries));
      return loaded != null ? loaded : new KarmaChain(ownerPub);
    }

    @Override
    public void close() {}
  }
}
