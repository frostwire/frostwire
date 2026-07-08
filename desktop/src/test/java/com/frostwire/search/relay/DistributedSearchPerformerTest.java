/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.util.Hex;
import org.junit.jupiter.api.Test;

import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DistributedSearchPerformerTest {

    @Test
    void constructorRejectsNullKeywords() throws Exception {
        IdentityKeys id = IdentityKeys.generate();
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, null, new InMemoryLocalIndex(),
                        emptyDirectory(), id, new FakeTransport()));
    }

    @Test
    void constructorRejectsNegativeToken() throws Exception {
        IdentityKeys id = IdentityKeys.generate();
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(-1L, "ubuntu", new InMemoryLocalIndex(),
                        emptyDirectory(), id, new FakeTransport()));
    }

    @Test
    void constructorRejectsNullDependencies() throws Exception {
        IdentityKeys id = IdentityKeys.generate();
        LocalIndex index = new InMemoryLocalIndex();
        PeerDirectory dir = emptyDirectory();
        FakeTransport transport = new FakeTransport();
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, "ubuntu", null, dir, id, transport));
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, "ubuntu", index, null, id, transport));
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, "ubuntu", index, dir, null, transport));
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, "ubuntu", index, dir, id, null));
    }

    @Test
    void constructorRejectsBadLimits() throws Exception {
        IdentityKeys id = IdentityKeys.generate();
        LocalIndex index = new InMemoryLocalIndex();
        PeerDirectory dir = emptyDirectory();
        FakeTransport transport = new FakeTransport();
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, "ubuntu", index, dir, id, transport,
                        0, 50, 25, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, "ubuntu", index, dir, id, transport,
                        5, 0, 25, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, "ubuntu", index, dir, id, transport,
                        5, 50, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, "ubuntu", index, dir, id, transport,
                        5, 50, RemoteSearchRequest.MAX_LIMIT + 1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new DistributedSearchPerformer(0L, "ubuntu", index, dir, id, transport,
                        5, 50, 25, 0));
    }

    @Test
    void flagsAreNonCrawlerAndNoDdos() throws Exception {
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                0L, "ubuntu", new InMemoryLocalIndex(), emptyDirectory(),
                IdentityKeys.generate(), new FakeTransport());
        assertFalse(p.isCrawler());
        assertFalse(p.isDDOSProtectionActive());
    }

    @Test
    void getKeywordsReturnsConstructorValue() throws Exception {
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                0L, "ubuntu", new InMemoryLocalIndex(), emptyDirectory(),
                IdentityKeys.generate(), new FakeTransport());
        assertEquals("ubuntu", p.getKeywords());
    }

    @Test
    void performReturnsLocalResultsWhenNoPeers() throws Exception {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("ubuntu desktop", 1_000L, 1));
        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                1L, "ubuntu", index, emptyDirectory(),
                IdentityKeys.generate(), new FakeTransport());
        p.setListener(listener);

        p.perform();

        assertEquals(1, listener.results.size());
        List<SearchResult> out = listener.results.get(0);
        assertEquals(1, out.size());
        assertEquals("ubuntu desktop", out.get(0).getDisplayName());
        assertEquals(DistributedSearchPerformer.SOURCE_NAME, ((CompositeFileSearchResult) out.get(0)).getSource());
    }

    @Test
    void performMergesPeerResults() throws Exception {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("local ubuntu", 100L, 1));

        IdentityKeys peerKeys = IdentityKeys.generate();
        IdentityKeys requesterKeys = IdentityKeys.generate();
        PeerDirectory directory = directoryWithVerifiedPeer(peerKeys, "127.0.0.1", 6888);

        FakeTransport transport = new FakeTransport();
        transport.addResponse(peerKeys.ed25519PubRaw(), peerKeys, "peer ubuntu", 200L, 1);

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                2L, "ubuntu", index, directory, requesterKeys, transport,
                5, 50, 25, 10);
        p.setListener(listener);

        p.perform();

        assertEquals(1, listener.results.size());
        List<SearchResult> out = listener.results.get(0);
        assertEquals(2, out.size(), "local + peer results merged");
        assertEquals("local ubuntu", out.get(0).getDisplayName());
        assertEquals("peer ubuntu", out.get(1).getDisplayName());
        assertEquals(DistributedSearchPerformer.SOURCE_NAME,
                ((CompositeFileSearchResult) out.get(1)).getSource());
    }

    @Test
    void performDeduplicatesByInfoHash() throws Exception {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        LocalSharedTorrent local = torrent("local ubuntu", 100L, 1);
        index.upsert(local);

        IdentityKeys peerKeys = IdentityKeys.generate();
        IdentityKeys requesterKeys = IdentityKeys.generate();
        PeerDirectory directory = directoryWithVerifiedPeer(peerKeys, "127.0.0.1", 6888);

        FakeTransport transport = new FakeTransport();
        transport.addResponse(peerKeys.ed25519PubRaw(), peerKeys,
                local.infoHash(), "peer ubuntu", 200L, 1);

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                3L, "ubuntu", index, directory, requesterKeys, transport,
                5, 50, 25, 10);
        p.setListener(listener);

        p.perform();

        List<SearchResult> out = listener.results.get(0);
        assertEquals(1, out.size(), "duplicate infohash collapsed; local result wins");
        assertEquals("local ubuntu", out.get(0).getDisplayName());
    }

    @Test
    void performSkipsUnverifiedPeers() throws Exception {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        IdentityKeys peerKeys = IdentityKeys.generate();
        PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
        directory.upsert(peerKeys.ed25519PubRaw(), "127.0.0.1", 6888); // unverified

        FakeTransport transport = new FakeTransport();
        transport.addResponse(peerKeys.ed25519PubRaw(), peerKeys, "peer ubuntu", 100L, 1);

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                4L, "ubuntu", index, directory, IdentityKeys.generate(), transport,
                5, 50, 25, 10);
        p.setListener(listener);

        p.perform();

        assertEquals(1, listener.results.size());
        assertTrue(listener.results.get(0).isEmpty(), "unverified peer contributes nothing");
    }

    @Test
    void performFailsClosedOnPeerError() throws Exception {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        IdentityKeys peerKeys = IdentityKeys.generate();
        PeerDirectory directory = directoryWithVerifiedPeer(peerKeys, "127.0.0.1", 6888);
        FakeTransport transport = new FakeTransport(); // no response registered -> empty

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                5L, "ubuntu", index, directory, IdentityKeys.generate(), transport,
                5, 50, 25, 3);
        p.setListener(listener);

        p.perform();

        assertEquals(1, listener.results.size());
        assertTrue(listener.results.get(0).isEmpty(), "peer failure returns empty, not error");
    }

    @Test
    void stopBeforePerformSkipsSearch() throws Exception {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.upsert(torrent("ubuntu", 100L, 1));
        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                6L, "ubuntu", index, emptyDirectory(),
                IdentityKeys.generate(), new FakeTransport());
        p.setListener(listener);
        p.stop();

        p.perform();

        assertTrue(p.isStopped());
        assertTrue(listener.results.isEmpty(), "stopped performer reports nothing");
        assertEquals(1, listener.stoppedTokens.size());
        assertEquals(6L, listener.stoppedTokens.get(0).longValue());
    }

    @Test
    void endToEndWithFakeTransportAndRelayService() throws Exception {
        IdentityKeys responderKeys = IdentityKeys.generate();
        IdentityKeys requesterKeys = IdentityKeys.generate();

        InMemoryLocalIndex responderIndex = new InMemoryLocalIndex();
        responderIndex.upsert(torrent("ubuntu server", 500L, 1));

        RelaySearchService service = new RelaySearchService(responderIndex, responderKeys);

        PeerDirectory directory = directoryWithVerifiedPeer(
                responderKeys, "127.0.0.1", 6888);

        FakeTransport transport = new FakeTransport();
        transport.setSearchService(responderKeys.ed25519PubRaw(), service);

        InMemoryLocalIndex requesterIndex = new InMemoryLocalIndex();

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                7L, "ubuntu", requesterIndex, directory, requesterKeys,
                transport, 5, 50, 25, 10);
        p.setListener(listener);

        p.perform();

        assertEquals(1, listener.results.size());
        List<SearchResult> out = listener.results.get(0);
        assertEquals(1, out.size());
        assertEquals("ubuntu server", out.get(0).getDisplayName());
        assertEquals(DistributedSearchPerformer.SOURCE_NAME,
                ((CompositeFileSearchResult) out.get(0)).getSource());
    }

    @Test
    void crawlIsNoOp() throws Exception {
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                0L, "ubuntu", new InMemoryLocalIndex(), emptyDirectory(),
                IdentityKeys.generate(), new FakeTransport());
        p.crawl((CrawlableSearchResult) null);
    }

    @Test
    void performSurfacesMatchedFileFromPeerResponse() throws Exception {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        IdentityKeys peerKeys = IdentityKeys.generate();
        IdentityKeys requesterKeys = IdentityKeys.generate();
        PeerDirectory directory = directoryWithVerifiedPeer(peerKeys, "127.0.0.1", 6888);

        FakeTransport transport = new FakeTransport();
        transport.addResponseWithMatchedFile(
                peerKeys.ed25519PubRaw(), peerKeys, "Peer Project",
                200L, 1, "docs/readme.txt");

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                10L, "readme", index, directory, requesterKeys, transport,
                5, 50, 25, 10);
        p.setListener(listener);

        p.perform();

        assertEquals(1, listener.results.size());
        List<SearchResult> out = listener.results.get(0);
        assertEquals(1, out.size());
        CompositeFileSearchResult cfsr = (CompositeFileSearchResult) out.get(0);
        assertEquals("Peer Project", cfsr.getDisplayName());
        assertEquals("docs/readme.txt", cfsr.getFilename(),
                "matchedFile from remote response should be used as filename");
    }

    @Test
    void performRejectsOversizedMatchedFileFromPeer() throws Exception {
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        IdentityKeys peerKeys = IdentityKeys.generate();
        IdentityKeys requesterKeys = IdentityKeys.generate();
        PeerDirectory directory = directoryWithVerifiedPeer(peerKeys, "127.0.0.1", 6888);

        String huge = "a".repeat(5000);
        FakeTransport transport = new FakeTransport();
        transport.addResponseWithMatchedFile(
                peerKeys.ed25519PubRaw(), peerKeys, "Peer Project",
                200L, 1, huge);

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                11L, "readme", index, directory, requesterKeys, transport,
                5, 50, 25, 10);
        p.setListener(listener);

        p.perform();

        List<SearchResult> out = listener.results.get(0);
        assertEquals(1, out.size());
        CompositeFileSearchResult cfsr = (CompositeFileSearchResult) out.get(0);
        assertEquals("Peer Project.torrent", cfsr.getFilename(),
                "oversized matchedFile should be rejected, fall back to default");
    }

    @Test
    void performSetsTtlOneAndIncludesOwnPubkeyInPath() throws Exception {
        IdentityKeys peerKeys = IdentityKeys.generate();
        IdentityKeys requesterKeys = IdentityKeys.generate();
        PeerDirectory directory = directoryWithVerifiedPeer(peerKeys, "127.0.0.1", 6888);

        FakeTransport transport = new FakeTransport();
        transport.addResponse(peerKeys.ed25519PubRaw(), peerKeys, "peer ubuntu", 200L, 1);

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer p = new DistributedSearchPerformer(
                12L, "ubuntu", new InMemoryLocalIndex(), directory, requesterKeys, transport,
                5, 50, 25, 10);
        p.setListener(listener);

        p.perform();

        assertFalse(transport.sentRequests.isEmpty(), "at least one request must have been sent");
        RemoteSearchRequest sent = transport.sentRequests.get(0);
        assertEquals(0, sent.ttl(), "ttl must be 0: v1 is direct peer search only (no multi-hop)");
        assertEquals(1, sent.pathLength(), "path must contain the requester's own pubkey");
        assertArrayEquals(requesterKeys.ed25519PubRaw(), sent.path()[0],
                "path[0] must be this node's own Ed25519 pubkey");
    }

    // --- helpers ---

    private static PeerDirectory emptyDirectory() {
        return new PeerDirectory(new NoOpKarmaCache());
    }

    private static PeerDirectory directoryWithVerifiedPeer(IdentityKeys peer, String host, int port) {
        PeerDirectory d = new PeerDirectory(new NoOpKarmaCache());
        d.upsertVerified(peer.ed25519PubRaw(), host, port);
        return d;
    }

    private static final AtomicInteger HASH_COUNTER = new AtomicInteger();

    private static LocalSharedTorrent torrent(String name, long size, int fileCount) {
        byte[] hash = new byte[20];
        int n = HASH_COUNTER.incrementAndGet();
        hash[0] = (byte) (n >>> 24);
        hash[1] = (byte) (n >>> 16);
        hash[2] = (byte) (n >>> 8);
        hash[3] = (byte) n;
        byte[] nodeId = new byte[20];
        byte[] pub = new byte[32];
        pub[31] = (byte) n;
        long now = System.currentTimeMillis() / 1000L;
        return new LocalSharedTorrent.Builder()
                .infoHash(hash)
                .name(name)
                .sizeBytes(size)
                .fileCount(fileCount)
                .filesJson("[]")
                .publisherNodeId(nodeId)
                .publisherEd25519Pub(pub)
                .publisherUtpPort(0)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    private static final class RecordingListener implements SearchListener {
        final List<List<SearchResult>> results = new CopyOnWriteArrayList<>();
        final List<Long> stoppedTokens = new CopyOnWriteArrayList<>();
        final List<SearchError> errors = new CopyOnWriteArrayList<>();
        final List<Long> errorTokens = new CopyOnWriteArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> rs) {
            results.add(new ArrayList<>(rs));
        }

        @Override
        public void onStopped(long token) {
            stoppedTokens.add(token);
        }

        @Override
        public void onError(long token, SearchError error) {
            errors.add(error);
            errorTokens.add(token);
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
            if (query == null || query.isEmpty()) {
                return Collections.emptyList();
            }
            String q = query.toLowerCase();
            List<LocalSharedTorrent> out = new ArrayList<>();
            for (LocalSharedTorrent r : rows) {
                if (r.name().toLowerCase().contains(q)) {
                    out.add(r);
                    if (out.size() >= limit) break;
                }
            }
            return out;
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
     * Fake transport that delivers mock responses asynchronously via a
     * background thread. Two modes:
     * <ul>
     *   <li>{@code addResponse}: delivers a pre-built signed response.</li>
     *   <li>{@code setSearchService}: routes the request through a real
     *       {@link RelaySearchService} for end-to-end testing.</li>
     * </ul>
     */
    private static final class FakeTransport implements DistributedSearchTransport {
        private final Map<String, PeerResponse> responses = new ConcurrentHashMap<>();
        private final Map<String, RelaySearchService> services = new ConcurrentHashMap<>();
        private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();
        final List<RemoteSearchRequest> sentRequests = new CopyOnWriteArrayList<>();

        void addResponse(byte[] peerPub, IdentityKeys signer,
                         String name, long size, int fileCount) {
            responses.put(Hex.encode(peerPub),
                    new PeerResponse(signer, name, size, fileCount, null, null));
        }

        void addResponse(byte[] peerPub, IdentityKeys signer,
                         byte[] infoHash, String name, long size, int fileCount) {
            responses.put(Hex.encode(peerPub),
                    new PeerResponse(signer, name, size, fileCount, infoHash, null));
        }

        void addResponseWithMatchedFile(byte[] peerPub, IdentityKeys signer,
                         String name, long size, int fileCount, String matchedFile) {
            responses.put(Hex.encode(peerPub),
                    new PeerResponse(signer, name, size, fileCount, null, matchedFile));
        }

        void setSearchService(byte[] peerPub, RelaySearchService service) {
            services.put(Hex.encode(peerPub), service);
        }

        @Override
        public boolean send(byte[] targetPub, byte[] payload) {
            RemoteSearchRequest request = SearchPayloadCodec.decodeRequest(payload);
            if (request != null) {
                sentRequests.add(request);
            }
            String key = Hex.encode(targetPub);
            PeerResponse pr = responses.get(key);
            RelaySearchService svc = services.get(key);
            if (pr == null && svc == null) {
                return true; // accepted but no response will come
            }
            if (request == null) {
                return false;
            }
            // Deliver the response asynchronously to mimic the real transport.
            Thread t = new Thread(() -> {
                try {
                    byte[] responseBytes;
                    if (svc != null) {
                        Optional<RemoteSearchResponse> response = svc.handle(request);
                        if (response.isEmpty()) {
                            return;
                        }
                        responseBytes = SearchPayloadCodec.encodeResponse(response.get());
                    } else {
                        RemoteSearchResponse response = pr.signFor(request);
                        responseBytes = SearchPayloadCodec.encodeResponse(response);
                    }
                    for (PayloadListener l : listeners) {
                        l.onPayload(targetPub, responseBytes, System.currentTimeMillis());
                    }
                } catch (Exception e) {
                    // swallow — fail-closed
                }
            }, "fake-transport-" + key.substring(0, 8));
            t.setDaemon(true);
            t.start();
            return true;
        }

        @Override
        public void addListener(PayloadListener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(PayloadListener listener) {
            listeners.remove(listener);
        }
    }

    private static final class PeerResponse {
        final IdentityKeys signer;
        final String name;
        final long size;
        final int fileCount;
        final byte[] fixedInfoHash;
        final String matchedFile;

        PeerResponse(IdentityKeys signer, String name, long size, int fileCount,
                     byte[] fixedInfoHash, String matchedFile) {
            this.signer = signer;
            this.name = name;
            this.size = size;
            this.fileCount = fileCount;
            this.fixedInfoHash = fixedInfoHash;
            this.matchedFile = matchedFile;
        }

        RemoteSearchResponse signFor(RemoteSearchRequest request) throws Exception {
            byte[] infoHash = fixedInfoHash != null ? fixedInfoHash.clone() : newInfoHash();
            RemoteSearchResponse unsigned = RemoteSearchResponse.builder()
                    .nonce(request.nonce())
                    .timestamp(System.currentTimeMillis() / 1000L)
                    .addRow(infoHash, name, size, fileCount, signer.ed25519PubRaw(),
                            null, matchedFile)
                    .signature(new byte[64])
                    .build();
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(signer.ed25519().getPrivate());
            sig.update(unsigned.canonicalBytes());
            return RemoteSearchResponse.builder()
                    .nonce(request.nonce())
                    .timestamp(unsigned.timestamp())
                    .addRow(infoHash, name, size, fileCount, signer.ed25519PubRaw(),
                            null, matchedFile)
                    .signature(sig.sign())
                    .build();
        }

        private static byte[] newInfoHash() {
            byte[] h = new byte[20];
            int n = HASH_COUNTER.incrementAndGet();
            h[0] = (byte) (n >>> 24);
            h[1] = (byte) (n >>> 16);
            h[2] = (byte) (n >>> 8);
            h[3] = (byte) n;
            return h;
        }
    }
}
