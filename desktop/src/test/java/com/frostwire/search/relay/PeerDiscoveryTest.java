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

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PeerDiscoveryTest {

    private FakeSource source;
    private PeerDirectory directory;
    private PeerDiscovery discovery;

    @BeforeEach
    void setUp() {
        source = new FakeSource();
        directory = new PeerDirectory(new NoopKarmaCache());
        discovery = new PeerDiscovery(source, directory);
    }

    @Test
    void constructorRejectsNullArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> new PeerDiscovery(null, directory));
        assertThrows(IllegalArgumentException.class,
                () -> new PeerDiscovery(source, null));
    }

    @Test
    void discoverReturnsEmptyWhenSourceReturnsEmpty() {
        assertTrue(discovery.discoverAndRegister().isEmpty());
        assertEquals(0, directory.size());
    }

    @Test
    void discoverRegistersDiscoveredEndpoints() {
        source.endpoints.add(new DiscoveredEndpoint("10.0.0.1", 6888));
        source.endpoints.add(new DiscoveredEndpoint("10.0.0.2", 6888));

        List<DiscoveredEndpoint> result = discovery.discoverAndRegister();

        assertEquals(2, result.size());
        assertEquals(2, directory.size());
        byte[] pk1 = PeerDiscovery.placeholderPubkey("10.0.0.1", 6888);
        byte[] pk2 = PeerDiscovery.placeholderPubkey("10.0.0.2", 6888);
        assertNotNull(directory.get(pk1).orElse(null));
        assertNotNull(directory.get(pk2).orElse(null));
        assertEquals("10.0.0.1", directory.get(pk1).get().hostname());
        assertEquals(6888, directory.get(pk1).get().utpPort());
    }

    @Test
    void discoverIsIdempotent() {
        source.endpoints.add(new DiscoveredEndpoint("10.0.0.1", 6888));
        discovery.discoverAndRegister();
        // Second pass: same endpoint, should be a no-op
        List<DiscoveredEndpoint> second = discovery.discoverAndRegister();
        assertTrue(second.isEmpty());
        assertEquals(1, directory.size());
    }

    @Test
    void discoverSkipsInvalidEndpoints() {
        source.endpoints.add(new DiscoveredEndpoint("", 0));
        source.endpoints.add(new DiscoveredEndpoint("valid", 6888));
        List<DiscoveredEndpoint> result = discovery.discoverAndRegister();
        assertEquals(1, result.size());
        assertEquals("valid", result.get(0).host);
    }

    @Test
    void discoverFailsClosedOnSourceException() {
        source.throwOnFetch = true;
        assertTrue(discovery.discoverAndRegister().isEmpty());
        assertEquals(0, directory.size());
    }

    @Test
    void placeholderPubkeyIsStableForSameHostPort() {
        byte[] a = PeerDiscovery.placeholderPubkey("10.0.0.1", 6888);
        byte[] b = PeerDiscovery.placeholderPubkey("10.0.0.1", 6888);
        assertArrayEquals(a, b);
    }

    @Test
    void placeholderPubkeyDiffersForDifferentEndpoints() {
        byte[] a = PeerDiscovery.placeholderPubkey("10.0.0.1", 6888);
        byte[] b = PeerDiscovery.placeholderPubkey("10.0.0.2", 6888);
        byte[] c = PeerDiscovery.placeholderPubkey("10.0.0.1", 6889);
        assertFalse(java.util.Arrays.equals(a, b));
        assertFalse(java.util.Arrays.equals(a, c));
    }

    @Test
    void placeholderPubkeyIs32Bytes() {
        byte[] a = PeerDiscovery.placeholderPubkey("any", 0);
        assertEquals(32, a.length);
    }

    @Test
    void fetchIdentityRecordRejectsBadInputs() {
        assertNull(discovery.fetchIdentityRecord(null));
        assertNull(discovery.fetchIdentityRecord(new byte[31]));
    }

    @Test
    void fetchIdentityRecordReturnsParsedRecord() throws Exception {
        KeyPair kp = java.security.KeyPairGenerator.getInstance("Ed25519")
                .generateKeyPair();
        byte[] rawPub = IdentityRecord.extractRawEd25519(kp.getPublic());
        byte[] x25519 = new byte[32];
        byte[] nodeId = new byte[20];
        IdentityRecord record = IdentityRecord.createSigned(nodeId, kp, x25519, 6888);
        source.identityEntries.put(rawPub, record.toEntry());

        IdentityRecord fetched = discovery.fetchIdentityRecord(rawPub);
        assertNotNull(fetched);
        assertEquals(6888, fetched.utpPort());
        assertArrayEquals(rawPub, fetched.ed25519Pub());
        assertTrue(fetched.verifySignature());
    }

    @Test
    void fetchIdentityRecordReturnsNullWhenItemMissing() {
        assertNull(discovery.fetchIdentityRecord(new byte[32]));
    }

    @Test
    void fetchIdentityRecordReturnsNullOnException() {
        source.throwOnIdentityFetch = true;
        assertNull(discovery.fetchIdentityRecord(new byte[32]));
    }

    @Test
    void discoverWithAuthenticatorRegistersVerifiedPeers() {
        FakeAuthenticator auth = new FakeAuthenticator();
        discovery = new PeerDiscovery(source, directory, auth);

        source.endpoints.add(new DiscoveredEndpoint("10.0.0.1", 6888));
        IdentityRecord record = auth.add("10.0.0.1", 6888);

        List<DiscoveredEndpoint> result = discovery.discoverAndRegister();

        assertEquals(1, result.size());
        assertEquals(1, directory.size());
        byte[] realPub = record.ed25519Pub();
        PeerDirectory.PeerInfo info = directory.get(realPub).orElse(null);
        assertNotNull(info);
        assertEquals("10.0.0.1", info.hostname());
        assertEquals(6888, info.utpPort());
        assertTrue(info.isVerified());
    }

    @Test
    void discoverWithAuthenticatorDropsUnauthenticatedPeers() {
        FakeAuthenticator auth = new FakeAuthenticator();
        discovery = new PeerDiscovery(source, directory, auth);

        source.endpoints.add(new DiscoveredEndpoint("10.0.0.1", 6888));
        // No identity registered for this endpoint

        List<DiscoveredEndpoint> result = discovery.discoverAndRegister();

        assertTrue(result.isEmpty());
        assertEquals(0, directory.size());
    }

    @Test
    void discoverWithAuthenticatorIsIdempotentForVerifiedPeers() {
        FakeAuthenticator auth = new FakeAuthenticator();
        discovery = new PeerDiscovery(source, directory, auth);

        source.endpoints.add(new DiscoveredEndpoint("10.0.0.1", 6888));
        auth.add("10.0.0.1", 6888);

        assertEquals(1, discovery.discoverAndRegister().size());
        assertTrue(discovery.discoverAndRegister().isEmpty());
        assertEquals(1, directory.size());
    }

    @Test
    void discoverWithAuthenticatorRejectsNullAuthenticator() {
        assertDoesNotThrow(() -> new PeerDiscovery(source, directory, null));
    }

    @Test
    void placeholderDiscoveryStillWorksWithoutAuthenticator() {
        source.endpoints.add(new DiscoveredEndpoint("10.0.0.1", 6888));
        List<DiscoveredEndpoint> result = discovery.discoverAndRegister();
        assertEquals(1, result.size());
        assertEquals(1, directory.size());
        PeerDirectory.PeerInfo info = directory.get(
                PeerDiscovery.placeholderPubkey("10.0.0.1", 6888)).orElse(null);
        assertNotNull(info);
        assertFalse(info.isVerified());
    }

    // --- helpers ---

    private static final class FakeAuthenticator implements PeerAuthenticator {
        final Map<String, IdentityRecord> records = new HashMap<>();

        IdentityRecord add(String host, int port) {
            try {
                java.security.KeyPair kp = java.security.KeyPairGenerator
                        .getInstance("Ed25519").generateKeyPair();
                byte[] nodeId = new byte[20];
                byte[] x25519 = new byte[32];
                IdentityRecord record = IdentityRecord.createSigned(
                        nodeId, kp, x25519, port);
                records.put(host + ":" + port, record);
                return record;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public java.util.Optional<IdentityRecord> authenticate(String host, int port) {
            return java.util.Optional.ofNullable(records.get(host + ":" + port));
        }
    }

    private static final class FakeSource implements PeerDiscoverySource {
        final List<DiscoveredEndpoint> endpoints = new ArrayList<>();
        final Map<byte[], Entry> identityEntries = new HashMap<>();
        boolean throwOnFetch;
        boolean throwOnIdentityFetch;

        @Override
        public List<DiscoveredEndpoint> fetchEndpoints() {
            if (throwOnFetch) {
                throw new RuntimeException("simulated BEP 5 failure");
            }
            return new ArrayList<>(endpoints);
        }

        @Override
        public Entry fetchIdentityEntry(byte[] peerPub) {
            if (throwOnIdentityFetch) {
                throw new RuntimeException("simulated BEP 46 failure");
            }
            return identityEntries.get(peerPub);
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
