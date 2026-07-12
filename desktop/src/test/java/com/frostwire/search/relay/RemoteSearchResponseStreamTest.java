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
import java.util.Map;
import org.junit.jupiter.api.Test;

class RemoteSearchResponseStreamTest {

    @Test
    void singleFrameFinalDefaultsPreservedInMapRoundTrip() throws Exception {
        byte[] nonce = new byte[32];
        nonce[0] = 7;
        byte[] ih = new byte[20];
        ih[0] = 1;
        byte[] pub = new byte[32];
        pub[0] = 2;

        RemoteSearchResponse.Builder b = RemoteSearchResponse.builder()
                .nonce(nonce)
                .timestamp(1_700_000_000L)
                .addRow(ih, "name", 100L, 1, pub)
                .signature(new byte[64]);
        RemoteSearchResponse unsigned = b.build();
        assertTrue(unsigned.isFinalChunk());
        assertEquals(0, unsigned.chunkIndex());

        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(unsigned.canonicalBytes());
        RemoteSearchResponse signed = RemoteSearchResponse.builder()
                .nonce(nonce)
                .timestamp(1_700_000_000L)
                .addRow(ih, "name", 100L, 1, pub)
                .signature(signer.sign())
                .build();

        Map<String, Object> map = signed.toBencodeableMap();
        RemoteSearchResponse parsed = RemoteSearchResponse.fromBencodeableMap(map);
        assertTrue(parsed.isFinalChunk());
        assertEquals(0, parsed.chunkIndex());
        assertEquals(1, parsed.rows().size());
        assertEquals(RemoteSearchResponse.VERSION, parsed.version());
        assertEquals(2, RemoteSearchResponse.VERSION,
                "wire v2 always covers chunk+final in the signature domain");
    }

    @Test
    void streamChunkChangesCanonicalDomain() throws Exception {
        byte[] nonce = new byte[32];
        byte[] ih = new byte[20];
        byte[] pub = new byte[32];

        RemoteSearchResponse partial = RemoteSearchResponse.builder()
                .nonce(nonce)
                .timestamp(100L)
                .chunkIndex(0)
                .finalChunk(false)
                .addRow(ih, "a", 1L, 1, pub)
                .signature(new byte[64])
                .build();
        RemoteSearchResponse bulk = RemoteSearchResponse.builder()
                .nonce(nonce)
                .timestamp(100L)
                .chunkIndex(0)
                .finalChunk(true)
                .addRow(ih, "a", 1L, 1, pub)
                .signature(new byte[64])
                .build();
        assertFalse(java.util.Arrays.equals(partial.canonicalBytes(), bulk.canonicalBytes()));
        assertFalse(partial.isFinalChunk());
    }
}
