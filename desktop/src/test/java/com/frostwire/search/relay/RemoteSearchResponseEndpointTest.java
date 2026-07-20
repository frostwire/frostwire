/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RemoteSearchResponseEndpointTest {

    @Test
    void v3RoundTripPreservesSeederEndpoints() {
        byte[] nonce = new byte[32];
        byte[] ih = new byte[20];
        ih[0] = 1;
        byte[] pub = new byte[32];
        pub[0] = 2;
        List<String> endpoints = Arrays.asList("192.168.1.10:45321", "76.130.145.63:45321");

        RemoteSearchResponse signed = RemoteSearchResponse.builder()
                .nonce(nonce)
                .timestamp(1_700_000_000L)
                .addRow(ih, "name", 100L, 1, pub, null, null, endpoints)
                .signature(new byte[64])
                .build();
        assertEquals(3, signed.version());

        Map<String, Object> map = signed.toBencodeableMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> rowMap = ((List<Map<String, Object>>) map.get("rows")).get(0);
        assertTrue(rowMap.containsKey("bt"), "v3 rows serialize bt endpoints");

        RemoteSearchResponse parsed = RemoteSearchResponse.fromBencodeableMap(map);
        assertEquals(3, parsed.version());
        assertEquals(endpoints, parsed.rows().get(0).seederEndpoints);
    }

    @Test
    void v2SignedResponseStillVerifiesAfterVersionBump() throws Exception {
        byte[] nonce = new byte[32];
        byte[] ih = new byte[20];
        byte[] pub = new byte[32];

        RemoteSearchResponse unsigned = RemoteSearchResponse.builder()
                .version(RemoteSearchResponse.VERSION_2)
                .nonce(nonce)
                .timestamp(1_700_000_000L)
                .addRow(ih, "name", 100L, 1, pub)
                .signature(new byte[64])
                .build();

        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(unsigned.canonicalBytes());
        byte[] sig = signer.sign();

        RemoteSearchResponse signed = RemoteSearchResponse.builder()
                .version(RemoteSearchResponse.VERSION_2)
                .nonce(nonce)
                .timestamp(1_700_000_000L)
                .addRow(ih, "name", 100L, 1, pub)
                .signature(sig)
                .build();

        RemoteSearchResponse parsed = RemoteSearchResponse.fromBencodeableMap(signed.toBencodeableMap());
        assertEquals(RemoteSearchResponse.VERSION_2, parsed.version(),
                "parsed response must carry the transmitted version or verification breaks");
        assertTrue(parsed.rows().get(0).seederEndpoints.isEmpty());

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(parsed.canonicalBytes());
        assertTrue(verifier.verify(sig),
                "v2 response must still verify when VERSION is 3");
    }

    @Test
    void seederEndpointsChangeCanonicalDomain() {
        byte[] nonce = new byte[32];
        byte[] ih = new byte[20];
        byte[] pub = new byte[32];

        RemoteSearchResponse without = RemoteSearchResponse.builder()
                .nonce(nonce)
                .timestamp(100L)
                .addRow(ih, "a", 1L, 1, pub)
                .signature(new byte[64])
                .build();
        RemoteSearchResponse with = RemoteSearchResponse.builder()
                .nonce(nonce)
                .timestamp(100L)
                .addRow(ih, "a", 1L, 1, pub, null, null,
                        java.util.Collections.singletonList("10.0.0.2:6881"))
                .signature(new byte[64])
                .build();
        assertFalse(Arrays.equals(without.canonicalBytes(), with.canonicalBytes()),
                "bt endpoints are inside the signed row domain");
    }

    @Test
    void malformedSeederEndpointsSkippedAndCapped() {
        byte[] nonce = new byte[32];
        byte[] ih = new byte[20];
        byte[] pub = new byte[32];
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tooMany.add("10.0.0." + i + ":6881");
        }
        tooMany.add("");
        tooMany.add(null);
        StringBuilder longEp = new StringBuilder("10.0.0.1:");
        for (int i = 0; i < 300; i++) {
            longEp.append('9');
        }
        tooMany.add(longEp.toString());

        RemoteSearchResponse r = RemoteSearchResponse.builder()
                .nonce(nonce)
                .timestamp(100L)
                .addRow(ih, "a", 1L, 1, pub, null, null, tooMany)
                .signature(new byte[64])
                .build();
        List<String> kept = r.rows().get(0).seederEndpoints;
        assertEquals(RemoteSearchResponse.Row.MAX_SEEDER_ENDPOINTS, kept.size());
        for (String ep : kept) {
            assertTrue(ep.length() <= 256);
            assertFalse(ep.isEmpty());
        }
    }

    @Test
    void mapWithoutBtDecodesWithEmptyEndpoints() {
        byte[] nonce = new byte[32];
        byte[] ih = new byte[20];
        byte[] pub = new byte[32];
        RemoteSearchResponse legacy = RemoteSearchResponse.builder()
                .nonce(nonce)
                .timestamp(100L)
                .addRow(ih, "a", 1L, 1, pub)
                .signature(new byte[64])
                .build();
        Map<String, Object> map = legacy.toBencodeableMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> rowMap = ((List<Map<String, Object>>) map.get("rows")).get(0);
        assertFalse(rowMap.containsKey("bt"), "rows without endpoints must not serialize bt");

        RemoteSearchResponse parsed = RemoteSearchResponse.fromBencodeableMap(map);
        assertTrue(parsed.rows().get(0).seederEndpoints.isEmpty());
        assertTrue(Arrays.equals(legacy.canonicalBytes(), parsed.canonicalBytes()),
                "legacy round-trip must preserve canonical bytes exactly");
    }
}
