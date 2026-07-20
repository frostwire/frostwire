/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SeederEndpointProviderTest {

    @Test
    void parseXpeSplitsEntriesAndSkipsBlanks() {
        List<String> entries = LibtorrentSeederEndpointProvider.parseXpe(
                "&x.pe=76.130.145.63:45321&x.pe=0.0.0.0:45321&x.pe=[::]:45321");
        assertEquals(3, entries.size());
        assertEquals("76.130.145.63:45321", entries.get(0));
        assertTrue(LibtorrentSeederEndpointProvider.parseXpe("").isEmpty());
        assertTrue(LibtorrentSeederEndpointProvider.parseXpe(null).isEmpty());
    }

    @Test
    void buildEndpointsKeepsExternalThenLanWithWildcardPort() {
        List<String> out = LibtorrentSeederEndpointProvider.buildEndpoints(
                Arrays.asList("76.130.145.63:45321", "0.0.0.0:45321", "[::]:45321"),
                Arrays.asList("192.168.1.10", "10.0.0.2"));
        assertEquals(
                Arrays.asList("76.130.145.63:45321", "192.168.1.10:45321", "10.0.0.2:45321"),
                out);
    }

    @Test
    void buildEndpointsWithoutWildcardKeepsOnlyUsableEntries() {
        List<String> out = LibtorrentSeederEndpointProvider.buildEndpoints(
                Arrays.asList("76.130.145.63:45321", "192.168.1.10:45321"),
                Collections.singletonList("10.0.0.2"));
        assertEquals(Arrays.asList("76.130.145.63:45321", "192.168.1.10:45321"), out);
    }

    @Test
    void buildEndpointsCapsAtMaxAndDedupes() {
        List<String> entries = Arrays.asList(
                "1.1.1.1:1", "2.2.2.2:2", "1.1.1.1:1", "0.0.0.0:9999");
        List<String> out = LibtorrentSeederEndpointProvider.buildEndpoints(
                entries, Arrays.asList("10.0.0.1", "10.0.0.2", "10.0.0.3"));
        assertTrue(out.size() <= LibtorrentSeederEndpointProvider.MAX_ENDPOINTS);
        assertEquals(1, Collections.frequency(out, "1.1.1.1:1"));
        assertTrue(out.contains("10.0.0.1:9999"));
    }

    @Test
    void serviceAdvertisesProviderEndpointsInSignedRows() throws Exception {
        NoopIndex index = new NoopIndex();
        index.torrents.add(torrent("ubuntu"));
        IdentityKeys identity = IdentityKeys.generate(0);
        RelaySearchService service = new RelaySearchService(index, identity);
        service.setSeederEndpointProvider(
                () -> Collections.singletonList("192.168.1.10:45321"));

        Optional<RemoteSearchResponse> resp = service.handle(signedRequest("ubuntu"));
        assertTrue(resp.isPresent());
        assertEquals(Collections.singletonList("192.168.1.10:45321"),
                resp.get().rows().get(0).seederEndpoints);
    }

    @Test
    void serviceWithoutProviderAdvertisesNothing() throws Exception {
        NoopIndex index = new NoopIndex();
        index.torrents.add(torrent("ubuntu"));
        IdentityKeys identity = IdentityKeys.generate(0);
        RelaySearchService service = new RelaySearchService(index, identity);

        Optional<RemoteSearchResponse> resp = service.handle(signedRequest("ubuntu"));
        assertTrue(resp.isPresent());
        assertTrue(resp.get().rows().get(0).seederEndpoints.isEmpty());
    }

    private static RemoteSearchRequest signedRequest(String keywords) throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] pub = IdentityRecord.extractRawEd25519(kp.getPublic());
        byte[] nonce = new byte[32];
        long ts = System.currentTimeMillis() / 1000L;
        RemoteSearchRequest unsigned = RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(pub)
                .keywords(keywords)
                .limit(5)
                .timestamp(ts)
                .path(new byte[0][])
                .signature(new byte[64])
                .build();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(unsigned.canonicalBytes());
        return RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(pub)
                .keywords(keywords)
                .limit(5)
                .timestamp(ts)
                .path(new byte[0][])
                .signature(signer.sign())
                .build();
    }

    private static LocalSharedTorrent torrent(String name) {
        long now = System.currentTimeMillis() / 1000L;
        return new LocalSharedTorrent.Builder()
                .infoHash(new byte[20])
                .name(name)
                .sizeBytes(1000)
                .fileCount(1)
                .filesJson("[]")
                .publisherNodeId(new byte[20])
                .publisherEd25519Pub(new byte[32])
                .publisherUtpPort(0)
                .addedAt(now)
                .lastSeenAt(now)
                .build();
    }

    private static final class NoopIndex implements LocalIndex {
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
            List<LocalSharedTorrent> out = new ArrayList<>();
            for (LocalSharedTorrent t : torrents) {
                if (t.name().toLowerCase().contains(query.toLowerCase())) {
                    out.add(t);
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
