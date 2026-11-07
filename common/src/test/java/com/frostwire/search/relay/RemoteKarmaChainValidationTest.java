/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Hex;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A remote peer's karma chain must be one the manifest format vouches for (IceBridge white paper
 * §10.5): version 1 manifests, which released clients publish ({@link KarmaManifestV1}), and the
 * pages {@link KarmaChainPublisher} publishes now score as before, and manifests whose head,
 * length, links or blocks don't hold up score 0.
 */
class RemoteKarmaChainValidationTest {

  /** A height after the karma chain existed, so chains anchored here are plausible. */
  private static final long H = 960_000L;

  private static final byte[] PEER = filled(32, 7);

  private IdentityKeys owner;
  private FakeBlockSource bitcoin;
  private KarmaChainWriter writer;
  private KarmaChainPublisher publisher;
  private PeerKarmaCache reader;

  @BeforeEach
  void setUp() throws Exception {
    owner = IdentityKeys.generate(0);
    bitcoin = new FakeBlockSource().withAnchor(H).withBlock(H);
    writer = new KarmaChainWriter(owner, bitcoin, new InMemoryStore(owner.ed25519PubRaw()));
    publisher = new KarmaChainPublisher(writer, owner);
  }

  @AfterEach
  void tearDown() {
    writer.close();
    if (reader != null) {
      reader.close();
    }
  }

  @Test
  void chainPublishedInPagesScoresEveryEndorsementItsPagesHold() {
    for (int i = 0; i < 12; i++) {
      long tip = H + 144L * i;
      bitcoin.withAnchor(tip).withBlock(tip);
      writer.onDownloadCompletedFromPeer(PEER, filled(20, i + 1));
    }
    List<Entry> pages = publisher.buildPages(writer.chain().entries());
    assertTrue(pages.size() > 1, "test needs older pages");
    KarmaChainSource paged =
        new KarmaChainSource() {
          @Override
          public Entry fetchManifest(byte[] peerPub) {
            return pages.get(0);
          }

          @Override
          public Entry fetchManifestPage(byte[] peerPub, int page) {
            return page < pages.size() ? pages.get(page) : null;
          }
        };
    reader = new PeerKarmaCache(new RemoteKarmaChainFetcher(paged, bitcoin));

    // All 12 endorsements, where the version 1 manifest held one.
    assertEquals(12, reader.participation(owner.ed25519PubRaw()));
  }

  @Test
  void chainPublishedByTheRealPublisherScoresItsEndorsements() {
    writer.onDownloadCompletedFromPeer(PEER, filled(20, 1));
    Entry manifest = v1(writer.chain().entries());
    assertNotNull(manifest);

    assertEquals(1, score(manifest, bitcoin));
    assertEquals(1, score(manifest, null), "offline readers score it too");
  }

  @Test
  void trimmedTailPublishedByTheRealPublisherScoresItsEndorsements() {
    for (int i = 0; i < 12; i++) {
      long tip = H + 144L * i;
      bitcoin.withAnchor(tip).withBlock(tip);
      writer.onDownloadCompletedFromPeer(PEER, filled(20, i + 1));
    }
    Entry manifest = v1(writer.chain().entries());
    assertNotNull(manifest);
    assertNotNull(manifest.dictionary().get("base"), "test needs a trimmed tail");
    long endorsements =
        manifest.dictionary().get("entries").list().stream()
            .filter(e -> "EN".equals(e.dictionary().get("k").string()))
            .count();
    assertTrue(endorsements > 0);

    assertEquals(endorsements, score(manifest, bitcoin));
  }

  @Test
  void manifestOfASnapshotNamesTheSnapshotsHead() {
    writer.onDownloadCompletedFromPeer(PEER, filled(20, 1));
    List<KarmaChainEntry> snapshot = writer.chain().entries();
    // The chain grows between the publisher's snapshot and its manifest.
    writer.onDownloadCompletedFromPeer(PEER, filled(20, 2));
    assertEquals(snapshot.size() + 1, writer.chain().entries().size());

    assertEquals(1, score(v1(snapshot), bitcoin));
  }

  @Test
  void headThatIsNotTheLastEntryScoresZero() {
    writer.onDownloadCompletedFromPeer(PEER, filled(20, 1));
    List<KarmaChainEntry> chain = writer.chain().entries();
    Entry manifest = v1(chain);

    // A fork: the manifest names a head its entries don't end with.
    String forkHead = Hex.encode(chain.get(0).entryHash());
    assertEquals(0, score(with(manifest, "head", new Entry(forkHead)), bitcoin));
    assertEquals(0, score(with(manifest, "head", null), bitcoin), "head is required");
  }

  @Test
  void lengthThatDoesNotMatchTheEntriesScoresZero() {
    writer.onDownloadCompletedFromPeer(PEER, filled(20, 1));
    Entry manifest = v1(writer.chain().entries());

    assertEquals(0, score(with(manifest, "len", new Entry(1L)), bitcoin), "a reset length");
    assertEquals(0, score(with(manifest, "len", new Entry(3L)), bitcoin), "a longer chain");
    assertEquals(0, score(with(manifest, "len", null), bitcoin), "len is required");
  }

  @Test
  void entriesThatDoNotLinkScoreZero() {
    PrivateKey key = owner.ed25519().getPrivate();
    byte[] pub = owner.ed25519PubRaw();
    BitcoinBlockReference block = bitcoin.getBlock(H);
    KarmaChainEntry ec =
        KarmaChainEntry.createEpochCommitment(
            KarmaChainEntry.GENESIS_PREV_HASH, 0, pub, block, 5.0, key);
    KarmaChainEntry unlinked =
        KarmaChainEntry.createEndorsement(
            filled(32, 9), 1, pub, block, PEER, filled(20, 1), 1, key);
    List<KarmaChainEntry> chain = new ArrayList<>();
    chain.add(ec);
    chain.add(unlinked);

    assertEquals(0, score(v1(chain), bitcoin));
  }

  @Test
  void endorsementWithABlockHashBitcoinDoesntHaveScoresZero() {
    KarmaChain chain = new KarmaChain(owner.ed25519PubRaw());
    PrivateKey key = owner.ed25519().getPrivate();
    chain.commitEpoch(bitcoin.getBlock(H), key);
    // Signed at a real height, under a made-up hash.
    chain.endorse(PEER, filled(20, 1), new BitcoinBlockReference(H + 1, filled(32, 0x66)), key);
    bitcoin.withTip(H + 1).withBlock(H + 1);

    assertEquals(0, score(v1(chain.entries()), bitcoin));
    assertEquals(1, score(v1(chain.entries()), null), "offline: unchecked");
  }

  @Test
  void endorsementWhoseBlockCantBeResolvedYetIsNotValidYet() {
    writer.commitEpochIfNeeded();
    bitcoin.withAnchor(H + 1).withBlock(H + 1);
    writer.onDownloadCompletedFromPeer(PEER, filled(20, 1));
    List<KarmaChainEntry> chain = writer.chain().entries();
    assertEquals(H + 1, chain.get(chain.size() - 1).blockHeight());
    Entry manifest = v1(chain);

    // The reader knows Bitcoin is at H + 1 but can't look that block up yet.
    FakeBlockSource lagging = new FakeBlockSource().withTip(H + 1).withBlock(H);
    assertEquals(0, score(manifest, lagging));
    lagging.withBlock(H + 1);
    assertEquals(1, score(manifest, lagging), "valid once the block resolves");
    FakeBlockSource behind = new FakeBlockSource().withTip(H).withBlock(H);
    assertEquals(1, score(manifest, behind), "a block past the tip is within the tolerance");
    assertEquals(1, score(manifest, new FakeBlockSource()), "offline: unchecked, as before");
  }

  @Test
  void chainStartingBeforeKarmaExistedScoresZero() throws Exception {
    long old = KarmaConstants.FIRST_KARMA_BLOCK_HEIGHT - 1;
    FakeBlockSource then = new FakeBlockSource().withAnchor(old).withBlock(old);
    try (KarmaChainWriter backdated =
        new KarmaChainWriter(owner, then, new InMemoryStore(owner.ed25519PubRaw()))) {
      backdated.onDownloadCompletedFromPeer(PEER, filled(20, 1));
      Entry manifest = v1(backdated.chain().entries());
      assertNotNull(manifest);

      assertEquals(0, score(manifest, then));
      assertEquals(0, score(manifest, null), "offline too");
    }
  }

  @Test
  void chainStartingAtTheFirstKarmaHeightScoresItsEndorsements() throws Exception {
    long first = KarmaConstants.FIRST_KARMA_BLOCK_HEIGHT;
    FakeBlockSource then = new FakeBlockSource().withAnchor(first).withBlock(first);
    try (KarmaChainWriter earliest =
        new KarmaChainWriter(owner, then, new InMemoryStore(owner.ed25519PubRaw()))) {
      earliest.onDownloadCompletedFromPeer(PEER, filled(20, 1));
      Entry manifest = v1(earliest.chain().entries());

      assertEquals(1, score(manifest, then));
    }
  }

  // --- helpers ---

  private long score(Entry manifest, BlockHeaderSource blocks) {
    if (reader != null) {
      reader.close();
    }
    reader =
        new PeerKarmaCache(
            blocks == null
                ? new RemoteKarmaChainFetcher(pub -> manifest)
                : new RemoteKarmaChainFetcher(pub -> manifest, blocks));
    return reader.participation(owner.ed25519PubRaw());
  }

  /** The version 1 manifest of {@code chain}, as released clients publish it. */
  private Entry v1(List<KarmaChainEntry> chain) {
    return KarmaManifestV1.build(chain, owner.ed25519PubRaw());
  }

  /** The manifest with {@code key} replaced, or removed when {@code value} is null. */
  private static Entry with(Entry manifest, String key, Entry value) {
    Map<String, Object> copy = new HashMap<>(manifest.dictionary());
    if (value == null) {
      copy.remove(key);
    } else {
      copy.put(key, value);
    }
    return Entry.fromMap(copy);
  }

  private static byte[] filled(int length, int value) {
    byte[] bytes = new byte[length];
    java.util.Arrays.fill(bytes, (byte) value);
    return bytes;
  }

  /** A made-up but stable block hash for a height. */
  static byte[] hashForHeight(long height) {
    byte[] hash = new byte[32];
    for (int i = 0; i < 8; i++) {
      hash[i] = (byte) (height >>> (8 * (7 - i)));
    }
    hash[31] = 1;
    return hash;
  }

  static final class FakeBlockSource implements BlockHeaderSource {
    private final AtomicLong tip = new AtomicLong(-1);
    private final Map<Long, byte[]> blocks = new HashMap<>();

    FakeBlockSource withTip(long height) {
      tip.set(height);
      return this;
    }

    /** A tip deep enough that the writer anchors at {@code height}. */
    FakeBlockSource withAnchor(long height) {
      return withTip(height + KarmaConstants.ANCHOR_DEPTH_BLOCKS);
    }

    FakeBlockSource withBlock(long height) {
      synchronized (blocks) {
        blocks.put(height, hashForHeight(height));
      }
      return this;
    }

    @Override
    public BitcoinBlockReference getBlock(long height) {
      byte[] hash;
      synchronized (blocks) {
        hash = blocks.get(height);
      }
      return hash == null ? null : new BitcoinBlockReference(height, hash);
    }

    @Override
    public long getChainTipHeight() {
      return tip.get();
    }
  }

  private static final class InMemoryStore implements KarmaChainStore {
    private final KarmaChain chain;

    InMemoryStore(byte[] ownerPub) {
      chain = new KarmaChain(ownerPub);
    }

    @Override
    public void append(KarmaChainEntry entry) {}

    @Override
    public KarmaChain loadChain(byte[] ownerPub) {
      return chain;
    }

    @Override
    public void close() {}
  }
}
