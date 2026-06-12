/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for the relay stack: spin up two or
 * more self-contained relay nodes, each on its own TCP port and
 * with its own Ed25519 identity and SQLite-backed local index.
 *
 * <p>Verifies the canonical publish / find / unpublish / not-find
 * flow that is the core value of the distributed relay search:
 * <ol>
 *   <li>Node A inserts a row into its local index.</li>
 *   <li>Node B (via {@link OutgoingRelayClient}) dials Node A's
 *       relay port, sends a signed {@link RemoteSearchRequest},
 *       and gets the row back.</li>
 *   <li>Node A removes the row from its index.</li>
 *   <li>Node B queries again and gets nothing.</li>
 * </ol>
 *
 * <p>Additional tests exercise the trust-graph: spam marking and
 * positive trust-floor rejection.
 *
 * <p>This test does NOT go through the DHT (which would need
 * {@code LocalDhtCluster}). It uses {@link OutgoingRelayClient}
 * directly against the other node's {@link IncomingRelayServer},
 * which is the protocol-level integration.
 */
class MultiInstanceRelayTest {

    @TempDir
    Path tempDir;

    private RelayNode nodeA;
    private RelayNode nodeB;
    private RelayNode nodeC;
    private KeyPairBag requester;

    @BeforeEach
    void setUp() throws Exception {
        // A requester identity used by clients when they query the
        // nodes. The "request from outside" viewpoint.
        requester = new KeyPairBag();
        // Three nodes, each on its own ephemeral port, each with a
        // shared requester identity so the directory's trust-floor
        // gate passes.
        nodeA = new RelayNode(tempDir.resolve("node-A").toFile(), 0, requester);
        nodeB = new RelayNode(tempDir.resolve("node-B").toFile(), 0, requester);
        nodeC = new RelayNode(tempDir.resolve("node-C").toFile(), 0, requester);
        // Each node learns about the other via the directory so the
        // default trust-floor of 0 isn't a regression in disguise.
        nodeA.directory.upsert(requester.pub, "test", 1);
        nodeB.directory.upsert(requester.pub, "test", 1);
        nodeC.directory.upsert(requester.pub, "test", 1);
    }

    @AfterEach
    void tearDown() {
        if (nodeA != null) nodeA.close();
        if (nodeB != null) nodeB.close();
        if (nodeC != null) nodeC.close();
    }

    // --- The core scenario the user asked for ---

    @Test
    void publishedTorrentIsDiscoverableThenUnpublishedRemovesIt() throws Exception {
        // 1. Node A publishes a torrent
        String name = "ubuntu-22.04-desktop-amd64";
        nodeA.publish(name, 1_000_000L, 1);
        String infoHashHex = nodeA.lastPublishedInfoHashHex;
        assertNotNull(infoHashHex);

        // 2. Node B queries Node A's relay server
        Optional<RemoteSearchResponse> resp = nodeB.search(nodeA, "ubuntu");
        assertTrue(resp.isPresent(), "Node B should find the published torrent");
        assertEquals(1, resp.get().rows().size());
        assertEquals(name, resp.get().rows().get(0).name);

        // 3. Node A unpublishes
        nodeA.unpublish(infoHashHex);

        // 4. Node B queries again — should find nothing
        Optional<RemoteSearchResponse> respAfter = nodeB.search(nodeA, "ubuntu");
        assertTrue(respAfter.isPresent(), "Server should still respond");
        assertEquals(0, respAfter.get().rows().size(),
                "After unpublish, Node B should find nothing");
    }

    @Test
    void multipleNodesHaveDistinctIdentities() {
        // Different nodes have different Ed25519 pubkeys (and thus
        // different "UUIDs" — the user's term for unique identity)
        assertFalse(java.util.Arrays.equals(nodeA.identity(), nodeB.identity()));
        assertFalse(java.util.Arrays.equals(nodeB.identity(), nodeC.identity()));
        assertFalse(java.util.Arrays.equals(nodeA.identity(), nodeC.identity()));
    }

    @Test
    void multipleNodesHaveDistinctPorts() {
        // Each node binds to a different ephemeral port
        assertNotEquals(nodeA.port(), nodeB.port());
        assertNotEquals(nodeB.port(), nodeC.port());
        assertNotEquals(nodeA.port(), nodeC.port());
    }

    @Test
    void nodeBCanQueryNodeCWhileNodeAIsolated() throws Exception {
        // Cross-pollination: Node A has data, Nodes B and C can both
        // find it independently. Search query is a complete word
        // within the name (FTS5 default tokenization does word-level
        // exact matching, not prefix).
        nodeA.publish("first torrent", 500_000L, 1);

        assertEquals(1, nodeB.search(nodeA, "first").get().rows().size());
        assertEquals(1, nodeC.search(nodeA, "first").get().rows().size());

        // Node B publishes, and any node (including Node A) can
        // query Node B and find it. The relay is public to known
        // requesters, regardless of who they are.
        nodeB.publish("second torrent", 600_000L, 1);
        assertEquals(1, nodeC.search(nodeB, "second").get().rows().size());
        assertEquals(1, nodeA.search(nodeB, "second").get().rows().size(),
                "Node A queries Node B; Node B has 'second torrent' — " +
                        "should find it (relay serves anyone who asks)");
    }

    @Test
    void eachNodeOnlyFindsWhatItHosts() throws Exception {
        // Partition: each node has its own torrent, others can't
        // find it through the wrong node. Search query is a complete
        // word within the name (FTS5 default tokenization does word-
        // level exact matching, not prefix).
        nodeA.publish("alpha torrent", 1_000L, 1);
        nodeB.publish("bravo torrent", 2_000L, 1);
        nodeC.publish("charlie torrent", 3_000L, 1);

        // A's content is only on A
        assertEquals(1, nodeA.search(nodeA, "alpha").get().rows().size());
        assertEquals(0, nodeA.search(nodeB, "alpha").get().rows().size());
        assertEquals(0, nodeA.search(nodeC, "alpha").get().rows().size());

        // B's content is only on B
        assertEquals(0, nodeB.search(nodeA, "bravo").get().rows().size());
        assertEquals(1, nodeB.search(nodeB, "bravo").get().rows().size());
        assertEquals(0, nodeB.search(nodeC, "bravo").get().rows().size());

        // C's content is only on C
        assertEquals(0, nodeC.search(nodeA, "charlie").get().rows().size());
        assertEquals(0, nodeC.search(nodeB, "charlie").get().rows().size());
        assertEquals(1, nodeC.search(nodeC, "charlie").get().rows().size());
    }

    // --- Trust-graph behavior across instances ---
    //
    // The trust check happens in the TARGET node's RelayRole: when
    // Node A receives a request, it looks up the requester's pubkey
    // in its OWN PeerDirectory. So the spam/floor tests mark the
    // requester in the target node's directory, not in the
    // querier's directory.

    @Test
    void spamMarkedRequesterIsRejected() throws Exception {
        nodeA.publish("evil-payload", 100L, 1);

        // Node A marks the requester (Node B's identity) as spam
        // in its OWN directory. Node A's RelayRole will reject the
        // request before it reaches the service.
        nodeA.directory.markSpam(requester.pub);

        // Node B (the requester) tries to query Node A
        Optional<RemoteSearchResponse> resp = nodeB.search(nodeA, "evil");
        assertTrue(resp.isEmpty(),
                "Spam-marked requester must be rejected before service dispatch");
    }

    @Test
    void unSpammingRestoresAccess() throws Exception {
        nodeA.publish("legit-payload", 100L, 1);

        nodeA.directory.markSpam(requester.pub);
        assertTrue(nodeB.search(nodeA, "legit").isEmpty());

        // Un-spam: re-upsert with the same pubkey (upsert replaces)
        nodeA.directory.upsert(requester.pub, "host", 6888);
        Optional<RemoteSearchResponse> resp = nodeB.search(nodeA, "legit");
        assertTrue(resp.isPresent());
        assertEquals(1, resp.get().rows().size());
    }

    @Test
    void unknownRequesterIsAcceptedByDefault() throws Exception {
        // Don't pre-register the requester in Node A's directory.
        // In setUp() we did register the requester in every node's
        // directory, so revoke that entry here.
        nodeA.directory.evict(requester.pub);
        nodeA.publish("anonymous", 100L, 1);

        Optional<RemoteSearchResponse> resp = nodeB.search(nodeA, "anonymous");
        assertTrue(resp.isPresent(),
                "Unknown requester with default floor=0 should be accepted");
        assertEquals(1, resp.get().rows().size());
    }

    @Test
    void positiveTrustFloorRejectsUnknownRequester() throws Exception {
        // Recreate Node A with a positive trust floor — the trust
        // check is in the target's RelayRole, so the target needs
        // the stricter policy.
        nodeA.close();
        nodeA = new RelayNode(tempDir.resolve("node-A-strict").toFile(), 1, requester);
        nodeA.directory.upsert(requester.pub, "test", 1);

        // Revoke the requester so trust = 0 (below floor=1)
        nodeA.directory.evict(requester.pub);

        nodeA.publish("guarded", 100L, 1);
        Optional<RemoteSearchResponse> resp = nodeB.search(nodeA, "guarded");
        assertTrue(resp.isEmpty(),
                "Unknown requester should be rejected when target floor > 0");
    }

    // --- Helpers ---

    /**
     * Self-contained relay node: home dir, identity, local index,
     * directory, role, server. Each instance binds to a different
     * ephemeral TCP port.
     */
    private static final class RelayNode implements AutoCloseable {
        final File homeDir;
        final IdentityKeys identity;
        final LocalIndexTable index;
        final PeerDirectory directory;
        final RelaySearchService service;
        final RelayRole role;
        final IncomingRelayServer server;
        final int port;
        private         final AtomicInteger infoHashCounter = new AtomicInteger();
        String lastPublishedInfoHashHex;
        final KeyPairBag defaultRequester;

        RelayNode(File homeDir) throws Exception {
            this(homeDir, 0, null); // default trust floor
        }

        RelayNode(File homeDir, int trustFloor) throws Exception {
            this(homeDir, trustFloor, null);
        }

        RelayNode(File homeDir, int trustFloor, KeyPairBag requester) throws Exception {
            this.homeDir = homeDir;
            if (!homeDir.exists() && !homeDir.mkdirs()) {
                throw new IllegalStateException("Could not create " + homeDir);
            }
            // Low PoW difficulty for fast test runs (~2-3 ms).
            this.identity = IdentityKeys.generate(4);
            this.index = LocalIndexTable.open(
                    new File(homeDir, LocalIndexTable.DEFAULT_DB_NAME));
            this.directory = new PeerDirectory(new NoopKarmaCache());
            this.service = new RelaySearchService(index, identity);
            this.role = new RelayRole(service, directory, trustFloor);
            this.server = new IncomingRelayServer(role, 0); // OS picks port
            this.server.start();
            this.port = server.port();
            this.defaultRequester = requester; // may be null; tests can pass an explicit one
        }

        void publish(String name, long size, int fileCount) {
            int n = infoHashCounter.incrementAndGet();
            byte[] infoHash = new byte[20];
            infoHash[0] = (byte) (n >>> 24);
            infoHash[1] = (byte) (n >>> 16);
            infoHash[2] = (byte) (n >>> 8);
            infoHash[3] = (byte) n;
            byte[] publisherPub = new byte[32];
            byte[] nodeId = new byte[20];
            long now = System.currentTimeMillis() / 1000L;
            LocalSharedTorrent torrent = new LocalSharedTorrent.Builder()
                    .infoHash(infoHash)
                    .name(name)
                    .sizeBytes(size)
                    .fileCount(fileCount)
                    .filesJson("[]")
                    .publisherNodeId(nodeId)
                    .publisherEd25519Pub(publisherPub)
                    .publisherUtpPort(0)
                    .addedAt(now)
                    .lastSeenAt(now)
                    .build();
            index.upsert(torrent);
            this.lastPublishedInfoHashHex = com.frostwire.util.Hex.encode(infoHash);
        }

        void unpublish(String infoHashHex) {
            index.delete(infoHashHex);
            this.lastPublishedInfoHashHex = null;
        }

        Optional<RemoteSearchResponse> search(RelayNode target, String keywords)
                throws Exception {
            return searchAs(target, keywords, makeRequester());
        }

        Optional<RemoteSearchResponse> searchAs(RelayNode target, String keywords,
                                                KeyPairBag requester) throws Exception {
            long ts = System.currentTimeMillis() / 1000L;
            byte[] nonce = new byte[32];
            for (int i = 0; i < 32; i++) nonce[i] = (byte) i;
            RemoteSearchRequest unsigned = RemoteSearchRequest.builder()
                    .nonce(nonce)
                    .requesterPub(requester.pub)
                    .keywords(keywords)
                    .limit(25)
                    .timestamp(ts)
                    .path(new byte[0][])
                    .signature(new byte[64])
                    .build();
            java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
            signer.initSign(requester.priv);
            signer.update(unsigned.canonicalBytes());
            RemoteSearchRequest signed = RemoteSearchRequest.builder()
                    .nonce(nonce)
                    .requesterPub(requester.pub)
                    .keywords(keywords)
                    .limit(25)
                    .timestamp(ts)
                    .path(new byte[0][])
                    .signature(signer.sign())
                    .build();
            return new OutgoingRelayClient().send("127.0.0.1", target.port, signed);
        }

        private KeyPairBag makeRequester() {
            return defaultRequester;
        }

        byte[] identity() {
            return identity.ed25519PubRaw();
        }

        int port() {
            return port;
        }

        @Override
        public void close() {
            try { server.stop(); } catch (Throwable ignored) {}
            try { index.close(); } catch (Throwable ignored) {}
        }
    }

    /** Reusable key pair + raw pub for a requester identity. */
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

    /** Karma cache that never returns a chain — peers are scored 0. */
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
}
