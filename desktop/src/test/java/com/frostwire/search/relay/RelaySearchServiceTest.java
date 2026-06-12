/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RelaySearchServiceTest {

    private static KeyPair requesterKey;
    private static byte[] requesterPub;
    private static IdentityKeys responderIdentity;

    @BeforeAll
    static void setUpClass() throws Exception {
        requesterKey = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        requesterPub = IdentityRecord.extractRawEd25519(requesterKey.getPublic());
        responderIdentity = IdentityKeys.generate(4);
    }

    private NoopLocalIndex index;
    private RelaySearchService service;

    @BeforeEach
    void setUp() throws Exception {
        index = new NoopLocalIndex();
        service = new RelaySearchService(index, responderIdentity);
    }

    @Test
    void constructorRejectsNullArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelaySearchService(null, responderIdentity));
        assertThrows(IllegalArgumentException.class,
                () -> new RelaySearchService(index, null));
    }

    @Test
    void handleRejectsNullRequest() {
        assertTrue(service.handle(null).isEmpty());
    }

    @Test
    void handleAcceptsValidSignedRequest() throws Exception {
        index.torrents.add(torrent("ubuntu", 1000, 1));
        RemoteSearchRequest req = signedRequest("ubuntu", 5);
        Optional<RemoteSearchResponse> resp = service.handle(req);
        assertTrue(resp.isPresent());
        assertEquals(1, resp.get().rows().size());
        assertEquals("ubuntu", resp.get().rows().get(0).name);
        // Response signature must verify
        assertTrue(verifyResponseSignature(resp.get()));
    }

    @Test
    void handleRejectsRequestWithBadSignature() throws Exception {
        RemoteSearchRequest req = signedRequest("ubuntu", 5);
        // Tamper with the signature
        RemoteSearchRequest tampered = RemoteSearchRequest.builder()
                .nonce(req.nonce())
                .requesterPub(req.requesterPub())
                .keywords(req.keywords())
                .limit(req.limit())
                .timestamp(req.timestamp())
                .path(new byte[0][])
                .signature(tamperSignature(req.signature()))
                .build();
        assertTrue(service.handle(tampered).isEmpty());
    }

    @Test
    void handleRejectsRequestWithBadPublicKey() throws Exception {
        // Build a request signed by requesterKey but with a DIFFERENT pub in the field
        KeyPair otherKey = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] otherPub = IdentityRecord.extractRawEd25519(otherKey.getPublic());
        RemoteSearchRequest req = RemoteSearchRequest.builder()
                .nonce(new byte[32])
                .requesterPub(otherPub) // mismatched
                .keywords("x")
                .limit(5)
                .timestamp(System.currentTimeMillis() / 1000L)
                .signature(new byte[64]) // invalid
                .build();
        // Manually sign with requesterKey, but use otherPub as the "requesterPub"
        // — this should fail because the public key in the request doesn't match the
        // signing key.
        byte[] canonical = req.canonicalBytes();
        byte[] sig = signWithKey(canonical, requesterKey.getPrivate());
        RemoteSearchRequest signed = RemoteSearchRequest.builder()
                .nonce(req.nonce())
                .requesterPub(req.requesterPub())
                .keywords(req.keywords())
                .limit(req.limit())
                .timestamp(req.timestamp())
                .path(new byte[0][])
                .signature(sig)
                .build();
        assertTrue(service.handle(signed).isEmpty());
    }

    @Test
    void handleRejectsStaleRequest() throws Exception {
        // Build a request with a timestamp 1 hour in the past
        long staleTs = (System.currentTimeMillis() / 1000L) - 3600;
        RemoteSearchRequest req = signWithTs("ubuntu", staleTs);
        assertTrue(service.handle(req).isEmpty());
    }

    @Test
    void handleRejectsFarFutureRequest() throws Exception {
        long futureTs = (System.currentTimeMillis() / 1000L) + 3600;
        RemoteSearchRequest req = signWithTs("ubuntu", futureTs);
        assertTrue(service.handle(req).isEmpty());
    }

    @Test
    void handleEnforcesRateLimit() throws Exception {
        // Service uses default 5 QPS bucket. After 5 requests, the 6th
        // is rejected.
        for (int i = 0; i < 5; i++) {
            Optional<RemoteSearchResponse> r = service.handle(signedRequest("u" + i, 1));
            assertTrue(r.isPresent(), "request " + i + " must be allowed");
        }
        Optional<RemoteSearchResponse> sixth = service.handle(signedRequest("u5", 1));
        assertTrue(sixth.isEmpty(), "6th request must be rate-limited");
    }

    @Test
    void handleRespectsRequestLimit() throws Exception {
        index.torrents.add(torrent("a", 100, 1));
        index.torrents.add(torrent("a", 200, 1));
        index.torrents.add(torrent("a", 300, 1));
        index.torrents.add(torrent("a", 400, 1));
        index.torrents.add(torrent("a", 500, 1));
        // Request limit 3, but the responder caps at MAX_LIMIT
        // and the index returns up to 3 (the test index respects limit).
        RemoteSearchRequest req = signedRequest("a", 3);
        Optional<RemoteSearchResponse> r = service.handle(req);
        assertTrue(r.isPresent());
        assertTrue(r.get().rows().size() <= 3);
    }

    @Test
    void handleCapsRequestLimit() throws Exception {
        for (int i = 0; i < 200; i++) {
            index.torrents.add(torrent("a" + i, 100, 1));
        }
        // Request limit MAX_LIMIT (100); service respects the request's limit
        // since it's already at the cap. We add 200 torrents to the index but
        // the in-test index returns at most `limit` rows, so the response
        // must not exceed the cap.
        RemoteSearchRequest req = signedRequest("a", RemoteSearchRequest.MAX_LIMIT);
        Optional<RemoteSearchResponse> r = service.handle(req);
        assertTrue(r.isPresent());
        assertTrue(r.get().rows().size() <= RemoteSearchRequest.MAX_LIMIT);
    }

    @Test
    void handleReturnsEmptyForEmptyIndex() throws Exception {
        RemoteSearchRequest req = signedRequest("nothing", 10);
        Optional<RemoteSearchResponse> r = service.handle(req);
        assertTrue(r.isPresent());
        assertEquals(0, r.get().rows().size());
    }

    @Test
    void handleResponseNonceMatchesRequest() throws Exception {
        index.torrents.add(torrent("ubuntu", 1000, 1));
        RemoteSearchRequest req = signedRequest("ubuntu", 5);
        byte[] reqNonce = req.nonce();
        Optional<RemoteSearchResponse> r = service.handle(req);
        assertTrue(r.isPresent());
        assertArrayEquals(reqNonce, r.get().nonce());
    }

    @Test
    void rateLimiterAccessorReturnsNonNull() {
        assertNotNull(service.rateLimiter());
    }

    // --- helpers ---

    private static RemoteSearchRequest signedRequest(String keywords, int limit) throws Exception {
        long ts = System.currentTimeMillis() / 1000L;
        return signWithTs(keywords, ts, limit);
    }

    private static RemoteSearchRequest signWithTs(String keywords, long ts) throws Exception {
        return signWithTs(keywords, ts, 5);
    }

    private static RemoteSearchRequest signWithTs(String keywords, long ts, int limit) throws Exception {
        byte[] nonce = new byte[32];
        for (int i = 0; i < 32; i++) nonce[i] = (byte) i;
        RemoteSearchRequest unsigned = RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(requesterPub)
                .keywords(keywords)
                .limit(limit)
                .timestamp(ts)
                .path(new byte[0][])
                .signature(new byte[64])
                .build();
        byte[] sig = signWithKey(unsigned.canonicalBytes(), requesterKey.getPrivate());
        return RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(requesterPub)
                .keywords(keywords)
                .limit(limit)
                .timestamp(ts)
                .path(new byte[0][])
                .signature(sig)
                .build();
    }

    private static byte[] signWithKey(byte[] data, java.security.PrivateKey key) throws Exception {
        java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(data);
        return signer.sign();
    }

    private static boolean verifyResponseSignature(RemoteSearchResponse response) throws Exception {
        byte[] responderPubRaw = responderIdentity.ed25519PubRaw();
        byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
        byte[] encoded = new byte[prefix.length + responderPubRaw.length];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(responderPubRaw, 0, encoded, prefix.length, responderPubRaw.length);
        java.security.PublicKey pub = java.security.KeyFactory.getInstance("Ed25519")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(encoded));
        java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
        verifier.initVerify(pub);
        verifier.update(response.canonicalBytes());
        return verifier.verify(response.signature());
    }

    private static byte[] tamperSignature(byte[] sig) {
        byte[] out = sig.clone();
        out[0] ^= 1;
        return out;
    }

    private static LocalSharedTorrent torrent(String name, long size, int fileCount) {
        byte[] hash = new byte[20];
        for (int i = 0; i < 20; i++) hash[i] = (byte) i;
        byte[] pub = new byte[32];
        byte[] nodeId = new byte[20];
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

    private static final class NoopLocalIndex implements LocalIndex {
        final List<LocalSharedTorrent> torrents = new ArrayList<>();

        @Override
        public void upsert(LocalSharedTorrent torrent) {
        }

        @Override
        public void delete(String infoHashHex) {
        }

        @Override
        public Optional<LocalSharedTorrent> get(String infoHashHex) {
            return Optional.empty();
        }

        @Override
        public List<LocalSharedTorrent> search(String query, int limit) {
            if (query == null || query.isEmpty()) {
                return Collections.emptyList();
            }
            String q = query.toLowerCase();
            List<LocalSharedTorrent> out = new ArrayList<>();
            for (LocalSharedTorrent t : torrents) {
                if (t.name().toLowerCase().contains(q)) {
                    out.add(t);
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
}
