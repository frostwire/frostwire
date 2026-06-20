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

import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RelayRoleTest {

    private NoopKarmaCache karma;
    private NoopLocalIndex index;
    private PeerDirectory directory;
    private IdentityKeys identity;
    private RelaySearchService service;
    private RelayRole role;
    private RelayRole forwardingRole;

    @BeforeEach
    void setUp() throws Exception {
        karma = new NoopKarmaCache();
        index = new NoopLocalIndex();
        directory = new PeerDirectory(karma);
        identity = IdentityKeys.generate();
        service = new RelaySearchService(index, identity);
        role = new RelayRole(service, directory);
        forwardingRole = new RelayRole(service, directory, identity);
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
    void forwardRejectsNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> forwardingRole.forward(null));
    }

    @Test
    void forwardThrowsWithoutIdentity() {
        RelayRole noIdentityRole = new RelayRole(service, directory);
        RemoteSearchRequest req = buildForwardableRequest(new byte[32], 1, new byte[0][]);
        assertThrows(IllegalStateException.class, () -> noIdentityRole.forward(req));
    }

    @Test
    void forwardReturnsEmptyWhenTtlZero() throws Exception {
        KeyPairBag peerA = new KeyPairBag();
        directory.upsertVerified(peerA.pub, "host-a", 6881);
        RemoteSearchRequest req = buildForwardableRequest(new byte[32], 0, new byte[0][]);
        List<RelayRole.ForwardTarget> result = forwardingRole.forward(req);
        assertTrue(result.isEmpty());
    }

    @Test
    void forwardSelectsAtMostThreePeers() throws Exception {
        KeyPairBag peerA = new KeyPairBag();
        KeyPairBag peerB = new KeyPairBag();
        KeyPairBag peerC = new KeyPairBag();
        KeyPairBag peerD = new KeyPairBag();
        KeyPairBag peerE = new KeyPairBag();
        directory.upsertVerified(peerA.pub, "host-a", 6881);
        directory.upsertVerified(peerB.pub, "host-b", 6882);
        directory.upsertVerified(peerC.pub, "host-c", 6883);
        directory.upsertVerified(peerD.pub, "host-d", 6884);
        directory.upsertVerified(peerE.pub, "host-e", 6885);
        byte[] requesterPub = new byte[32];
        RemoteSearchRequest req = buildForwardableRequest(requesterPub, 2, new byte[0][]);
        List<RelayRole.ForwardTarget> result = forwardingRole.forward(req);
        assertEquals(RelayRole.MAX_FORWARD_TARGETS, result.size());
    }

    @Test
    void forwardSkipsPeersInPath() throws Exception {
        KeyPairBag peerA = new KeyPairBag();
        KeyPairBag peerB = new KeyPairBag();
        directory.upsertVerified(peerA.pub, "host-a", 6881);
        directory.upsertVerified(peerB.pub, "host-b", 6882);
        byte[] requesterPub = new byte[32];
        byte[][] path = {peerA.pub};
        RemoteSearchRequest req = buildForwardableRequest(requesterPub, 2, path);
        List<RelayRole.ForwardTarget> result = forwardingRole.forward(req);
        assertEquals(1, result.size());
        assertTrue(Arrays.equals(result.get(0).peerPub(), peerB.pub),
                "peerA is in the path and must be skipped");
    }

    @Test
    void forwardResignsWithOwnKey() throws Exception {
        KeyPairBag peerA = new KeyPairBag();
        directory.upsertVerified(peerA.pub, "host-a", 6881);
        byte[] requesterPub = new byte[32];
        RemoteSearchRequest req = buildForwardableRequest(requesterPub, 1, new byte[0][]);
        List<RelayRole.ForwardTarget> result = forwardingRole.forward(req);
        assertEquals(1, result.size());
        RelayRole.ForwardTarget target = result.get(0);
        RemoteSearchRequest forwarded = target.request();
        assertEquals(0, forwarded.ttl(), "ttl must be decremented");
        assertEquals(1, forwarded.pathLength(), "own pubkey must be appended to path");
        assertTrue(Arrays.equals(forwarded.path()[0], identity.ed25519PubRaw()),
                "path must contain this node's own pubkey");
        assertTrue(Arrays.equals(target.peerPub(), peerA.pub),
                "target must be the selected peer");
        assertTrue(verifySignature(forwarded, identity.ed25519PubRaw()),
                "forwarded request must be signed by this node's key");
    }

    @Test
    void accessorsReturnNonNull() {
        assertNotNull(role.service());
        assertNotNull(role.directory());
        assertSame(service, role.service());
        assertSame(directory, role.directory());
    }

    // --- helpers ---

    private static RemoteSearchRequest buildForwardableRequest(byte[] requesterPub,
                                                                int ttl, byte[][] path) {
        byte[] nonce = new byte[32];
        return RemoteSearchRequest.builder()
                .keywords("ubuntu")
                .limit(25)
                .nonce(nonce)
                .ttl(ttl)
                .requesterPub(requesterPub)
                .path(path)
                .timestamp(System.currentTimeMillis() / 1000L)
                .signature(new byte[64])
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
