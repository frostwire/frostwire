/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RelayRoleTest {

    private NoopKarmaCache karma;
    private NoopLocalIndex index;
    private PeerDirectory directory;
    private RelaySearchService service;
    private RelayRole role;

    @BeforeEach
    void setUp() throws Exception {
        karma = new NoopKarmaCache();
        index = new NoopLocalIndex();
        directory = new PeerDirectory(karma);
        service = new RelaySearchService(index, IdentityKeys.generate(4));
        role = new RelayRole(service, directory);
    }

    @Test
    void constructorRejectsNullArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelayRole(null, directory));
        assertThrows(IllegalArgumentException.class,
                () -> new RelayRole(service, null));
    }

    @Test
    void handleRequestRejectsNull() {
        assertTrue(role.handleRequest(null).isEmpty());
    }

    @Test
    void handleRequestAcceptsValidRequest() throws Exception {
        index.torrents.add(torrent("ubuntu", 1000, 1));
        KeyPairBag requester = new KeyPairBag();
        directory.upsert(requester.pub, "host", 6881);
        RemoteSearchRequest req = signRequest(requester, "ubuntu", 5);
        Optional<RemoteSearchResponse> resp = role.handleRequest(req);
        assertTrue(resp.isPresent());
    }

    @Test
    void handleRequestRejectsSpammer() throws Exception {
        index.torrents.add(torrent("ubuntu", 1000, 1));
        KeyPairBag requester = new KeyPairBag();
        directory.upsert(requester.pub, "host", 6881);
        directory.markSpam(requester.pub);
        RemoteSearchRequest req = signRequest(requester, "ubuntu", 5);
        assertTrue(role.handleRequest(req).isEmpty());
    }

    @Test
    void handleRequestAcceptsUnknownPeerByDefault() throws Exception {
        // No directory entry for the requester; trust score is 0
        // which is not below the default floor of 0, so the request
        // is accepted. Stricter policies can be enforced by setting
        // a positive trust floor.
        index.torrents.add(torrent("ubuntu", 1000, 1));
        KeyPairBag requester = new KeyPairBag();
        // Don't upsert into directory
        RemoteSearchRequest req = signRequest(requester, "ubuntu", 5);
        assertTrue(role.handleRequest(req).isPresent());
    }

    @Test
    void handleRequestRejectsUnknownPeerWithPositiveTrustFloor() throws Exception {
        index.torrents.add(torrent("ubuntu", 1000, 1));
        KeyPairBag requester = new KeyPairBag();
        RelayRole strictRole = new RelayRole(service, directory, 1);
        RemoteSearchRequest req = signRequest(requester, "ubuntu", 5);
        // Unknown peer has trust score 0, which is below the floor of 1
        assertTrue(strictRole.handleRequest(req).isEmpty());
    }

    @Test
    void handleRequestHonorsTrustFloor() throws Exception {
        index.torrents.add(torrent("ubuntu", 1000, 1));
        KeyPairBag requester = new KeyPairBag();
        directory.upsert(requester.pub, "host", 6881);
        // Default trust score is 1.0; with floor 2.0, request is rejected
        RelayRole strictRole = new RelayRole(service, directory, 2);
        RemoteSearchRequest req = signRequest(requester, "ubuntu", 5);
        assertTrue(strictRole.handleRequest(req).isEmpty());
    }

    @Test
    void handleRequestDelegatesToServiceForValidPeers() throws Exception {
        index.torrents.add(torrent("ubuntu", 1000, 1));
        index.torrents.add(torrent("debian", 1000, 1));
        KeyPairBag requester = new KeyPairBag();
        directory.upsert(requester.pub, "host", 6881);
        RemoteSearchRequest req = signRequest(requester, "ubuntu", 5);
        Optional<RemoteSearchResponse> resp = role.handleRequest(req);
        assertTrue(resp.isPresent());
        assertEquals(1, resp.get().rows().size());
        assertEquals("ubuntu", resp.get().rows().get(0).name);
    }

    @Test
    void forwardAppendsToPath() {
        byte[] a = new byte[32]; a[31] = 0x01;
        byte[] b = new byte[32]; b[31] = 0x02;
        byte[] nonce = new byte[32];
        RemoteSearchRequest req = RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(a)
                .ttl(2)
                .path(new byte[][]{b})
                .signature(new byte[64])
                .build();
        byte[] nextHop = new byte[32]; nextHop[31] = 0x03;
        RemoteSearchRequest forwarded = role.forward(req, nextHop);
        assertEquals(2, forwarded.pathLength());
        assertEquals(1, forwarded.ttl());
    }

    @Test
    void forwardRejectsLoop() {
        byte[] a = new byte[32]; a[31] = 0x01;
        byte[] b = new byte[32]; b[31] = 0x02;
        byte[] nonce = new byte[32];
        RemoteSearchRequest req = RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(a)
                .ttl(2)
                .path(new byte[][]{b})
                .signature(new byte[64])
                .build();
        // b is already in the path
        assertThrows(IllegalStateException.class, () -> role.forward(req, b));
    }

    @Test
    void forwardRejectsExhaustedTtl() {
        byte[] a = new byte[32]; a[31] = 0x01;
        byte[] nonce = new byte[32];
        RemoteSearchRequest req = RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(a)
                .ttl(0)
                .signature(new byte[64])
                .build();
        byte[] nextHop = new byte[32]; nextHop[31] = 0x03;
        assertThrows(IllegalStateException.class, () -> role.forward(req, nextHop));
    }

    @Test
    void forwardRejectsBadInputs() {
        byte[] nonce = new byte[32];
        byte[] a = new byte[32];
        RemoteSearchRequest req = RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(a)
                .ttl(2)
                .signature(new byte[64])
                .build();
        assertThrows(IllegalArgumentException.class, () -> role.forward(null, a));
        assertThrows(IllegalArgumentException.class, () -> role.forward(req, null));
        assertThrows(IllegalArgumentException.class, () -> role.forward(req, new byte[31]));
    }

    @Test
    void accessorsReturnNonNull() {
        assertNotNull(role.service());
        assertNotNull(role.directory());
        assertSame(service, role.service());
        assertSame(directory, role.directory());
    }

    // --- helpers ---

    private static RemoteSearchRequest signRequest(KeyPairBag requester,
                                                    String keywords, int limit) throws Exception {
        long ts = System.currentTimeMillis() / 1000L;
        byte[] nonce = new byte[32];
        for (int i = 0; i < 32; i++) nonce[i] = (byte) i;
        RemoteSearchRequest unsigned = RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(requester.pub)
                .keywords(keywords)
                .limit(limit)
                .timestamp(ts)
                .path(new byte[0][])
                .signature(new byte[64])
                .build();
        java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
        signer.initSign(requester.priv);
        signer.update(unsigned.canonicalBytes());
        byte[] sigBytes = signer.sign();
        return RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(requester.pub)
                .keywords(keywords)
                .limit(limit)
                .timestamp(ts)
                .path(new byte[0][])
                .signature(sigBytes)
                .build();
    }

    private static LocalSharedTorrent torrent(String name, long size, int fileCount) {
        byte[] hash = new byte[20];
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

    /** Bundle of a key pair and its raw public bytes. */
    private static final class KeyPairBag {
        final java.security.KeyPair pair;
        final byte[] pub;
        final java.security.PrivateKey priv;

        KeyPairBag() throws Exception {
            this.pair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            this.pub = IdentityRecord.extractRawEd25519(pair.getPublic());
            this.priv = pair.getPrivate();
        }
    }

    private static final class NoopKarmaCache extends PeerKarmaCache {
        NoopKarmaCache() {
            super(new RemoteKarmaChainFetcher(new KarmaChainSource() {
                @Override
                public Entry fetchManifest(byte[] peerPub) {
                    return null;
                }
            }));
        }
    }

    private static final class NoopLocalIndex implements LocalIndex {
        final List<LocalSharedTorrent> torrents = new ArrayList<>();
        final Map<String, LocalSharedTorrent> byHash = new HashMap<>();

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
                return new ArrayList<>();
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
            return new ArrayList<>();
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
