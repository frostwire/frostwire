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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RemoteKarmaChainFetcherTest {

  private static byte[] peerPub;
  private static BitcoinBlockReference block;

  @BeforeAll
  static void setUpClass() throws Exception {
    peerPub = new byte[32];
    for (int i = 0; i < 32; i++) peerPub[i] = (byte) (i + 1);
    byte[] hash = new byte[32];
    for (int i = 0; i < 32; i++) hash[i] = (byte) (i + 1);
    block = new BitcoinBlockReference(850000L, hash);
  }

  @Test
  void constructorRejectsNullSource() {
    assertThrows(IllegalArgumentException.class, () -> new RemoteKarmaChainFetcher(null));
  }

  @Test
  void fetchChainRejectsBadPubkey() {
    FakeSource source = new FakeSource();
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    assertNull(fetcher.fetchChain(null));
    assertNull(fetcher.fetchChain(new byte[31]));
    assertEquals(0, source.callCount.get(), "bad pubkey must not hit source");
  }

  @Test
  void fetchChainReturnsNullWhenSourceReturnsNull() {
    FakeSource source = new FakeSource();
    source.nextManifest = null;
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    assertNull(fetcher.fetchChain(peerPub));
    assertEquals(0, fetcher.cacheSize());
  }

  @Test
  void fetchChainReturnsNullWhenManifestIsNotDict() {
    FakeSource source = new FakeSource();
    source.nextManifest = new Entry("not-a-dict");
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    assertNull(fetcher.fetchChain(peerPub));
  }

  @Test
  void fetchChainReturnsNullWhenManifestHasNoEntries() {
    FakeSource source = new FakeSource();
    source.nextManifest = Entry.fromMap(new HashMap<>());
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    assertNull(fetcher.fetchChain(peerPub));
  }

  @Test
  void fetchChainReturnsNullWhenChainFailsVerification() throws Exception {
    // All-zero signature: verifySignature will return false, so
    // KarmaChain.verify will reject the chain.
    Map<String, Object> manifest = buildManifestWithBadSignature();
    FakeSource source = new FakeSource();
    source.nextManifest = Entry.fromMap(manifest);
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    assertNull(fetcher.fetchChain(peerPub));
  }

  @Test
  void fetchChainReturnsChainWhenVerified() throws Exception {
    // Feed ACTUAL publisher output: build a real KarmaChainWriter chain,
    // publish its manifest, feed it through the fetcher with a fake source.
    FakeBlockSource blockSource = new FakeBlockSource()
        .withTip(144L).withBlock(144L, hashForHeight(144L));
    IdentityKeys publisherIdentity = IdentityKeys.generate(0);
    byte[] ownerPub = publisherIdentity.ed25519PubRaw();
    InMemoryStore store = new InMemoryStore(ownerPub);
    KarmaChainWriter writer = new KarmaChainWriter(publisherIdentity, blockSource, store);
    writer.onDownloadCompletedFromPeer(peerPub, new byte[20]);
    List<KarmaChainEntry> published = writer.chain().entries();
    assertTrue(published.size() >= 2, "writer must produce commitment + endorsement");
    KarmaChainPublisher publisher =
        new KarmaChainPublisher(writer, publisherIdentity);
    Entry manifest = publisher.buildManifest(published);
    assertNotNull(manifest, "real publisher must emit a manifest");
    writer.close();

    FakeSource source = new FakeSource();
    source.nextManifest = manifest;
    AtomicLong now = new AtomicLong();
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source, 4, 100, now::get);

    List<KarmaChainEntry> fetched = fetcher.fetchChain(ownerPub);
    assertNotNull(fetched, "actual publisher output must verify end-to-end");
    assertEquals(published.size(), fetched.size());
    assertEquals(KarmaChainEntry.Kind.EPOCH_COMMITMENT, fetched.get(0).kind());
    assertEquals(KarmaChainEntry.Kind.ENDORSEMENT, fetched.get(1).kind());
    assertEquals(1, fetcher.cacheSize());
    assertNull(fetcher.fetchChain(peerPub), "another owner's signed chain cannot confer trust");
    assertThrows(UnsupportedOperationException.class, fetched::clear);
    source.nextManifest = null;
    now.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(101));
    assertNull(fetcher.fetchChain(ownerPub), "expired positive trust must be refreshed");
    assertEquals(0, fetcher.cacheSize());
    fetcher.close();
  }

  @Test
  void fetchChainAcceptsTruncatedTailManifest() throws Exception {
    // Grow a chain until the publisher trims the tail; the emitted
    // manifest carries base seq/hash and must pass verifyTail.
    FakeBlockSource blockSource = new FakeBlockSource();
    IdentityKeys publisherIdentity = IdentityKeys.generate(0);
    byte[] ownerPub = publisherIdentity.ed25519PubRaw();
    InMemoryStore store = new InMemoryStore(ownerPub);
    KarmaChainWriter writer = new KarmaChainWriter(publisherIdentity, blockSource, store);
    byte[] infoHash = new byte[20];
    for (int i = 0; i < 12; i++) {
      long tip = 144L * (i + 1);
      blockSource.withTip(tip).withBlock(tip, hashForHeight(tip));
      writer.onDownloadCompletedFromPeer(peerPub, infoHash);
    }
    List<KarmaChainEntry> full = writer.chain().entries();
    KarmaChainPublisher publisher = new KarmaChainPublisher(writer, publisherIdentity);
    Entry manifest = publisher.buildManifest(full);
    assertNotNull(manifest);
    int included = manifest.dictionary().get("entries").list().size();
    assertTrue(included < full.size(), "test requires a trimmed tail");
    assertNotNull(manifest.dictionary().get("base"), "trimmed manifest must carry base");
    writer.close();

    FakeSource source = new FakeSource();
    source.nextManifest = manifest;
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    List<KarmaChainEntry> fetched = fetcher.fetchChain(ownerPub);
    assertNotNull(fetched, "trimmed tail with correct base must verify");
    assertEquals(included, fetched.size());
    fetcher.close();
  }

  @Test
  void fetchChainRejectsTamperedBase() throws Exception {
    FakeBlockSource blockSource = new FakeBlockSource();
    IdentityKeys publisherIdentity = IdentityKeys.generate(0);
    byte[] ownerPub = publisherIdentity.ed25519PubRaw();
    InMemoryStore store = new InMemoryStore(ownerPub);
    KarmaChainWriter writer = new KarmaChainWriter(publisherIdentity, blockSource, store);
    byte[] infoHash = new byte[20];
    for (int i = 0; i < 12; i++) {
      long tip = 144L * (i + 1);
      blockSource.withTip(tip).withBlock(tip, hashForHeight(tip));
      writer.onDownloadCompletedFromPeer(peerPub, infoHash);
    }
    List<KarmaChainEntry> full = writer.chain().entries();
    KarmaChainPublisher publisher = new KarmaChainPublisher(writer, publisherIdentity);
    Entry manifest = publisher.buildManifest(full);
    assertNotNull(manifest);
    assertNotNull(manifest.dictionary().get("base"), "test requires a trimmed tail");
    writer.close();

    // Tamper the base prevHash: verification must fail closed.
    Map<String, Entry> original = manifest.dictionary();
    Map<String, Object> tampered = new HashMap<>();
    tampered.put("v", original.get("v"));
    tampered.put("pub", original.get("pub"));
    tampered.put("len", original.get("len"));
    tampered.put("head", original.get("head"));
    tampered.put("entries", original.get("entries"));
    tampered.put("ts", original.get("ts"));
    Map<String, Object> badBase = new HashMap<>();
    Map<String, Entry> base = manifest.dictionary().get("base").dictionary();
    badBase.put("seq", new Entry(base.get("seq").integer()));
    badBase.put("ph", new Entry(Hex.encode(new byte[32])));
    tampered.put("base", Entry.fromMap(badBase));

    FakeSource source = new FakeSource();
    source.nextManifest = Entry.fromMap(tampered);
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    assertNull(fetcher.fetchChain(ownerPub), "tampered base must be rejected");
    assertEquals(0, fetcher.cacheSize());
    fetcher.close();
  }

  @Test
  void fetchChainCachesAbsence() {
    FakeSource source = new FakeSource();
    source.nextManifest = null;
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    assertNull(fetcher.fetchChain(peerPub));
    fetcher.fetchChain(peerPub);
    assertEquals(1, source.callCount.get(), "cached absence should skip subsequent source calls");
  }

  @Test
  void evictForcesRefetch() {
    FakeSource source = new FakeSource();
    source.nextManifest = null;
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    fetcher.fetchChain(peerPub);
    fetcher.evict(peerPub);
    fetcher.fetchChain(peerPub);
    assertEquals(2, source.callCount.get(), "evict() should allow a new source call");
  }

  @Test
  void clearResetsCache() {
    FakeSource source = new FakeSource();
    source.nextManifest = null;
    RemoteKarmaChainFetcher fetcher = new RemoteKarmaChainFetcher(source);
    fetcher.fetchChain(peerPub);
    fetcher.clear();
    fetcher.fetchChain(peerPub);
    assertEquals(2, source.callCount.get(), "clear() should reset both verified and absent caches");
  }

  // --- PeerKarmaCache tests ---

  @Test
  void peerKarmaCacheRejectsNullFetcher() {
    assertThrows(IllegalArgumentException.class, () -> new PeerKarmaCache(null));
  }

  @Test
  void peerKarmaCacheReturnsZeroForBadInputs() {
    PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(new FakeSource()));
    assertEquals(0, cache.getKarma(null));
    assertEquals(0, cache.getKarma(new byte[31]));
  }

  @Test
  void peerKarmaCacheReturnsZeroForEmptyChain() {
    PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(new FixedSource(null)));
    assertEquals(0, cache.getKarma(peerPub));
  }

  @Test
  void peerKarmaCacheCountsEndorsements() throws Exception {
    FakeBlockSource blockSource = new FakeBlockSource()
        .withTip(144L).withBlock(144L, hashForHeight(144L));
    IdentityKeys publisherIdentity = IdentityKeys.generate(0);
    byte[] ownerPub = publisherIdentity.ed25519PubRaw();
    InMemoryStore store = new InMemoryStore(ownerPub);
    KarmaChainWriter writer = new KarmaChainWriter(publisherIdentity, blockSource, store);
    // One endorsement produces the smallest verifiable published chain: EC + EN.
    writer.onDownloadCompletedFromPeer(peerPub, new byte[20]);
    List<KarmaChainEntry> chain = new ArrayList<>(writer.chain().entries());
    Entry manifest = new KarmaChainPublisher(writer, publisherIdentity).buildManifest(chain);
    assertNotNull(manifest, "cache test must use real publisher output");
    writer.close();

    FakeSource source = new FakeSource();
    source.nextManifest = manifest;
    PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));

    assertEquals(1, cache.getKarma(ownerPub), "score counts ENDORSEMENT entries only");
  }

  @Test
  void peerKarmaCacheMemoizesScore() {
    FakeSource source = new FakeSource();
    source.nextManifest = null;
    PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));
    cache.getKarma(peerPub);
    cache.getKarma(peerPub);
    cache.getKarma(peerPub);
    assertEquals(1, source.callCount.get(), "score should be memoized after first lookup");
    assertTrue(cache.cacheHitCount() >= 2);
  }

  @Test
  void peerKarmaCacheEvictClearsBoth() {
    FakeSource source = new FakeSource();
    source.nextManifest = null;
    PeerKarmaCache cache = new PeerKarmaCache(new RemoteKarmaChainFetcher(source));
    cache.getKarma(peerPub);
    cache.evict(peerPub);
    cache.getKarma(peerPub);
    assertEquals(2, source.callCount.get());
  }

  // --- helpers ---

  private static final class InMemoryStore implements KarmaChainStore {
    private final KarmaChain chain;

    InMemoryStore(byte[] ownerPub) {
      this.chain = new KarmaChain(ownerPub);
    }

    @Override
    public void append(KarmaChainEntry entry) {
      // KarmaChainWriter already appended to its in-memory chain; mirror it here.
    }

    @Override
    public KarmaChain loadChain(byte[] ownerPub) {
      return chain;
    }

    @Override
    public void close() {
    }
  }

  private static final class FakeBlockSource implements BlockHeaderSource {
    private final AtomicLong tip = new AtomicLong(-1);
    private final Map<Long, byte[]> blocks = new HashMap<>();

    FakeBlockSource withTip(long height) {
      tip.set(height);
      return this;
    }

    FakeBlockSource withBlock(long height, byte[] hash) {
      blocks.put(height, hash.clone());
      return this;
    }

    @Override
    public BitcoinBlockReference getBlock(long height) {
      byte[] hash = blocks.get(height);
      return hash == null ? null : new BitcoinBlockReference(height, hash);
    }

    @Override
    public long getChainTipHeight() {
      return tip.get();
    }
  }

  private static byte[] hashForHeight(long height) {
    byte[] hash = new byte[32];
    for (int i = 0; i < 8; i++) {
      hash[i] = (byte) (height >>> (8 * (7 - i)));
    }
    return hash;
  }

  private static Map<String, Object> buildManifestWithBadSignature() throws Exception {
    byte[] ownerPub = IdentityKeys.generate(0).ed25519PubRaw();
    Map<String, Object> manifest = new HashMap<>();
    manifest.put("v", new Entry(1L));
    manifest.put("len", new Entry(1L));
    manifest.put("head", new Entry(Hex.encode(new byte[32])));
    manifest.put("ts", new Entry(0L));

    Map<String, Object> entry = new HashMap<>();
    entry.put("k", new Entry("EC"));
    entry.put("seq", new Entry(0L));
    entry.put("bh", new Entry(850000L));
    entry.put("bkh", new Entry(Hex.encode(block.hash())));
    entry.put("ph", new Entry(Hex.encode(KarmaChainEntry.GENESIS_PREV_HASH)));
    entry.put("pub", new Entry(Base64.getUrlEncoder().withoutPadding().encodeToString(ownerPub)));
    entry.put("s", new Entry(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64])));
    entry.put("ep", new Entry(1L));
    entry.put("en", new Entry("5.00"));
    manifest.put("entries", Entry.fromList(List.of(Entry.fromMap(entry))));
    return manifest;
  }

  private static final class FakeSource implements KarmaChainSource {
    Entry nextManifest;
    final AtomicInteger callCount = new AtomicInteger();
    final AtomicReference<byte[]> lastPeerPub = new AtomicReference<>();

    @Override
    public Entry fetchManifest(byte[] peerPub) {
      callCount.incrementAndGet();
      lastPeerPub.set(peerPub.clone());
      return nextManifest;
    }
  }

  private static final class FixedSource implements KarmaChainSource {
    private final Entry manifest;

    FixedSource(Entry manifest) {
      this.manifest = manifest;
    }

    @Override
    public Entry fetchManifest(byte[] peerPub) {
      return manifest;
    }
  }
}
