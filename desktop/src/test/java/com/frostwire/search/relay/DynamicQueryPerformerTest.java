/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.SearchError;
import com.frostwire.search.relay.icebridge.IceBridgeTopology;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.util.Hex;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Originator dynamic querying ({@code DESIGN_DYNAMIC_QUERYING.md}).
 *
 * <p>A fake transport counts every first-hop {@code send} and signs a
 * canned answer per registered peer, so each test observes exactly how
 * many phases fired, with which TTLs, and within which budget.
 */
class DynamicQueryPerformerTest {

  @Test
  void phase1AnswerNeverTriggersPhase2() throws Exception {
    List<IdentityKeys> peers = verifiedPeers(10);
    PeerDirectory directory = directoryWith(peers);
    FakeTransport transport = answeringTransport(peers);
    RecordingListener listener = new RecordingListener();
    DistributedSearchPerformer p =
        new DistributedSearchPerformer(
            1L, "ubuntu", new InMemoryLocalIndex(), directory,
            IdentityKeys.generate(), transport,
            10, 50, 25, 10, DynamicQueryConfig.withDesiredResults(2));
    p.setListener(listener);

    p.perform();

    assertEquals(4, transport.sentRequests.size(), "phase-1 probe subset only");
    for (RemoteSearchRequest sent : transport.sentRequests) {
      assertEquals(1, sent.ttl(), "probe phase uses TTL=1");
    }
    assertEquals(1, listener.results.size());
    assertEquals(4, listener.results.get(0).size());
  }

  @Test
  void thinResultsExpandToLaterPhases() throws Exception {
    List<IdentityKeys> peers = verifiedPeers(10);
    PeerDirectory directory = directoryWith(peers);
    FakeTransport transport = answeringTransport(peers);
    RecordingListener listener = new RecordingListener();
    DistributedSearchPerformer p =
        new DistributedSearchPerformer(
            2L, "ubuntu", new InMemoryLocalIndex(), directory,
            IdentityKeys.generate(), transport,
            10, 50, 25, 10, DynamicQueryConfig.withDesiredResults(10));
    p.setListener(listener);

    p.perform();

    assertEquals(10, transport.sentRequests.size(), "phase 1 (4) + phase 2 (6)");
    for (int i = 0; i < 4; i++) {
      assertEquals(1, transport.sentRequests.get(i).ttl());
    }
    for (int i = 4; i < 10; i++) {
      assertEquals(2, transport.sentRequests.get(i).ttl(), "expansion uses TTL=2");
    }
    assertEquals(1, listener.results.size());
    assertEquals(10, listener.results.get(0).size());
  }

  @Test
  void totalRequestsStayUnderBudget() throws Exception {
    List<IdentityKeys> peers = verifiedPeers(40);
    PeerDirectory directory = directoryWith(peers);
    FakeTransport transport = new FakeTransport(); // nobody answers
    RecordingListener listener = new RecordingListener();
    int maxPeers = 30;
    DistributedSearchPerformer p =
        new DistributedSearchPerformer(
            3L, "rare-content-xyz", new InMemoryLocalIndex(), directory,
            IdentityKeys.generate(), transport,
            maxPeers, 50, 25, 10, DynamicQueryConfig.defaults());
    p.setListener(listener);

    p.perform();

    assertTrue(transport.sentRequests.size() <= maxPeers,
        "all phases together must not exceed maxPeers, was " + transport.sentRequests.size());
    assertTrue(transport.sentTtls().contains(1));
    assertTrue(transport.sentTtls().contains(2));
    assertTrue(
        transport.sentTtls().contains(IceBridgeTopology.get().searchTtl()),
        "final phase uses the full topology TTL");
    assertEquals(1, listener.results.size());
    assertTrue(listener.results.get(0).isEmpty());
  }

  @Test
  void phase3ExhaustionReturnsPartialResults() throws Exception {
    List<IdentityKeys> peers = verifiedPeers(12);
    PeerDirectory directory = directoryWith(peers);
    // Only 2 of the 12 peers answer; the rest are unreachable.
    FakeTransport transport = answeringTransport(peers.subList(0, 2));
    RecordingListener listener = new RecordingListener();
    DistributedSearchPerformer p =
        new DistributedSearchPerformer(
            4L, "ubuntu", new InMemoryLocalIndex(), directory,
            IdentityKeys.generate(), transport,
            12, 50, 25, 10, DynamicQueryConfig.withDesiredResults(10));
    p.setListener(listener);

    p.perform();

    assertEquals(12, transport.sentRequests.size(), "all phases ran to exhaustion");
    assertEquals(1, listener.results.size(), "exactly one onResults even when exhausted");
    assertEquals(2, listener.results.get(0).size(), "partial hits are still returned");
  }

  // --- helpers ---

  private static List<IdentityKeys> verifiedPeers(int n) throws Exception {
    List<IdentityKeys> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      out.add(IdentityKeys.generate());
    }
    return out;
  }

  private static PeerDirectory directoryWith(List<IdentityKeys> peers) {
    PeerDirectory d = new PeerDirectory(new NoOpKarmaCache());
    for (IdentityKeys k : peers) {
      d.upsertVerified(k.ed25519PubRaw(), "127.0.0.1", 6888);
    }
    return d;
  }

  private static FakeTransport answeringTransport(List<IdentityKeys> peers) {
    FakeTransport t = new FakeTransport();
    int i = 0;
    for (IdentityKeys k : peers) {
      t.addResponse(k, "answer " + (++i));
    }
    return t;
  }

  private static final class RecordingListener implements SearchListener {
    final List<List<SearchResult>> results = new CopyOnWriteArrayList<>();

    @Override
    public void onResults(long token, List<? extends SearchResult> rs) {
      results.add(new ArrayList<>(rs));
    }

    @Override
    public void onStopped(long token) {
    }

    @Override
    public void onError(long token, SearchError error) {
    }
  }

  private static final class InMemoryLocalIndex implements LocalIndex {
    private final List<LocalSharedTorrent> rows = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void upsert(LocalSharedTorrent torrent) {
      rows.removeIf(r -> r.infoHashHex().equals(torrent.infoHashHex()));
      rows.add(torrent);
    }

    @Override
    public void delete(String infoHashHex) {
      rows.removeIf(r -> r.infoHashHex().equalsIgnoreCase(infoHashHex));
    }

    @Override
    public Optional<LocalSharedTorrent> get(String infoHashHex) {
      for (LocalSharedTorrent r : rows) {
        if (r.infoHashHex().equalsIgnoreCase(infoHashHex)) {
          return Optional.of(r);
        }
      }
      return Optional.empty();
    }

    @Override
    public List<LocalSharedTorrent> search(String query, int limit) {
      return Collections.emptyList();
    }

    @Override
    public void markPublished(String infoHashHex, long timestamp) {
    }

    @Override
    public List<String> needsRepublish(long nowSec, long thresholdSec) {
      return Collections.emptyList();
    }

    @Override
    public void updateLastSeen(String infoHashHex, long ts) {
    }

    @Override
    public int size() {
      return rows.size();
    }
  }

  private static final class NoOpKarmaCache extends PeerKarmaCache {
    NoOpKarmaCache() {
      super(new RemoteKarmaChainFetcher(new NoOpKarmaSource()));
    }

    @Override
    public long getKarma(byte[] peerPub) {
      return 0;
    }
  }

  private static final class NoOpKarmaSource implements KarmaChainSource {
    @Override
    public com.frostwire.jlibtorrent.Entry fetchManifest(byte[] peerPub) {
      return null;
    }
  }

  /**
   * Fake transport with synchronous delivery. Registered peers answer
   * with one signed row; unregistered peers NACK the send (fail-closed,
   * no latch wait) so expansion tests stay fast.
   */
  private static final class FakeTransport implements DistributedSearchTransport {
    private final Map<String, IdentityKeys> responders = new ConcurrentHashMap<>();
    private final Map<String, String> names = new ConcurrentHashMap<>();
    private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();
    final List<RemoteSearchRequest> sentRequests = new CopyOnWriteArrayList<>();

    private static final AtomicInteger HASH_COUNTER = new AtomicInteger();

    void addResponse(IdentityKeys signer, String name) {
      responders.put(Hex.encode(signer.ed25519PubRaw()), signer);
      names.put(Hex.encode(signer.ed25519PubRaw()), name);
    }

    List<Integer> sentTtls() {
      List<Integer> out = new ArrayList<>(sentRequests.size());
      for (RemoteSearchRequest r : sentRequests) {
        out.add(r.ttl());
      }
      return out;
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      RemoteSearchRequest request = SearchPayloadCodec.decodeRequest(payload);
      if (request == null) {
        return false;
      }
      sentRequests.add(request);
      IdentityKeys signer = responders.get(Hex.encode(targetPub));
      if (signer == null) {
        return false;
      }
      try {
        RemoteSearchResponse response = signFor(signer, names.get(Hex.encode(targetPub)), request);
        byte[] responseBytes = SearchPayloadCodec.encodeResponse(response);
        for (PayloadListener l : listeners) {
          l.onPayload(targetPub, responseBytes, System.currentTimeMillis());
        }
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    @Override
    public void addListener(PayloadListener listener) {
      listeners.add(listener);
    }

    @Override
    public void removeListener(PayloadListener listener) {
      listeners.remove(listener);
    }

    private static RemoteSearchResponse signFor(
        IdentityKeys signer, String name, RemoteSearchRequest request) throws Exception {
      byte[] infoHash = new byte[20];
      int n = HASH_COUNTER.incrementAndGet();
      infoHash[0] = (byte) (n >>> 24);
      infoHash[1] = (byte) (n >>> 16);
      infoHash[2] = (byte) (n >>> 8);
      infoHash[3] = (byte) n;
      RemoteSearchResponse unsigned =
          RemoteSearchResponse.builder()
              .nonce(request.nonce())
              .timestamp(System.currentTimeMillis() / 1000L)
              .addRow(infoHash, name, 200L, 1, signer.ed25519PubRaw(), null, null)
              .signature(new byte[64])
              .build();
      Signature sig = Signature.getInstance("Ed25519");
      sig.initSign(signer.ed25519().getPrivate());
      sig.update(unsigned.canonicalBytes());
      return RemoteSearchResponse.builder()
          .nonce(request.nonce())
          .timestamp(unsigned.timestamp())
          .addRow(infoHash, name, 200L, 1, signer.ed25519PubRaw(), null, null)
          .signature(sig.sign())
          .build();
    }
  }

  @Test
  void nullConfigKeepsLegacySingleShot() throws Exception {
    List<IdentityKeys> peers = verifiedPeers(10);
    PeerDirectory directory = directoryWith(peers);
    FakeTransport transport = answeringTransport(peers);
    RecordingListener listener = new RecordingListener();
    DistributedSearchPerformer p =
        new DistributedSearchPerformer(
            5L, "ubuntu", new InMemoryLocalIndex(), directory,
            IdentityKeys.generate(), transport,
            10, 50, 25, 10, null);
    p.setListener(listener);

    p.perform();

    assertEquals(10, transport.sentRequests.size(), "legacy path contacts all peers at once");
    assertNull(p.dynamicQueryConfig());
    assertEquals(1, listener.results.size());
    assertEquals(10, listener.results.get(0).size());
  }

  @Test
  void dynamicQueryConfigRejectsBadTargets() {
    assertThrows(IllegalArgumentException.class, () -> DynamicQueryConfig.withDesiredResults(0));
    assertThrows(IllegalArgumentException.class, () -> DynamicQueryConfig.withDesiredResults(-3));
  }
}
