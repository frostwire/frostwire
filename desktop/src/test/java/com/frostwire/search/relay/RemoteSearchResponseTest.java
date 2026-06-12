/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RemoteSearchResponseTest {

    @Test
    void builderRejectsMissingNonce() {
        assertThrows(IllegalStateException.class, () ->
                RemoteSearchResponse.builder()
                        .addRow(new byte[20], "ubuntu", 1000, 1, new byte[32])
                        .signature(new byte[64])
                        .build());
    }

    @Test
    void builderRejectsMissingSignature() {
        assertThrows(IllegalStateException.class, () ->
                RemoteSearchResponse.builder()
                        .nonce(new byte[32])
                        .addRow(new byte[20], "ubuntu", 1000, 1, new byte[32])
                        .build());
    }

    @Test
    void builderAcceptsMinimalValid() {
        RemoteSearchResponse r = RemoteSearchResponse.builder()
                .nonce(new byte[32])
                .signature(new byte[64])
                .build();
        assertNotNull(r);
        assertEquals(0, r.rows().size());
    }

    @Test
    void rowRejectsBadInfoHash() {
        assertThrows(IllegalArgumentException.class, () ->
                new RemoteSearchResponse.Row(new byte[19], "ubuntu", 1000, 1, new byte[32], null));
        assertThrows(IllegalArgumentException.class, () ->
                new RemoteSearchResponse.Row(null, "ubuntu", 1000, 1, new byte[32], null));
    }

    @Test
    void rowRejectsBadPublisherPub() {
        assertThrows(IllegalArgumentException.class, () ->
                new RemoteSearchResponse.Row(new byte[20], "ubuntu", 1000, 1, new byte[31], null));
        assertThrows(IllegalArgumentException.class, () ->
                new RemoteSearchResponse.Row(new byte[20], "ubuntu", 1000, 1, null, null));
    }

    @Test
    void rowsDefensivelyCopied() {
        byte[] nonce = new byte[32];
        byte[] infoHash = new byte[20];
        byte[] pub = new byte[32];
        RemoteSearchResponse r = RemoteSearchResponse.builder()
                .nonce(nonce)
                .addRow(infoHash, "ubuntu", 1000, 1, pub)
                .signature(new byte[64])
                .build();
        // Mutate the inputs
        java.util.Arrays.fill(nonce, (byte) 0xff);
        java.util.Arrays.fill(infoHash, (byte) 0xff);
        java.util.Arrays.fill(pub, (byte) 0xff);
        // Internal state unaffected
        RemoteSearchResponse.Row row = r.rows().get(0);
        assertFalse(java.util.Arrays.equals(row.infoHash, infoHash));
        assertFalse(java.util.Arrays.equals(row.publisherEd25519Pub, pub));
    }

    @Test
    void canonicalBytesAreDeterministic() {
        byte[] nonce = new byte[32];
        for (int i = 0; i < 32; i++) nonce[i] = (byte) i;
        long ts = 1700000000L;
        byte[] infoHash = new byte[20];
        infoHash[0] = 1;
        byte[] pub = new byte[32];
        pub[31] = 1;
        RemoteSearchResponse a = RemoteSearchResponse.builder()
                .nonce(nonce).timestamp(ts)
                .addRow(infoHash, "ubuntu", 1000, 1, pub)
                .signature(new byte[64])
                .build();
        RemoteSearchResponse b = RemoteSearchResponse.builder()
                .nonce(nonce).timestamp(ts)
                .addRow(infoHash, "ubuntu", 1000, 1, pub)
                .signature(new byte[64])
                .build();
        assertArrayEquals(a.canonicalBytes(), b.canonicalBytes());
    }

    @Test
    void canonicalBytesDifferOnNonceChange() {
        byte[] nonce1 = new byte[32];
        byte[] nonce2 = new byte[32];
        nonce2[0] = 1;
        long ts = 1700000000L;
        byte[] infoHash = new byte[20];
        byte[] pub = new byte[32];
        RemoteSearchResponse a = RemoteSearchResponse.builder()
                .nonce(nonce1).timestamp(ts)
                .addRow(infoHash, "ubuntu", 1000, 1, pub)
                .signature(new byte[64])
                .build();
        RemoteSearchResponse b = RemoteSearchResponse.builder()
                .nonce(nonce2).timestamp(ts)
                .addRow(infoHash, "ubuntu", 1000, 1, pub)
                .signature(new byte[64])
                .build();
        assertFalse(java.util.Arrays.equals(a.canonicalBytes(), b.canonicalBytes()));
    }

    @Test
    void canonicalBytesDifferOnRowChange() {
        byte[] nonce = new byte[32];
        long ts = 1700000000L;
        byte[] infoHash1 = new byte[20];
        infoHash1[0] = 1;
        byte[] infoHash2 = new byte[20];
        infoHash2[0] = 2;
        byte[] pub = new byte[32];
        RemoteSearchResponse a = RemoteSearchResponse.builder()
                .nonce(nonce).timestamp(ts)
                .addRow(infoHash1, "ubuntu", 1000, 1, pub)
                .signature(new byte[64])
                .build();
        RemoteSearchResponse b = RemoteSearchResponse.builder()
                .nonce(nonce).timestamp(ts)
                .addRow(infoHash2, "ubuntu", 1000, 1, pub)
                .signature(new byte[64])
                .build();
        assertFalse(java.util.Arrays.equals(a.canonicalBytes(), b.canonicalBytes()));
    }

    @Test
    void publisherNodeIdIsOptional() {
        RemoteSearchResponse r = RemoteSearchResponse.builder()
                .nonce(new byte[32])
                .addRow(new byte[20], "ubuntu", 1000, 1, new byte[32], null)
                .signature(new byte[64])
                .build();
        assertNull(r.rows().get(0).publisherNodeId);
    }

    @Test
    void publisherNodeIdIsDefensivelyCopied() {
        byte[] nodeId = new byte[20];
        nodeId[0] = 7;
        RemoteSearchResponse r = RemoteSearchResponse.builder()
                .nonce(new byte[32])
                .addRow(new byte[20], "ubuntu", 1000, 1, new byte[32], nodeId)
                .signature(new byte[64])
                .build();
        java.util.Arrays.fill(nodeId, (byte) 0xff);
        assertEquals(7, r.rows().get(0).publisherNodeId[0]);
    }

    @Test
    void toStringIsSafe() {
        RemoteSearchResponse r = RemoteSearchResponse.builder()
                .nonce(new byte[32])
                .timestamp(1700000000L)
                .addRow(new byte[20], "ubuntu", 1000, 1, new byte[32])
                .signature(new byte[64])
                .build();
        assertNotNull(r.toString());
        assertTrue(r.toString().contains("rows=1"));
    }
}
