/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.util.Bencode;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The publisher puts the chain's pages (frostwire/frostwire#1416): every non-empty chain, a lone
 * epoch commitment included, under the head salt and {@code frostwire-karma-v1/<n>}, each within
 * the DHT value cap.
 */
class KarmaChainPublisherTest {

  private static final int LOW_DIFFICULTY = 4;

  /** Remote chains can't name blocks from before karma existed. */
  private static final long FIRST = KarmaConstants.FIRST_KARMA_BLOCK_HEIGHT;

  private static final byte[] FAKE_PEER_PUB =
      new byte[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
      };
  private static final byte[] FAKE_INFO_HASH = new byte[20];

  @TempDir Path tempDir;

  private KarmaChainTable table;
  private IdentityKeys identity;
  private FakeBlockSource blockSource;
  private KarmaChainWriter writer;
  private KarmaChainPublisher publisher;

  @BeforeEach
  void setUp() throws Exception {
    File dbFile = tempDir.resolve("karma-test.db").toFile();
    table = KarmaChainTable.open(dbFile);
    identity = IdentityKeys.generate(LOW_DIFFICULTY);
    blockSource = new FakeBlockSource().withAnchor(FIRST).withBlock(FIRST, hashForHeight(FIRST));
    writer = new KarmaChainWriter(identity, blockSource, table);
    publisher = new KarmaChainPublisher(writer, identity);
  }

  @AfterEach
  void tearDown() {
    if (table != null) {
      table.close();
    }
  }

  @Test
  void constructorRejectsNulls() {
    assertThrows(IllegalArgumentException.class, () -> new KarmaChainPublisher(null, identity));
    assertThrows(IllegalArgumentException.class, () -> new KarmaChainPublisher(writer, null));
  }

  @Test
  void buildManifestReturnsNullForEmptyChain() {
    assertNull(publisher.buildManifest(null));
    assertNull(publisher.buildManifest(java.util.Collections.emptyList()));
  }

  @Test
  void publishIfNeededReturnsZeroForEmptyChainOrNoSession() {
    assertEquals(0, publisher.publishIfNeeded(new RecordingSession()));
    writer.commitEpochIfNeeded();
    assertEquals(0, publisher.publishIfNeeded(null));
  }

  @Test
  void aLoneEpochCommitmentIsPublished() {
    writer.commitEpochIfNeeded();
    RecordingSession session = new RecordingSession();

    assertEquals(1, publisher.publishIfNeeded(session), "an EC-only chain must not expire");

    assertEquals(1, session.puts.size());
    assertEquals(KarmaConstants.BEP46_SALT_KARMA, session.salt(0));
    assertEquals(writer.chain().entries(), read(session.value(0), null));
  }

  @Test
  void everyPageIsPutUnderItsSaltWithinTheCap() {
    growAcrossEpochs(16);
    List<KarmaChainEntry> chain = writer.chain().entries();
    RecordingSession session = new RecordingSession();

    int published = publisher.publishIfNeeded(session);

    assertTrue(session.puts.size() > 1, "a long chain needs older pages");
    assertEquals(KarmaConstants.BEP46_SALT_KARMA, session.salt(0));
    for (int i = 1; i < session.puts.size(); i++) {
      assertEquals(KarmaConstants.BEP46_SALT_KARMA + "/" + i, session.salt(i));
    }
    for (Object[] put : session.puts) {
      assertArrayEquals(identity.ed25519PubRaw(), (byte[]) put[0]);
      assertTrue(((Entry) put[2]).bencode().length <= KarmaChainPublisher.MAX_MANIFEST_BYTES);
    }
    List<KarmaChainEntry> read = read(session.value(0), session::olderPage);
    assertNotNull(read);
    assertTrue(read.size() > 4, "the pages hold more than a version 1 manifest's two entries");
    assertTrue(read.size() <= published);
    assertEquals(chain.subList(chain.size() - read.size(), chain.size()), read);
  }

  @Test
  void theHeadNamesTheSnapshotItWasBuiltFrom() {
    writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, FAKE_INFO_HASH);
    List<KarmaChainEntry> snapshot = writer.chain().entries();
    // The chain grows between the publisher's snapshot and its pages.
    writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, distinctInfoHash(1));

    Map<String, Object> head =
        Bencode.decodeDictionary(publisher.buildManifest(snapshot).bencode());

    assertEquals((long) KarmaChainPublisher.MANIFEST_VERSION, head.get("v"));
    assertEquals((long) snapshot.size(), head.get("len"));
    assertArrayEquals(snapshot.get(snapshot.size() - 1).entryHash(), (byte[]) head.get("head"));
    assertEquals(0L, head.get("pg"));
  }

  // --- helpers ---

  private void growAcrossEpochs(int epochs) {
    for (int i = 0; i < epochs; i++) {
      long tip = FIRST + 144L * i;
      blockSource.withAnchor(tip).withBlock(tip, hashForHeight(tip));
      writer.onDownloadCompletedFromPeer(FAKE_PEER_PUB, distinctInfoHash(i));
    }
  }

  private List<KarmaChainEntry> read(byte[] head, IntFunction<byte[]> older) {
    return KarmaChainPages.read(head, identity.ed25519PubRaw(), older, null);
  }

  private static byte[] hashForHeight(long height) {
    byte[] hash = new byte[32];
    for (int i = 0; i < 8; i++) {
      hash[i] = (byte) (height >>> (8 * (7 - i)));
    }
    return hash;
  }

  /** Endorsements are one per (peer, torrent), so growing a chain needs distinct torrents. */
  private static byte[] distinctInfoHash(int i) {
    byte[] infoHash = FAKE_INFO_HASH.clone();
    infoHash[0] ^= (byte) i;
    infoHash[1] ^= (byte) (i >>> 8);
    return infoHash;
  }

  private static final class RecordingSession extends SessionManager {
    final List<Object[]> puts = new CopyOnWriteArrayList<>();

    @Override
    public void dhtPutItem(byte[] publicKey, byte[] privateKey, Entry entry, byte[] salt) {
      puts.add(new Object[] {publicKey, privateKey, entry, salt});
    }

    String salt(int i) {
      return new String((byte[]) puts.get(i)[3], StandardCharsets.US_ASCII);
    }

    byte[] value(int i) {
      return ((Entry) puts.get(i)[2]).bencode();
    }

    byte[] olderPage(int page) {
      return page < puts.size() ? value(page) : null;
    }
  }

  private static final class FakeBlockSource implements BlockHeaderSource {
    private final AtomicLong tip = new AtomicLong(-1);
    private final Map<Long, byte[]> blocks = new HashMap<>();

    /** Puts the tip {@link KarmaConstants#ANCHOR_DEPTH_BLOCKS} above the block the writer uses. */
    FakeBlockSource withAnchor(long height) {
      tip.set(height + KarmaConstants.ANCHOR_DEPTH_BLOCKS);
      return this;
    }

    FakeBlockSource withBlock(long height, byte[] hash) {
      blocks.put(height, hash);
      return this;
    }

    @Override
    public BitcoinBlockReference getBlock(long height) {
      byte[] hash = blocks.get(height);
      if (hash == null) {
        return null;
      }
      return new BitcoinBlockReference(height, hash);
    }

    @Override
    public long getChainTipHeight() {
      return tip.get();
    }
  }
}
