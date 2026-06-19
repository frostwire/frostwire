/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless multi-instance integration test that simulates two FrostWire
 * desktop instances running on the same machine with different user
 * directories, identities, ports, and local indexes.
 *
 * <p>Each instance gets its own in-process {@link IceBridgeServer} (rUDP +
 * HTTP control API), {@link IceBridgeSearchTransport}, and
 * {@link IncomingSearchRequestHandler}. The test simulates DHT discovery
 * by cross-registering identities in each other's {@link PeerDirectory},
 * routes peers into each daemon's registry via {@code /route}, and then
 * performs a distributed search from instance A.
 *
 * <p>Verified flow:
 * <ol>
 *   <li>A's performer queries A's local index → "ubuntu server"</li>
 *   <li>A's performer sends signed request to B via IceBridge rUDP</li>
 *   <li>B's IncomingSearchRequestHandler processes the request, queries
 *       B's local index, signs the response</li>
 *   <li>B sends the signed response back to A via IceBridge rUDP</li>
 *   <li>A's performer verifies the response and merges results</li>
 *   <li>Final result set contains both "ubuntu server" (local) and
 *       "ubuntu package" (remote from B)</li>
 * </ol>
 */
class MultiInstanceDistributedSearchTest {

    private final List<AutoCloseable> resources = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // Close in reverse order of creation.
        Collections.reverse(resources);
        for (AutoCloseable r : resources) {
            try {
                r.close();
            } catch (Throwable ignored) {
            }
        }
    }

    @Test
    void twoInstancesSearchEachOther() throws Exception {
        // --- 1. Start two IceBridge servers with different identities ---

        Instance nodeA = startInstance("A");
        Instance nodeB = startInstance("B");

        // --- 2. Simulate DHT discovery: cross-register identities ---

        nodeA.directory.upsertVerified(
                nodeB.identity.ed25519PubRaw(), "127.0.0.1", nodeB.rudpPort);
        nodeB.directory.upsertVerified(
                nodeA.identity.ed25519PubRaw(), "127.0.0.1", nodeA.rudpPort);

        // --- 3. Route peers into each daemon's registry via /route ---

        assertTrue(nodeA.client.route(
                nodeB.identity.ed25519PubRaw(), "127.0.0.1", nodeB.rudpPort,
                IceBridgeConfig.Role.BOTH),
                "Node A should route Node B into its daemon");
        assertTrue(nodeB.client.route(
                nodeA.identity.ed25519PubRaw(), "127.0.0.1", nodeA.rudpPort,
                IceBridgeConfig.Role.BOTH),
                "Node B should route Node A into its daemon");

        // --- 4. Populate local indexes with different torrents ---

        nodeA.index.upsert(torrent("ubuntu server", 1_000L, 1));
        nodeB.index.upsert(torrent("ubuntu package", 2_000L, 2));

        // --- 5. Perform distributed search from Node A ---

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer performer = new DistributedSearchPerformer(
                1L, "ubuntu", nodeA.index, nodeA.directory,
                nodeA.identity, nodeA.transport,
                5, 50, 25, 15);
        performer.setListener(listener);
        performer.perform();

        // --- 6. Verify results ---

        assertEquals(1, listener.results.size(), "performer should deliver one result batch");
        List<SearchResult> results = listener.results.get(0);
        assertEquals(2, results.size(), "should have local + remote results");

        List<String> names = results.stream()
                .map(SearchResult::getDisplayName)
                .sorted()
                .toList();

        assertEquals("ubuntu package", names.get(0), "remote result from Node B");
        assertEquals("ubuntu server", names.get(1), "local result from Node A");

        // Verify both are tagged as Distributed source.
        for (SearchResult sr : results) {
            assertEquals(DistributedSearchPerformer.SOURCE_NAME,
                    ((CompositeFileSearchResult) sr).getSource(),
                    "all results should be tagged Distributed");
        }
    }

    @Test
    void searchReturnsOnlyLocalWhenNoPeersKnown() throws Exception {
        Instance nodeA = startInstance("A");

        nodeA.index.upsert(torrent("ubuntu server", 1_000L, 1));

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer performer = new DistributedSearchPerformer(
                2L, "ubuntu", nodeA.index, nodeA.directory,
                nodeA.identity, nodeA.transport);
        performer.setListener(listener);
        performer.perform();

        assertEquals(1, listener.results.size());
        assertEquals(1, listener.results.get(0).size(), "only local results");
        assertEquals("ubuntu server", listener.results.get(0).get(0).getDisplayName());
    }

    /**
     * Two-peer integration test that verifies file-level matching across
     * the distributed search path.
     *
     * <p>Node A has a torrent named "Project Atlas" whose files include
     * "docs/architecture.md". Node B has a torrent named "Cookbook
     * Recipes" whose files include "docs/architecture-notes.txt".
     *
     * <p>Searching for "architecture" from Node A should return:
     * <ul>
     *   <li>Local result from A — matchedFile populated (file-path match,
     *       torrent name doesn't contain "architecture")</li>
     *   <li>Remote result from B — matchedFile populated (file-path match
     *       via rUDP round-trip)</li>
     * </ul>
     *
     * <p>This validates the full matchedFile pipeline:
     * LocalIndex → RelaySearchService → RemoteSearchResponse (JSON "mf"
     * field) → SearchPayloadCodec → IceBridge rUDP →
     * DistributedSearchPerformer.toResult → CompositeFileSearchResult.filename
     */
    @Test
    void twoInstancesFileLevelMatchAcrossRudp() throws Exception {
        Instance nodeA = startInstance("A");
        Instance nodeB = startInstance("B");

        // Cross-register identities (simulate DHT discovery).
        nodeA.directory.upsertVerified(
                nodeB.identity.ed25519PubRaw(), "127.0.0.1", nodeB.rudpPort);
        nodeB.directory.upsertVerified(
                nodeA.identity.ed25519PubRaw(), "127.0.0.1", nodeA.rudpPort);

        assertTrue(nodeA.client.route(
                nodeB.identity.ed25519PubRaw(), "127.0.0.1", nodeB.rudpPort,
                IceBridgeConfig.Role.BOTH));
        assertTrue(nodeB.client.route(
                nodeA.identity.ed25519PubRaw(), "127.0.0.1", nodeA.rudpPort,
                IceBridgeConfig.Role.BOTH));

        // Node A: torrent name has nothing to do with "architecture";
        // only a file inside it does.
        nodeA.index.upsert(torrent("Project Atlas", 5_000L, 3,
                "[{\"path\":\"docs/architecture.md\",\"size\":2048}," +
                "{\"path\":\"src/main.java\",\"size\":1024}]"));

        // Node B: also only matches "architecture" in a file path.
        nodeB.index.upsert(torrent("Cookbook Recipes", 8_000L, 2,
                "[{\"path\":\"docs/architecture-notes.txt\",\"size\":4096}," +
                "{\"path\":\"recipes/soup.txt\",\"size\":512}]"));

        // Perform distributed search from Node A.
        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer performer = new DistributedSearchPerformer(
                10L, "architecture", nodeA.index, nodeA.directory,
                nodeA.identity, nodeA.transport,
                5, 50, 25, 15);
        performer.setListener(listener);
        performer.perform();

        // Verify we got results.
        assertEquals(1, listener.results.size(), "performer should deliver one result batch");
        List<SearchResult> results = listener.results.get(0);
        assertEquals(2, results.size(),
                "should have local (A) + remote (B) file-level matches");

        // Both results should have the matched file as the filename
        // (not "<name>.torrent") because the match was on a file path.
        for (SearchResult sr : results) {
            CompositeFileSearchResult cfsr = (CompositeFileSearchResult) sr;
            String filename = cfsr.getFilename();
            assertNotNull(filename);
            assertTrue(filename.contains("architecture"),
                    "filename should contain the matched file path, got: " + filename);
            assertNotEquals(cfsr.getDisplayName() + ".torrent", filename,
                    "filename should be the matched file, not the default torrent name");
        }

        // Verify both torrent names are present (neither contains "architecture").
        List<String> names = results.stream()
                .map(SearchResult::getDisplayName)
                .sorted()
                .toList();
        assertEquals("Cookbook Recipes", names.get(0), "remote result from Node B");
        assertEquals("Project Atlas", names.get(1), "local result from Node A");

        // Verify the matched files are distinct and correct.
        List<String> matchedFiles = results.stream()
                .map(sr -> ((CompositeFileSearchResult) sr).getFilename())
                .sorted()
                .toList();
        assertTrue(matchedFiles.get(0).contains("architecture-notes.txt"),
                "Node B's matched file should be architecture-notes.txt, got: " + matchedFiles.get(0));
        assertTrue(matchedFiles.get(1).contains("architecture.md"),
                "Node A's matched file should be architecture.md, got: " + matchedFiles.get(1));
    }

    /**
     * Mixed-match scenario: one torrent matches on its name (local),
     * another matches only on a file path (remote). Verifies that
     * matchedFile is null for name matches and populated for file
     * matches across the rUDP path.
     */
    @Test
    void mixedNameAndFileMatchAcrossPeers() throws Exception {
        Instance nodeA = startInstance("A");
        Instance nodeB = startInstance("B");

        nodeA.directory.upsertVerified(
                nodeB.identity.ed25519PubRaw(), "127.0.0.1", nodeB.rudpPort);
        nodeB.directory.upsertVerified(
                nodeA.identity.ed25519PubRaw(), "127.0.0.1", nodeA.rudpPort);

        nodeA.client.route(nodeB.identity.ed25519PubRaw(), "127.0.0.1", nodeB.rudpPort,
                IceBridgeConfig.Role.BOTH);
        nodeB.client.route(nodeA.identity.ed25519PubRaw(), "127.0.0.1", nodeA.rudpPort,
                IceBridgeConfig.Role.BOTH);

        // Node A: torrent NAME contains "freeware" → name match (matchedFile null).
        nodeA.index.upsert(torrent("Freeware Collection", 10_000L, 2,
                "[{\"path\":\"setup.exe\",\"size\":5000}]"));

        // Node B: only a FILE contains "freeware" → file match (matchedFile set).
        nodeB.index.upsert(torrent("Random Stuff", 3_000L, 1,
                "[{\"path\":\"freeware-tool.zip\",\"size\":3000}]"));

        RecordingListener listener = new RecordingListener();
        DistributedSearchPerformer performer = new DistributedSearchPerformer(
                20L, "freeware", nodeA.index, nodeA.directory,
                nodeA.identity, nodeA.transport,
                5, 50, 25, 15);
        performer.setListener(listener);
        performer.perform();

        assertEquals(1, listener.results.size());
        List<SearchResult> results = listener.results.get(0);
        assertEquals(2, results.size());

        CompositeFileSearchResult localResult = results.stream()
                .filter(r -> r.getDisplayName().equals("Freeware Collection"))
                .map(r -> (CompositeFileSearchResult) r)
                .findFirst().orElseThrow();
        CompositeFileSearchResult remoteResult = results.stream()
                .filter(r -> r.getDisplayName().equals("Random Stuff"))
                .map(r -> (CompositeFileSearchResult) r)
                .findFirst().orElseThrow();

        // Local name match → filename is the default (name + .torrent)
        assertEquals("Freeware Collection.torrent", localResult.getFilename(),
                "name match should use default filename, not matched file");
        assertNotEquals("Freeware Collection.torrent", remoteResult.getFilename(),
                "file match should use matched file path, not default filename");
        assertTrue(remoteResult.getFilename().contains("freeware-tool.zip"),
                "remote file match should surface the matched file path");
    }

    // --- helpers ---

    /**
     * Start a full headless instance: IceBridge server, transport, incoming
     * handler, and empty PeerDirectory. The caller is responsible for
     * populating the directory and index.
     */
    private Instance startInstance(String label) throws Exception {
        Path tmpDir = Files.createTempDirectory("frostwire-test-" + label + "-");
        Path identityFile = tmpDir.resolve("identity.dat");

        // Generate with difficulty 0 (no PoW) for fast tests.
        IdentityKeys preGenerated = IdentityKeys.generate(0);
        IdentityKeys.save(preGenerated, identityFile.toFile());

        IceBridgeConfig config = IceBridgeConfig.newBuilder()
                .rudpPort(freePort())
                .controlHttpPort(freePort())
                .role(IceBridgeConfig.Role.BOTH)
                .maxPeers(100)
                .peerTtlSec(120)
                .maxQpsPerKey(100.0)
                .identityFile(identityFile.toFile())
                .build();

        IceBridgeServer server = new IceBridgeServer(config);
        server.start();
        resources.add(server);

        // Wait for the control server to be reachable.
        IceBridgeClient client = new IceBridgeClient(server.controlPort());
        client.setAuthToken(server.authToken());
        boolean healthy = false;
        for (int i = 0; i < 100; i++) {
            if (client.health()) {
                healthy = true;
                break;
            }
            Thread.sleep(50);
        }
        assertTrue(healthy, "Instance " + label + " did not become healthy");

        IdentityKeys identity = server.identity();
        InMemoryLocalIndex index = new InMemoryLocalIndex();
        PeerDirectory directory = new PeerDirectory(new NoOpKarmaCache());

        IceBridgeSearchTransport transport = new IceBridgeSearchTransport(client);
        transport.start();
        resources.add(transport);

        RelaySearchService searchService = new RelaySearchService(index, identity);
        IncomingSearchRequestHandler incomingHandler =
                new IncomingSearchRequestHandler(transport, searchService);
        incomingHandler.start();
        // IncomingSearchRequestHandler doesn't implement AutoCloseable;
        // stop it via the transport's removeListener in tearDown by
        // wrapping.
        resources.add(() -> incomingHandler.stop());

        return new Instance(identity, index, directory, client, transport,
                server.controlPort(), server.rudpPort());
    }

    private static final java.util.concurrent.atomic.AtomicInteger HASH_COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();

    private static int freePort() throws java.io.IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static LocalSharedTorrent torrent(String name, long size, int fileCount) {
        return torrent(name, size, fileCount, "[]");
    }

    private static LocalSharedTorrent torrent(String name, long size, int fileCount, String filesJson) {
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
                .filesJson(filesJson)
                .publisherNodeId(nodeId)
                .publisherEd25519Pub(pub)
                .publisherUtpPort(0)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    /** Bundles all the per-instance state. */
    private static final class Instance {
        final IdentityKeys identity;
        final InMemoryLocalIndex index;
        final PeerDirectory directory;
        final IceBridgeClient client;
        final IceBridgeSearchTransport transport;
        final int controlPort;
        final int rudpPort;

        Instance(IdentityKeys identity, InMemoryLocalIndex index,
                 PeerDirectory directory, IceBridgeClient client,
                 IceBridgeSearchTransport transport,
                 int controlPort, int rudpPort) {
            this.identity = identity;
            this.index = index;
            this.directory = directory;
            this.client = client;
            this.transport = transport;
            this.controlPort = controlPort;
            this.rudpPort = rudpPort;
        }
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
            if (query == null || query.isEmpty()) {
                return Collections.emptyList();
            }
            String q = query.toLowerCase();
            List<LocalSharedTorrent> out = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            // Phase 1: torrent-name match (matchedFile = null)
            for (LocalSharedTorrent r : rows) {
                if (r.name().toLowerCase().contains(q)) {
                    if (seen.add(r.infoHashHex())) {
                        out.add(r);
                        if (out.size() >= limit) break;
                    }
                }
            }
            if (out.size() >= limit) return out;
            // Phase 2: file-path match (matchedFile = matched path)
            for (LocalSharedTorrent r : rows) {
                if (out.size() >= limit) break;
                if (seen.contains(r.infoHashHex())) continue;
                String matched = matchFilePath(r, q);
                if (matched != null) {
                    out.add(r.toBuilder().matchedFile(matched).build());
                    seen.add(r.infoHashHex());
                }
            }
            return out;
        }

        private static String matchFilePath(LocalSharedTorrent t, String query) {
            String json = t.filesJson();
            if (json == null || json.isEmpty() || "[]".equals(json)) {
                return null;
            }
            try {
                var arr = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
                for (var el : arr) {
                    var obj = el.getAsJsonObject();
                    var pathEl = obj.get("path");
                    if (pathEl == null || pathEl.isJsonNull()) continue;
                    String path = pathEl.getAsString();
                    if (path != null && path.toLowerCase().contains(query)) {
                        return path;
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
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
}
