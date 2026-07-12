/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.RelayMesh;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Multi-relay mesh E2E: pure FORWARDER backbone + seeder/searcher clients
 * on different relays.
 *
 * <pre>
 *   Searcher ──control──► Relay1 ──RELAY mesh──► Relay2 ──► Relay3 ◄──control── Seeder
 *                                                      (LocalIndex: "mesh-only-torrent")
 * </pre>
 *
 * <p>Searcher does not have a direct rUDP route to Seeder. Search is
 * multi-hop relayed; Seeder answers from its local index; response returns
 * through the mesh; Searcher verifies signed results.
 */
class MultiRelayMeshSearchTest {

    private final List<AutoCloseable> resources = new ArrayList<>();

    @AfterEach
    void tearDown() {
        Collections.reverse(resources);
        for (AutoCloseable r : resources) {
            try {
                r.close();
            } catch (Throwable ignored) {
            }
        }
    }

    @Test
    void searchCrossesThreeForwardersToPeerWithIndex() throws Exception {
        // --- 1. Three pure FORWARDER IceBridge nodes (mesh backbone) ---
        RelayNode r1 = startRelay("R1");
        RelayNode r2 = startRelay("R2");
        RelayNode r3 = startRelay("R3");

        int linked = RelayMesh.linkFully(RelayMesh.MeshNode.of(
                new RelayMesh.MeshNode("R1", r1.client, r1.server.identity().ed25519PubRaw(),
                        "127.0.0.1", r1.server.rudpPort()),
                new RelayMesh.MeshNode("R2", r2.client, r2.server.identity().ed25519PubRaw(),
                        "127.0.0.1", r2.server.rudpPort()),
                new RelayMesh.MeshNode("R3", r3.client, r3.server.identity().ed25519PubRaw(),
                        "127.0.0.1", r3.server.rudpPort())));
        assertTrue(linked >= 6, "full mesh should route each pair both ways; got " + linked);

        // Force HELLO/HELLO_ACK between every forwarder pair (direct DATA to known pubs).
        warmMeshSessions(r1, r2, r3);

        // --- 2. Seeder client on Relay3 (has LocalIndex) ---
        PeerClient seeder = startPeerClient("seeder", r3);
        seeder.index.upsert(torrent("mesh-only-torrent", 42_000L, 1));
        assertTrue(seeder.client.register(
                seeder.identity, "127.0.0.1", r3.server.rudpPort(), IceBridgeConfig.Role.CLIENT),
                "seeder must register on Relay3");

        // --- 3. Searcher client on Relay1 (empty index) ---
        PeerClient searcher = startPeerClient("searcher", r1);
        assertTrue(searcher.client.register(
                searcher.identity, "127.0.0.1", r1.server.rudpPort(), IceBridgeConfig.Role.CLIENT),
                "searcher must register on Relay1");

        // Multi-hop requirement: seeder is NOT registered on R1 (only on R3).
        assertTrue(r1.client.lookup(50).stream()
                        .noneMatch(p -> seeder.identity.ed25519PubRaw().length == 32
                                && java.util.Base64.getUrlDecoder()
                                .decode(p.pub).length == 32
                                && java.util.Arrays.equals(
                                java.util.Base64.getUrlDecoder().decode(p.pub),
                                seeder.identity.ed25519PubRaw())),
                "R1 must not know seeder - path must multi-hop via mesh");

        // Searcher "learned" seeder's identity (DHT / mesh import in production).
        searcher.directory.upsertVerified(
                seeder.identity.ed25519PubRaw(), "127.0.0.1", r3.server.rudpPort());

        // --- 4. Distributed search from searcher ---
        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer performer = new DistributedSearchPerformer(
                1L,
                "mesh-only",
                searcher.index,
                searcher.directory,
                searcher.identity,
                searcher.transport,
                5,
                50,
                25,
                20);
        performer.setListener(listener);
        performer.perform();

        assertEquals(1, listener.results.size(), "one result batch");
        List<SearchResult> results = listener.results.get(0);
        assertEquals(1, results.size(), "exactly one remote hit from seeder");
        assertEquals("mesh-only-torrent", results.get(0).getDisplayName());
        assertEquals(
                DistributedSearchPerformer.SOURCE_NAME,
                ((CompositeFileSearchResult) results.get(0)).getSource());
    }

    // ---- fixtures ----

    private RelayNode startRelay(String label) throws Exception {
        Path tmp = Files.createTempDirectory("mesh-relay-" + label + "-");
        Path idFile = tmp.resolve("identity.dat");
        IdentityKeys.save(IdentityKeys.generate(0), idFile.toFile());

        IceBridgeConfig config = IceBridgeConfig.newBuilder()
                .host("127.0.0.1")
                .rudpPort(freePort())
                .relayPort(freePort())
                .controlHttpPort(freePort())
                .role(IceBridgeConfig.Role.FORWARDER)
                .maxPeers(200)
                .peerTtlSec(300)
                .maxQpsPerKey(1000.0)
                .identityFile(idFile.toFile())
                .dhtEnabled(false)
                .build();

        IceBridgeServer server = new IceBridgeServer(config);
        server.start();
        resources.add(server);

        IceBridgeClient client = new IceBridgeClient(server.controlPort());
        client.setAuthToken(server.authToken());
        awaitHealthy(client, label);

        return new RelayNode(label, server, client);
    }

    private PeerClient startPeerClient(String label, RelayNode homeRelay) throws Exception {
        IdentityKeys identity = IdentityKeys.generate(0);
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());

        IceBridgeClient client = new IceBridgeClient(homeRelay.server.controlPort());
        client.setAuthToken(homeRelay.server.authToken());

        IceBridgeSearchTransport transport = new IceBridgeSearchTransport(client);
        transport.start();
        resources.add(transport);

        RelaySearchService service = new RelaySearchService(index, identity);
        IncomingSearchRequestHandler handler =
                new IncomingSearchRequestHandler(transport, service, directory, identity, index);
        handler.start();
        resources.add(handler::stop);

        return new PeerClient(label, identity, index, directory, client, transport);
    }

    private static void awaitHealthy(IceBridgeClient client, String label) throws Exception {
        boolean ok = false;
        for (int i = 0; i < 100; i++) {
            if (client.health()) {
                ok = true;
                break;
            }
            Thread.sleep(50);
        }
        assertTrue(ok, label + " control /health");
    }

    /**
     * Direct /send between mesh-routed forwarders triggers HELLO + HELLO_ACK
     * so initiator sessions learn remotePub before multi-hop RELAY.
     */
    private static void warmMeshSessions(RelayNode... nodes) throws Exception {
        byte[] ping = new byte[] {0x01};
        for (RelayNode a : nodes) {
            for (RelayNode b : nodes) {
                if (a == b) {
                    continue;
                }
                a.client.send(b.server.identity().ed25519PubRaw(),
                        com.frostwire.search.relay.icebridge.MeshProtocolId.TELEMETRY, ping);
            }
        }
        // Allow HELLO_ACK to settle on all pairs.
        for (int i = 0; i < 20; i++) {
            Thread.sleep(25);
        }
    }

    private static int freePort() throws java.io.IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger HASH =
            new java.util.concurrent.atomic.AtomicInteger();

    private static LocalSharedTorrent torrent(String name, long size, int files) {
        byte[] hash = new byte[20];
        int n = HASH.incrementAndGet();
        hash[0] = (byte) n;
        hash[1] = (byte) (n >> 8);
        byte[] pub = new byte[32];
        pub[0] = (byte) n;
        long now = System.currentTimeMillis() / 1000L;
        return new LocalSharedTorrent.Builder()
                .infoHash(hash)
                .name(name)
                .sizeBytes(size)
                .fileCount(files)
                .filesJson("[]")
                .publisherNodeId(new byte[20])
                .publisherEd25519Pub(pub)
                .publisherUtpPort(0)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    private static final class RelayNode {
        final String label;
        final IceBridgeServer server;
        final IceBridgeClient client;

        RelayNode(String label, IceBridgeServer server, IceBridgeClient client) {
            this.label = label;
            this.server = server;
            this.client = client;
        }
    }

    private static final class PeerClient {
        final String label;
        final IdentityKeys identity;
        final InMemoryLocalIndex index;
        final PeerDirectory directory;
        final IceBridgeClient client;
        final IceBridgeSearchTransport transport;

        PeerClient(String label, IdentityKeys identity, InMemoryLocalIndex index,
                   PeerDirectory directory, IceBridgeClient client,
                   IceBridgeSearchTransport transport) {
            this.label = label;
            this.identity = identity;
            this.index = index;
            this.directory = directory;
            this.client = client;
            this.transport = transport;
        }
    }

    private static final class RecordingListener implements SearchListener {
        final List<List<SearchResult>> results = new CopyOnWriteArrayList<>();

        @Override
        public void onResults(long token, List<? extends SearchResult> list) {
            results.add(new ArrayList<>(list));
        }

        @Override
        public void onError(long token, com.frostwire.search.SearchError error) {
        }

        @Override
        public void onStopped(long token) {
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
                    if (out.size() >= limit) {
                        break;
                    }
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
            super(new RemoteKarmaChainFetcher(peerPub -> null));
        }

        @Override
        public long getKarma(byte[] peerPub) {
            return 0;
        }
    }
}
