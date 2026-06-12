/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.tests.dht.LocalDhtCluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless end-to-end smoke test of two FrostWire relay nodes
 * wired through a real (in-process) DHT.
 *
 * <p>Uses {@link LocalDhtCluster} for the SessionManager — each
 * relay node has a real DHT-capable jlibtorrent session. The
 * cluster's DHT is on a local-only bootstrap which is not a
 * full BEP 5 routing network, so we don't assert DHT-mediated
 * discovery round-trips. We DO assert:
 * <ul>
 *   <li>The relay protocol between two nodes with distinct
 *       identities works end-to-end (publish / find /
 *       unpublish / not-find) — see
 *       {@link MultiInstanceRelayTest} for the canonical case
 *       and this class for the SessionManager-backed case.</li>
 *   <li>The DHT advertiser runs without error and exercises
 *       the BEP 5 / BEP 46 code paths against the real
 *       SessionManager.</li>
 *   <li>The DHT discovery runs without error and the
 *       source/fetcher code paths are exercised.</li>
 * </ul>
 *
 * <p>For the actual two-instance DHT round-trip (advertise
 * → discover → query), a real DHT network is required. The
 * {@link LocalDhtCluster} is suitable for DHT primitive
 * testing but its localhost-only bootstrap doesn't form a
 * proper BEP 5 routing network. The eventual verification of
 * the cross-machine scenario is a manual smoke test, since it
 * requires two real FrostWire desktop instances connecting
 * through the public DHT.
 */
@ExtendWith(LocalDhtCluster.class)
class TwoInstanceDhtSmokeTest {

    private DhtRelayNode nodeA;
    private DhtRelayNode nodeB;
    private LocalDhtCluster cluster;

    @BeforeEach
    void setUp(LocalDhtCluster cluster) throws Exception {
        this.cluster = cluster;
        // Each relay node has its own SessionManager. This
        // mirrors production: each FrostWire instance has its
        // own BTEngine, hence its own DHT. The cluster
        // provides 3 nodes that bootstrap from each other so
        // the DHTs have some initial connectivity.
        nodeA = new DhtRelayNode(cluster.getNode(0), "A");
        nodeB = new DhtRelayNode(cluster.getNode(1), "B");
    }

    @AfterEach
    void tearDown() {
        if (nodeA != null) nodeA.close();
        if (nodeB != null) nodeB.close();
    }

    @Test
    void twoNodesDoFullPublishDiscoverRelayQueryFlow() throws Exception {
        // 1. Node A publishes a torrent to its local index.
        nodeA.publish("alpha torrent", 1_000L, 1);

        // 2. Node A advertises (BEP 5 + BEP 46) via the cluster
        //    SessionManager. We don't assert DHT round-trip
        //    success here (the cluster's localhost-only bootstrap
        //    doesn't form a BEP 5 routing network). We just
        //    verify the code path runs without error.
        nodeA.advertise();

        // 3. Node B runs a discovery pass.
        nodeB.discover();

        // 4. Node B dials Node A's relay port and queries. The
        //    relay protocol doesn't go through the DHT — it uses
        //    plain TCP. Node B knows the port from the relay
        //    server construction (port 0 → OS-assigned ephemeral).
        Optional<RemoteSearchResponse> resp =
                nodeB.queryNodeA(nodeA, "alpha");
        assertTrue(resp.isPresent(), "Node A's relay server should respond");
        assertEquals(1, resp.get().rows().size());
        assertEquals("alpha torrent", resp.get().rows().get(0).name);

        // 5. Node A unpublishes.
        nodeA.unpublish("alpha torrent");

        // 6. Node B queries again — should find nothing.
        nodeA.advertise();
        nodeB.discover();
        Optional<RemoteSearchResponse> respAfter =
                nodeB.queryNodeA(nodeA, "alpha");
        assertTrue(respAfter.isPresent(), "Node A's relay server should still respond");
        assertEquals(0, respAfter.get().rows().size(),
                "After unpublish, Node B should find nothing");
    }

    @Test
    void nodeAAdvertiseRunsWithoutErrorOnRealSession() {
        // Single-node: just verify the advertiser exercises both
        // the BEP 5 announce and the BEP 46 publish against a
        // real SessionManager without throwing.
        nodeA.advertise();
        // Verify the advertiser's internal state was updated.
        assertTrue(nodeA.identityPublisher.lastPublishEpochSec() > 0,
                "Identity publish should have set lastPublishEpochSec");
    }

    @Test
    void nodeBDiscoveryRunsWithoutErrorOnRealSession() {
        // Node B's discovery pass returns an empty list (no
        // peers to discover in the localhost-only cluster) but
        // doesn't throw.
        var discovered = nodeB.discover();
        assertNotNull(discovered);
    }

    /**
     * A self-contained relay node backed by a real DHT
     * SessionManager from the {@link LocalDhtCluster}.
     */
    private static final class DhtRelayNode implements AutoCloseable {
        final String label;
        final SessionManager session;
        final IdentityKeys identity;
        final LocalIndexTable index;
        final PeerDirectory directory;
        final RelaySearchService service;
        final RelayRole role;
        final IncomingRelayServer server;
        final IdentityRecordPublisher identityPublisher;
        final int relayPort;
        private final AtomicInteger infoHashCounter = new AtomicInteger();
        private String lastPublishedInfoHashHex;

        DhtRelayNode(SessionManager session, String label) throws Exception {
            this.label = label;
            this.session = session;
            this.identity = IdentityKeys.generate(4); // low PoW for fast tests
            this.index = LocalIndexTable.open(
                    new java.io.File(System.getProperty("java.io.tmpdir"),
                            "dht-relay-smoke-" + label + "-"
                                    + System.nanoTime() + ".db"));
            this.directory = new PeerDirectory(new NoopKarmaCache());
            this.service = new RelaySearchService(index, identity);
            this.role = new RelayRole(service, directory);
            this.server = new IncomingRelayServer(role, 0);
            this.server.start();
            this.relayPort = server.port();
            this.identityPublisher = new IdentityRecordPublisher(identity, relayPort);
        }

        void advertise() {
            try {
                // Force-publish identity (no throttle) and announce
                // at the relay port. The actual DHT round-trip is
                // async; we don't wait for completion.
                identityPublisher.publish(session);
                DhtRendezvous.announcePeer(session, relayPort);
            } catch (Throwable t) {
                throw new RuntimeException("advertise failed", t);
            }
        }

        List<DiscoveredEndpoint> discover() {
            try {
                DhtPeerDiscoverySource source = new DhtPeerDiscoverySource(session);
                PeerDiscovery discovery = new PeerDiscovery(source, directory);
                return discovery.discoverAndRegister();
            } catch (Throwable t) {
                throw new RuntimeException("discover failed", t);
            }
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
            lastPublishedInfoHashHex = com.frostwire.util.Hex.encode(infoHash);
        }

        void unpublish(String name) {
            if (lastPublishedInfoHashHex == null) {
                return;
            }
            index.delete(lastPublishedInfoHashHex);
            lastPublishedInfoHashHex = null;
        }

        Optional<RemoteSearchResponse> queryNodeA(DhtRelayNode target, String keywords)
                throws Exception {
            KeyPair kp = java.security.KeyPairGenerator.getInstance("Ed25519")
                    .generateKeyPair();
            byte[] requesterPub = IdentityRecord.extractRawEd25519(kp.getPublic());
            long ts = System.currentTimeMillis() / 1000L;
            byte[] nonce = new byte[32];
            for (int i = 0; i < 32; i++) nonce[i] = (byte) i;
            RemoteSearchRequest unsigned = RemoteSearchRequest.builder()
                    .nonce(nonce)
                    .requesterPub(requesterPub)
                    .keywords(keywords)
                    .limit(25)
                    .timestamp(ts)
                    .path(new byte[0][])
                    .signature(new byte[64])
                    .build();
            java.security.Signature signer = java.security.Signature
                    .getInstance("Ed25519");
            signer.initSign(kp.getPrivate());
            signer.update(unsigned.canonicalBytes());
            RemoteSearchRequest signed = RemoteSearchRequest.builder()
                    .nonce(nonce)
                    .requesterPub(requesterPub)
                    .keywords(keywords)
                    .limit(25)
                    .timestamp(ts)
                    .path(new byte[0][])
                    .signature(signer.sign())
                    .build();
            return new OutgoingRelayClient().send("127.0.0.1", target.relayPort, signed);
        }

        @Override
        public void close() {
            try { server.stop(); } catch (Throwable ignored) {}
            try { index.close(); } catch (Throwable ignored) {}
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
}
