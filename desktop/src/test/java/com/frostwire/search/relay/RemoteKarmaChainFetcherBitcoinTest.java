/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.frostwire.jlibtorrent.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * A fetched karma chain is checked against Bitcoin when the fetcher has a block source, and still
 * verifies offline when the source knows nothing (frostwire/frostwire#1415).
 */
class RemoteKarmaChainFetcherBitcoinTest {

  @Test
  void chainNamingABlockBitcoinDoesntHaveIsRejected() throws Exception {
    Published published = publish();
    Blocks otherChain = new Blocks(published.height, (byte) 0x55);
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(published.source(), otherChain);

    assertNull(fetcher.fetchChain(published.owner));
    fetcher.close();
  }

  @Test
  void chainBeyondTheBitcoinTipIsRejected() throws Exception {
    Published published = publish();
    Blocks behind =
        new Blocks(published.height - KarmaConstants.EPOCH_TOLERANCE_BLOCKS - 1, (byte) 0x11);
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(published.source(), behind);

    assertNull(fetcher.fetchChain(published.owner));
    fetcher.close();
  }

  @Test
  void chainMatchingBitcoinIsAccepted() throws Exception {
    Published published = publish();
    RemoteKarmaChainFetcher fetcher =
        new RemoteKarmaChainFetcher(published.source(), new Blocks(published.height, (byte) 0x11));

    assertNotNull(fetcher.fetchChain(published.owner));
    fetcher.close();
  }

  @Test
  void offlineBlockSourceStillAcceptsAValidChain() throws Exception {
    Published published = publish();
    RemoteKarmaChainFetcher fetcher =
        new RemoteKarmaChainFetcher(published.source(), new Blocks(-1, (byte) 0x11));

    assertNotNull(fetcher.fetchChain(published.owner));
    fetcher.close();
  }

  @Test
  void tipIsReusedForAMinute() {
    AtomicInteger asked = new AtomicInteger();
    AtomicLong now = new AtomicLong();
    BlockHeaderSource counting =
        new BlockHeaderSource() {
          @Override
          public BitcoinBlockReference getBlock(long height) {
            return null;
          }

          @Override
          public long getChainTipHeight() {
            return asked.incrementAndGet() == 1 ? -1 : 900_000L;
          }
        };
    TipCachingBlockHeaderSource cached =
        new TipCachingBlockHeaderSource(counting, 60_000, now::get);

    assertEquals(-1, cached.getChainTipHeight(), "unknown tip");
    assertEquals(900_000L, cached.getChainTipHeight(), "an unknown tip isn't remembered");
    now.addAndGet(59_000_000_000L);
    assertEquals(900_000L, cached.getChainTipHeight());
    assertEquals(2, asked.get(), "reused within the minute");
    now.addAndGet(2_000_000_000L);
    cached.getChainTipHeight();
    assertEquals(3, asked.get(), "asked again after it");
  }

  private static final class Published {
    byte[] owner;
    Entry manifest;
    long height;

    KarmaChainSource source() {
      return pub -> java.util.Arrays.equals(pub, owner) ? manifest : null;
    }
  }

  /** A real writer's chain at one Bitcoin height, as the publisher publishes it. */
  private static Published publish() throws Exception {
    Published p = new Published();
    p.height = 144L * 6000 + 17;
    Blocks blocks = new Blocks(p.height, (byte) 0x11);
    IdentityKeys identity = IdentityKeys.generate(0);
    p.owner = identity.ed25519PubRaw();
    KarmaChainWriter writer = new KarmaChainWriter(identity, blocks, new Store());
    byte[] peer = new byte[32];
    peer[0] = 3;
    writer.endorseSourcePeer(peer, new byte[20]);
    p.manifest = new KarmaChainPublisher(writer, identity).buildManifest(writer.chain().entries());
    writer.close();
    return p;
  }

  private static final class Blocks implements BlockHeaderSource {
    private final long tip;
    private final byte salt;

    Blocks(long tip, byte salt) {
      this.tip = tip;
      this.salt = salt;
    }

    @Override
    public BitcoinBlockReference getBlock(long height) {
      if (tip < 0 || height > tip) {
        return null;
      }
      byte[] hash = new byte[32];
      hash[0] = salt;
      hash[31] = (byte) height;
      return new BitcoinBlockReference(height, hash);
    }

    @Override
    public long getChainTipHeight() {
      return tip;
    }
  }

  private static final class Store implements KarmaChainStore {
    private final List<KarmaChainEntry> entries = new ArrayList<>();

    @Override
    public synchronized void append(KarmaChainEntry entry) {
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
