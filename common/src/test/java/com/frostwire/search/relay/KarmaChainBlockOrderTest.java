/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link KarmaChain#verify} rejects a chain whose block heights go down, so the chain must never
 * sign an entry at a block below its head. The writer reads the Bitcoin tip before it takes its
 * lock, and the tip can go backwards (an API that lags behind another, or the epoch commit
 * scheduler appending a newer block in between): an entry signed there made the persisted chain
 * fail verification on the next start, and the user's karma was thrown away.
 */
class KarmaChainBlockOrderTest {

  private static final long EPOCH_3 = 144L * 3;

  private static KeyPair keyPair;
  private static byte[] pubRaw;

  @BeforeAll
  static void setUp() throws Exception {
    keyPair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    pubRaw = IdentityRecord.extractRawEd25519(keyPair.getPublic());
  }

  @Test
  void anEndorsementBelowTheHeadsBlockIsRefused() {
    KarmaChain chain = new KarmaChain(pubRaw);
    chain.commitEpoch(block(EPOCH_3 + 20), keyPair.getPrivate());

    assertThrows(
        IllegalStateException.class,
        () -> chain.endorse(peer(1), infoHash(1), block(EPOCH_3 + 10), keyPair.getPrivate()));

    assertEquals(1, chain.entries().size());
    assertTrue(KarmaChain.verify(chain.entries()));
  }

  @Test
  void anEndorsementAtTheHeadsBlockIsAccepted() {
    KarmaChain chain = new KarmaChain(pubRaw);
    chain.commitEpoch(block(EPOCH_3 + 20), keyPair.getPrivate());

    chain.endorse(peer(1), infoHash(1), block(EPOCH_3 + 20), keyPair.getPrivate());

    assertTrue(KarmaChain.verify(chain.entries()));
  }

  @Test
  void aCommitmentBelowTheHeadsBlockIsRefused() {
    KarmaChain chain = new KarmaChain(pubRaw);
    chain.commitEpoch(block(EPOCH_3), keyPair.getPrivate());
    // An endorsement at a later epoch's block, made without committing that epoch first.
    chain.endorse(peer(1), infoHash(1), block(144L * 5), keyPair.getPrivate());

    assertThrows(
        IllegalStateException.class, () -> chain.commitEpoch(block(144L * 4), keyPair.getPrivate()));

    assertEquals(2, chain.entries().size());
    assertTrue(KarmaChain.verify(chain.entries()));
  }

  @Test
  void theWriterKeepsAVerifiableChainWhenTheTipGoesBackwards() throws Exception {
    IdentityKeys identity = IdentityKeys.generate(4);
    AtomicLong tip = new AtomicLong(EPOCH_3 + 20);
    MemoryStore store = new MemoryStore();
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocksAt(tip), store);
    writer.commitEpochIfNeeded();

    tip.set(EPOCH_3 + 10);
    writer.endorseSourcePeer(peer(1), infoHash(1));

    assertTrue(KarmaChain.verify(store.entries), "the stored chain must still verify");
    assertEquals(store.entries, writer.chain().entries());
    KarmaChain reloaded = store.loadChain(identity.ed25519PubRaw());
    assertEquals(store.entries.size(), reloaded.entries().size(), "nothing lost on reload");
  }

  @Test
  void theWriterEndorsesAgainOnceTheTipCatchesUp() throws Exception {
    IdentityKeys identity = IdentityKeys.generate(4);
    AtomicLong tip = new AtomicLong(EPOCH_3 + 20);
    MemoryStore store = new MemoryStore();
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocksAt(tip), store);
    writer.commitEpochIfNeeded();
    tip.set(EPOCH_3 + 10);
    boolean first = writer.endorseSourcePeer(peer(1), infoHash(1));

    tip.set(EPOCH_3 + 21);
    boolean second = writer.endorseSourcePeer(peer(1), infoHash(1));

    assertFalse(first && second, "one pair is endorsed at most once");
    assertTrue(writer.chain().hasEndorsement(peer(1), infoHash(1)));
    assertTrue(KarmaChain.verify(store.entries));
  }

  private static BlockHeaderSource blocksAt(AtomicLong tip) {
    return new BlockHeaderSource() {
      @Override
      public BitcoinBlockReference getBlock(long height) {
        return block(height);
      }

      @Override
      public long getChainTipHeight() {
        return tip.get();
      }
    };
  }

  private static BitcoinBlockReference block(long height) {
    byte[] hash = new byte[32];
    for (int i = 0; i < 32; i++) {
      hash[i] = (byte) ((height + i) & 0xff);
    }
    return new BitcoinBlockReference(height, hash);
  }

  private static byte[] peer(int seed) {
    byte[] b = new byte[32];
    b[0] = (byte) seed;
    b[1] = (byte) (seed + 1);
    return b;
  }

  private static byte[] infoHash(int seed) {
    byte[] b = new byte[20];
    b[0] = (byte) seed;
    b[1] = (byte) (seed + 1);
    return b;
  }

  /** Keeps appended entries in memory and loads them the way the real stores do. */
  private static final class MemoryStore implements KarmaChainStore {
    final List<KarmaChainEntry> entries = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void append(KarmaChainEntry entry) {
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
