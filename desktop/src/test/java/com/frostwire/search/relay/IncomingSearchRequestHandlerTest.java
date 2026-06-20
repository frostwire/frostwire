/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import com.frostwire.util.Hex;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IncomingSearchRequestHandlerTest {

    private static final AtomicInteger HASH_COUNTER = new AtomicInteger();

    @Test
    void forwardsWhenTtlGreaterThanZero() throws Exception {
        KeyPair requesterKey = generateEd25519KeyPair();
        byte[] requesterPub = rawPub(requesterKey);

        IdentityKeys handlerIdentity = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.torrents.add(torrent("ubuntu server", 500L, 1));
        RelaySearchService service = new RelaySearchService(index, handlerIdentity);

        KeyPair peerAKey = generateEd25519KeyPair();
        KeyPair peerBKey = generateEd25519KeyPair();
        PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
        directory.upsertVerified(rawPub(peerAKey), "host-a", 6881);
        directory.upsertVerified(rawPub(peerBKey), "host-b", 6882);

        CapturingTransport transport = new CapturingTransport();
        IncomingSearchRequestHandler handler =
                new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
        handler.start();

        byte[][] path = {requesterPub};
        RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 1, path);
        transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

        assertFalse(transport.sent.isEmpty(), "local response must be sent");
        assertTrue(transport.sent.size() >= 2, "response + at least one forwarded request");
        List<RemoteSearchRequest> forwarded = extractForwardedRequests(transport);
        assertEquals(2, forwarded.size(), "both peers must receive a forwarded request");
    }

    @Test
    void doesNotForwardWhenTtlZero() throws Exception {
        KeyPair requesterKey = generateEd25519KeyPair();
        byte[] requesterPub = rawPub(requesterKey);

        IdentityKeys handlerIdentity = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.torrents.add(torrent("ubuntu server", 500L, 1));
        RelaySearchService service = new RelaySearchService(index, handlerIdentity);

        KeyPair peerKey = generateEd25519KeyPair();
        PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
        directory.upsertVerified(rawPub(peerKey), "host", 6881);

        CapturingTransport transport = new CapturingTransport();
        IncomingSearchRequestHandler handler =
                new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
        handler.start();

        byte[][] path = {requesterPub};
        RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 0, path);
        transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

        assertEquals(1, transport.sent.size(), "only the local response, no forwards");
        RemoteSearchResponse response = SearchPayloadCodec.decodeResponse(transport.sent.get(0).payload);
        assertNotNull(response, "the single sent payload must be the response");
    }

    @Test
    void forwardSkipsPeersInPath() throws Exception {
        KeyPair requesterKey = generateEd25519KeyPair();
        byte[] requesterPub = rawPub(requesterKey);

        IdentityKeys handlerIdentity = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.torrents.add(torrent("ubuntu server", 500L, 1));
        RelaySearchService service = new RelaySearchService(index, handlerIdentity);

        byte[] peerAPub = rawPub(generateEd25519KeyPair());
        byte[] peerBPub = rawPub(generateEd25519KeyPair());
        PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
        directory.upsertVerified(peerAPub, "host-a", 6881);
        directory.upsertVerified(peerBPub, "host-b", 6882);

        CapturingTransport transport = new CapturingTransport();
        IncomingSearchRequestHandler handler =
                new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
        handler.start();

        byte[][] path = {requesterPub, peerAPub};
        RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 1, path);
        transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

        List<RemoteSearchRequest> forwarded = extractForwardedRequests(transport);
        assertEquals(1, forwarded.size(), "only peerB should receive a forward (peerA is in path)");
        CapturingTransport.SentPayload sp = findForwardTo(transport, peerBPub);
        assertNotNull(sp, "forwarded request must be sent to peerB");
        assertNull(findForwardTo(transport, peerAPub), "peerA is in the path and must be skipped");
    }

    @Test
    void forwardSelectsAtMostThreePeers() throws Exception {
        KeyPair requesterKey = generateEd25519KeyPair();
        byte[] requesterPub = rawPub(requesterKey);

        IdentityKeys handlerIdentity = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.torrents.add(torrent("ubuntu server", 500L, 1));
        RelaySearchService service = new RelaySearchService(index, handlerIdentity);

        PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
        for (int i = 0; i < 5; i++) {
            byte[] pub = rawPub(generateEd25519KeyPair());
            directory.upsertVerified(pub, "host-" + i, 6881 + i);
        }

        CapturingTransport transport = new CapturingTransport();
        IncomingSearchRequestHandler handler =
                new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
        handler.start();

        byte[][] path = {requesterPub};
        RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 1, path);
        transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

        List<RemoteSearchRequest> forwarded = extractForwardedRequests(transport);
        assertEquals(3, forwarded.size(), "at most 3 peers must be selected for forwarding");
    }

    @Test
    void forwardedRequestsAreResignedCorrectly() throws Exception {
        KeyPair requesterKey = generateEd25519KeyPair();
        byte[] requesterPub = rawPub(requesterKey);

        IdentityKeys handlerIdentity = IdentityKeys.generate();
        byte[] handlerPub = handlerIdentity.ed25519PubRaw();

        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.torrents.add(torrent("ubuntu server", 500L, 1));
        RelaySearchService service = new RelaySearchService(index, handlerIdentity);

        byte[] peerPub = rawPub(generateEd25519KeyPair());
        PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());
        directory.upsertVerified(peerPub, "host", 6881);

        CapturingTransport transport = new CapturingTransport();
        IncomingSearchRequestHandler handler =
                new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
        handler.start();

        byte[] nonce = new byte[32];
        nonce[0] = 0x42;
        byte[][] path = {requesterPub};
        RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 1, path, nonce);
        transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

        CapturingTransport.SentPayload sp = findForwardTo(transport, peerPub);
        assertNotNull(sp, "peer must receive a forwarded request");
        RemoteSearchRequest forwarded = SearchPayloadCodec.decodeRequest(sp.payload);
        assertNotNull(forwarded);
        assertEquals(0, forwarded.ttl(), "ttl must be decremented to 0");
        assertEquals(2, forwarded.pathLength(), "path must contain requester + handler pubkeys");
        assertArrayEquals(requesterPub, forwarded.path()[0]);
        assertArrayEquals(handlerPub, forwarded.path()[1]);
        assertArrayEquals(request.nonce(), forwarded.nonce(),
                "nonce must match the original request");
        assertTrue(verifySignature(forwarded, handlerPub),
                "forwarded request must be signed by the handler's Ed25519 key");
    }

    @Test
    void localResponseStillGoesThroughWhenNoPeersToForward() throws Exception {
        KeyPair requesterKey = generateEd25519KeyPair();
        byte[] requesterPub = rawPub(requesterKey);

        IdentityKeys handlerIdentity = IdentityKeys.generate();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        index.torrents.add(torrent("ubuntu server", 500L, 1));
        RelaySearchService service = new RelaySearchService(index, handlerIdentity);

        PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());

        CapturingTransport transport = new CapturingTransport();
        IncomingSearchRequestHandler handler =
                new IncomingSearchRequestHandler(transport, service, directory, handlerIdentity);
        handler.start();

        byte[][] path = {requesterPub};
        RemoteSearchRequest request = signedRequest(requesterKey, "ubuntu", 25, 1, path);
        transport.deliver(requesterPub, SearchPayloadCodec.encodeRequest(request));

        assertEquals(1, transport.sent.size(), "local response must be sent even with no peers");
        RemoteSearchResponse response = SearchPayloadCodec.decodeResponse(transport.sent.get(0).payload);
        assertNotNull(response, "the single sent payload must be the response");
        assertArrayEquals(request.nonce(), response.nonce(),
                "response nonce must match request nonce");
    }

    // --- helpers ---

    private static KeyPair generateEd25519KeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static byte[] rawPub(KeyPair kp) {
        return IdentityRecord.extractRawEd25519(kp.getPublic());
    }

    private static RemoteSearchRequest signedRequest(KeyPair requesterKey,
                                                     String keywords, int limit,
                                                     int ttl, byte[][] path) throws Exception {
        return signedRequest(requesterKey, keywords, limit, ttl, path, new byte[32]);
    }

    private static RemoteSearchRequest signedRequest(KeyPair requesterKey,
                                                     String keywords, int limit,
                                                     int ttl, byte[][] path,
                                                     byte[] nonce) throws Exception {
        byte[] requesterPub = rawPub(requesterKey);
        long ts = System.currentTimeMillis() / 1000L;
        RemoteSearchRequest unsigned = RemoteSearchRequest.builder()
                .keywords(keywords)
                .limit(limit)
                .nonce(nonce)
                .ttl(ttl)
                .requesterPub(requesterPub)
                .path(path)
                .timestamp(ts)
                .signature(new byte[64])
                .build();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(requesterKey.getPrivate());
        signer.update(unsigned.canonicalBytes());
        byte[] sig = signer.sign();
        return RemoteSearchRequest.builder()
                .keywords(keywords)
                .limit(limit)
                .nonce(nonce)
                .ttl(ttl)
                .requesterPub(requesterPub)
                .path(path)
                .timestamp(ts)
                .signature(sig)
                .build();
    }

    private static boolean verifySignature(RemoteSearchRequest request, byte[] expectedPubRaw)
            throws Exception {
        PublicKey pub = SearchResponseVerifier.rawEd25519ToPublicKey(expectedPubRaw);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pub);
        verifier.update(request.canonicalBytes());
        return verifier.verify(request.signature());
    }

    private static List<RemoteSearchRequest> extractForwardedRequests(CapturingTransport transport) {
        List<RemoteSearchRequest> out = new ArrayList<>();
        for (CapturingTransport.SentPayload sp : transport.sent) {
            RemoteSearchRequest req = SearchPayloadCodec.decodeRequest(sp.payload);
            if (req != null) {
                out.add(req);
            }
        }
        return out;
    }

    private static CapturingTransport.SentPayload findForwardTo(
            CapturingTransport transport, byte[] expectedTarget) {
        for (CapturingTransport.SentPayload sp : transport.sent) {
            if (Arrays.equals(sp.targetPub, expectedTarget)) {
                RemoteSearchRequest req = SearchPayloadCodec.decodeRequest(sp.payload);
                if (req != null) {
                    return sp;
                }
            }
        }
        return null;
    }

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

    private static final class CapturingTransport implements DistributedSearchTransport {
        private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();
        final List<SentPayload> sent = new CopyOnWriteArrayList<>();

        void deliver(byte[] sourcePub, byte[] payload) {
            for (PayloadListener l : listeners) {
                l.onPayload(sourcePub, payload, System.currentTimeMillis());
            }
        }

        @Override
        public boolean send(byte[] targetPub, byte[] payload) {
            sent.add(new SentPayload(targetPub, payload));
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

        static final class SentPayload {
            final byte[] targetPub;
            final byte[] payload;

            SentPayload(byte[] targetPub, byte[] payload) {
                this.targetPub = targetPub.clone();
                this.payload = payload.clone();
            }
        }
    }

    private static final class InMemoryLocalIndex implements LocalIndex {
        final List<LocalSharedTorrent> torrents = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void upsert(LocalSharedTorrent torrent) {
            torrents.removeIf(r -> r.infoHashHex().equals(torrent.infoHashHex()));
            torrents.add(torrent);
        }

        @Override
        public void delete(String infoHashHex) {
            torrents.removeIf(r -> r.infoHashHex().equalsIgnoreCase(infoHashHex));
        }

        @Override
        public Optional<LocalSharedTorrent> get(String infoHashHex) {
            for (LocalSharedTorrent r : torrents) {
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
            for (LocalSharedTorrent r : torrents) {
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
            return torrents.size();
        }
    }

    private static final class NoOpKarmaCache extends PeerKarmaCache {
        NoOpKarmaCache() {
            super(new RemoteKarmaChainFetcher(new KarmaChainSource() {
                @Override
                public Entry fetchManifest(byte[] peerPub) {
                    return null;
                }
            }));
        }

        @Override
        public long getKarma(byte[] peerPub) {
            return 0;
        }
    }
}
