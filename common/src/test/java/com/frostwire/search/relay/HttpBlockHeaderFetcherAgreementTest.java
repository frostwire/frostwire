/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.util.Hex;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * IceBridge white paper §11.3: block hashes came from Blockstream, or from mempool.space when
 * Blockstream failed, so one explorer that was wrong, compromised or down to the other's answer
 * decided what the karma chain was anchored to. A block is now used only when both explorers report
 * the same hash, and nothing is anchored when one is unreachable. The explorers here are fakes; no
 * network is used.
 */
class HttpBlockHeaderFetcherAgreementTest {

  private static final String BLOCKSTREAM = "https://blockstream.info/api/";
  private static final String MEMPOOL = "https://mempool.space/api/";
  private static final byte[] PEER = new byte[32];

  static {
    PEER[0] = 9;
  }

  @TempDir Path tempDir;

  private KarmaChainTable table;
  private IdentityKeys identity;
  private Explorer blockstream;
  private Explorer mempool;
  private final List<String> requested = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    table = KarmaChainTable.open(tempDir.resolve("karma-agreement.db").toFile());
    identity = IdentityKeys.generate(4);
    blockstream = new Explorer();
    mempool = new Explorer();
  }

  @AfterEach
  void tearDown() {
    table.close();
  }

  @Test
  void agreeingSourcesAnchorADeepBlock() {
    setTips(1000);
    KarmaChainWriter writer = new KarmaChainWriter(identity, fetcher(), table);

    writer.commitEpochIfNeeded();

    assertEquals(1, writer.chain().entries().size());
    KarmaChainEntry commitment = writer.chain().head();
    long anchor = 1000 - KarmaConstants.ANCHOR_DEPTH_BLOCKS;
    assertEquals(anchor, commitment.blockHeight());
    assertArrayEquals(Explorer.hash(anchor, (byte) 0), commitment.blockHash());
  }

  @Test
  void disagreeingSourcesAnchorNothing() {
    setTips(1000);
    mempool.salt = 1;
    HttpBlockHeaderFetcher fetcher = fetcher();
    KarmaChainWriter writer = new KarmaChainWriter(identity, fetcher, table);

    writer.commitEpochIfNeeded();
    assertFalse(writer.endorseSourcePeer(PEER, new byte[20]));

    assertTrue(writer.chain().entries().isEmpty());
    assertNull(fetcher.getBlock(990));
  }

  @Test
  void eitherSourceDownAnchorsNothingUntilItIsBack() {
    setTips(1000);
    for (Explorer down : new Explorer[] {blockstream, mempool}) {
      down.down = true;
      HttpBlockHeaderFetcher fetcher = fetcher();
      KarmaChainWriter writer = new KarmaChainWriter(identity, fetcher, table);

      writer.commitEpochIfNeeded();
      assertFalse(writer.endorseSourcePeer(PEER, new byte[20]));

      assertTrue(writer.chain().entries().isEmpty());
      assertEquals(-1, fetcher.getChainTipHeight());
      assertNull(fetcher.getBlock(990));
      down.down = false;
    }

    // The next scheduled round, with both sources back, anchors.
    KarmaChainWriter writer = new KarmaChainWriter(identity, fetcher(), table);
    writer.commitEpochIfNeeded();
    assertEquals(1, writer.chain().entries().size());
  }

  @Test
  void aSourceThatDoesNotKnowTheBlockAnchorsNothing() {
    setTips(1000);
    mempool.forgets = 1000 - KarmaConstants.ANCHOR_DEPTH_BLOCKS;
    KarmaChainWriter writer = new KarmaChainWriter(identity, fetcher(), table);

    writer.commitEpochIfNeeded();

    assertTrue(writer.chain().entries().isEmpty());
  }

  @Test
  void aBlockTooRecentForANewEpochIsNotAnchored() {
    setTips(140);
    KarmaChainWriter writer = new KarmaChainWriter(identity, fetcher(), table);
    writer.commitEpochIfNeeded();
    assertEquals(0L, writer.chain().currentEpoch());

    // Both sources agree that block 144 opens epoch 1, but it has only three confirmations.
    setTips(146);
    requested.clear();
    writer.commitEpochIfNeeded();

    assertEquals(0L, writer.chain().currentEpoch());
    for (String url : requested) {
      assertFalse(url.endsWith("/block-height/144"), "asked for " + url);
    }
  }

  @Test
  void tipsAFewBlocksApartGiveTheLowerTipAndFartherApartGiveNone() {
    blockstream.tip = 1000;
    mempool.tip = 1000 - HttpBlockHeaderFetcher.MAX_TIP_SPREAD_BLOCKS;
    assertEquals(mempool.tip, fetcher().getChainTipHeight());

    mempool.tip = 1000 - HttpBlockHeaderFetcher.MAX_TIP_SPREAD_BLOCKS - 1;
    assertEquals(-1, fetcher().getChainTipHeight());
  }

  @Test
  void aGarbageAnswerFromOneSourceIsNotAgreement() {
    setTips(1000);
    mempool.garbage = true;
    HttpBlockHeaderFetcher fetcher = fetcher();

    assertEquals(-1, fetcher.getChainTipHeight());
    assertNull(fetcher.getBlock(990));
  }

  private void setTips(long tip) {
    blockstream.tip = tip;
    mempool.tip = tip;
  }

  // The cache: a hash is remembered for good, so only blocks a reorganization can't replace, and
  // only hashes both sources agreed on, may be in it.

  @Test
  void aBlockNearTheTipIsNotCachedSoAReorganizationIsSeen() {
    setTips(1000);
    File dir = tempDir.resolve("reorg").toFile();
    HttpBlockHeaderFetcher fetcher = fetcher(dir);
    assertEquals(1000, fetcher.getChainTipHeight());
    assertArrayEquals(Explorer.hash(999, (byte) 0), fetcher.getBlock(999).hash());

    // Both sources now report the block that replaced it.
    blockstream.salt = 2;
    mempool.salt = 2;
    assertArrayEquals(Explorer.hash(999, (byte) 2), fetcher.getBlock(999).hash());
    assertArrayEquals(Explorer.hash(999, (byte) 2), fetcher(dir).getBlock(999).hash());
  }

  @Test
  void aDeepAgreedBlockIsCached() {
    setTips(1000);
    File dir = tempDir.resolve("deep").toFile();
    HttpBlockHeaderFetcher fetcher = fetcher(dir);
    long deep = 1000 - KarmaConstants.ANCHOR_DEPTH_BLOCKS;
    assertEquals(1000, fetcher.getChainTipHeight());
    assertNotNull(fetcher.getBlock(deep));

    blockstream.down = true;
    mempool.down = true;
    assertArrayEquals(Explorer.hash(deep, (byte) 0), fetcher.getBlock(deep).hash());
    assertArrayEquals(Explorer.hash(deep, (byte) 0), fetcher(dir).getBlock(deep).hash());
  }

  @Test
  void theSingleSourceCacheOfEarlierVersionsIsNotUsed() throws Exception {
    setTips(1000);
    File dir = tempDir.resolve("legacy").toFile();
    assertTrue(dir.mkdirs());
    File legacy = new File(dir, "block-headers.cache");
    java.nio.file.Files.write(
        legacy.toPath(),
        ("990 " + Hex.encode(Explorer.hash(990, (byte) 7)) + "\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    HttpBlockHeaderFetcher fetcher = fetcher(dir);

    assertArrayEquals(Explorer.hash(990, (byte) 0), fetcher.getBlock(990).hash());
    assertFalse(legacy.exists());
  }

  /** A fresh fetcher, with its own empty cache, reading the fake explorers. */
  private HttpBlockHeaderFetcher fetcher() {
    return fetcher(tempDir.resolve("blocks-" + System.nanoTime()).toFile());
  }

  /** A fetcher with its cache in {@code cacheDir}, reading the fake explorers. */
  private HttpBlockHeaderFetcher fetcher(File cacheDir) {
    return new HttpBlockHeaderFetcher(
        cacheDir,
        url -> {
          requested.add(url);
          if (url.startsWith(BLOCKSTREAM)) {
            return blockstream.get(url.substring(BLOCKSTREAM.length()));
          }
          if (url.startsWith(MEMPOOL)) {
            return mempool.get(url.substring(MEMPOOL.length()));
          }
          throw new IOException("unexpected URL " + url);
        });
  }

  /** An Esplora-style explorer: a tip, and a hash for every block up to it. */
  private static final class Explorer {
    volatile long tip = -1;
    volatile byte salt;
    volatile boolean down;
    volatile boolean garbage;
    volatile long forgets = -1;

    String get(String path) throws IOException {
      if (down) {
        throw new IOException("connect timed out");
      }
      if (garbage) {
        return "<html>502 Bad Gateway</html>";
      }
      if (path.equals("blocks/tip/height")) {
        return Long.toString(tip);
      }
      if (path.startsWith("block-height/")) {
        long height = Long.parseLong(path.substring("block-height/".length()));
        if (height > tip || height == forgets) {
          throw new IOException("404 Block not found");
        }
        return Hex.encode(hash(height, salt));
      }
      throw new IOException("404 " + path);
    }

    static byte[] hash(long height, byte salt) {
      byte[] hash = new byte[32];
      hash[0] = salt;
      for (int i = 0; i < 8; i++) {
        hash[24 + i] = (byte) (height >>> (8 * (7 - i)));
      }
      return hash;
    }
  }
}
