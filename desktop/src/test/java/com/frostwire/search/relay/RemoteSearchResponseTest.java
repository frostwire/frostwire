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

    // ===== matchedFile tests =====

    @Test
    void canonicalBytesDifferWhenMatchedFileChanges() {
        byte[] nonce = new byte[32];
        long ts = 1700000000L;
        byte[] infoHash = new byte[20];
        byte[] pub = new byte[32];
        RemoteSearchResponse withoutMf = RemoteSearchResponse.builder()
                .nonce(nonce).timestamp(ts)
                .addRow(infoHash, "ubuntu", 1000, 1, pub, null, null)
                .signature(new byte[64])
                .build();
        RemoteSearchResponse withMf = RemoteSearchResponse.builder()
                .nonce(nonce).timestamp(ts)
                .addRow(infoHash, "ubuntu", 1000, 1, pub, null, "docs/readme.txt")
                .signature(new byte[64])
                .build();
        assertFalse(java.util.Arrays.equals(withoutMf.canonicalBytes(), withMf.canonicalBytes()),
                "canonical bytes must differ when matchedFile is added — signature covers it");
    }

    @Test
    void canonicalBytesDifferOnMatchedFileContentChange() {
        byte[] nonce = new byte[32];
        long ts = 1700000000L;
        byte[] infoHash = new byte[20];
        byte[] pub = new byte[32];
        RemoteSearchResponse a = RemoteSearchResponse.builder()
                .nonce(nonce).timestamp(ts)
                .addRow(infoHash, "ubuntu", 1000, 1, pub, null, "docs/readme.txt")
                .signature(new byte[64])
                .build();
        RemoteSearchResponse b = RemoteSearchResponse.builder()
                .nonce(nonce).timestamp(ts)
                .addRow(infoHash, "ubuntu", 1000, 1, pub, null, "docs/changelog.txt")
                .signature(new byte[64])
                .build();
        assertFalse(java.util.Arrays.equals(a.canonicalBytes(), b.canonicalBytes()),
                "canonical bytes must differ when matchedFile content changes");
    }

    @Test
    void matchedFileSurvivesBuildAndRowsAccessor() {
        RemoteSearchResponse r = RemoteSearchResponse.builder()
                .nonce(new byte[32])
                .addRow(new byte[20], "ubuntu", 1000, 1, new byte[32], null, "docs/readme.txt")
                .signature(new byte[64])
                .build();
        assertEquals("docs/readme.txt", r.rows().get(0).matchedFile,
                "matchedFile must survive build() and rows() copy");
    }

    @Test
    void matchedFileSurvivesBencodeableMapRoundTrip() {
        RemoteSearchResponse original = RemoteSearchResponse.builder()
                .nonce(new byte[32])
                .timestamp(1700000000L)
                .addRow(new byte[20], "ubuntu", 1000, 1, new byte[32], null, "docs/readme.txt")
                .signature(new byte[64])
                .build();
        java.util.Map<String, Object> map = original.toBencodeableMap();
        // Verify "mf" is in the serialized map.
        java.util.List<?> rows = (java.util.List<?>) map.get("rows");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> firstRow = (java.util.Map<String, Object>) rows.get(0);
        assertEquals("docs/readme.txt", firstRow.get("mf"),
                "toBencodeableMap must include 'mf' field");

        RemoteSearchResponse decoded = RemoteSearchResponse.fromBencodeableMap(map);
        assertNotNull(decoded);
        assertEquals("docs/readme.txt", decoded.rows().get(0).matchedFile,
                "fromBencodeableMap must restore matchedFile");
    }

    @Test
    void matchedFileNullIsOmittedInBencodeableMap() {
        RemoteSearchResponse r = RemoteSearchResponse.builder()
                .nonce(new byte[32])
                .addRow(new byte[20], "ubuntu", 1000, 1, new byte[32], null, null)
                .signature(new byte[64])
                .build();
        java.util.Map<String, Object> map = r.toBencodeableMap();
        java.util.List<?> rows = (java.util.List<?>) map.get("rows");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> firstRow = (java.util.Map<String, Object>) rows.get(0);
        assertFalse(firstRow.containsKey("mf"),
                "null matchedFile should be omitted from the map, not serialized as null");
    }

    @Test
    void matchedFileSurvivesCodecRoundTrip() {
        RemoteSearchResponse original = RemoteSearchResponse.builder()
                .nonce(new byte[32])
                .timestamp(1700000000L)
                .addRow(new byte[20], "ubuntu", 1000, 1, new byte[32], null, "docs/readme.txt")
                .signature(new byte[64])
                .build();
        byte[] encoded = SearchPayloadCodec.encodeResponse(original);
        RemoteSearchResponse decoded = SearchPayloadCodec.decodeResponse(encoded);
        assertNotNull(decoded);
        assertEquals("docs/readme.txt", decoded.rows().get(0).matchedFile,
                "matchedFile must survive SearchPayloadCodec encode/decode round-trip");
    }

    @Test
    void signatureWithMatchedFileVerifiesCorrectly() throws Exception {
        java.security.KeyPair kp = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] rawPub = IdentityRecord.extractRawEd25519(kp.getPublic());

        RemoteSearchResponse unsigned = RemoteSearchResponse.builder()
                .nonce(new byte[32])
                .timestamp(1700000000L)
                .addRow(new byte[20], "ubuntu", 1000, 1, rawPub, null, "docs/readme.txt")
                .signature(new byte[64])
                .build();
        java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(unsigned.canonicalBytes());
        byte[] signature = sig.sign();

        RemoteSearchResponse signed = RemoteSearchResponse.builder()
                .nonce(unsigned.nonce())
                .timestamp(unsigned.timestamp())
                .addRow(new byte[20], "ubuntu", 1000, 1, rawPub, null, "docs/readme.txt")
                .signature(signature)
                .build();

        // Verify the signature
        java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(signed.canonicalBytes());
        assertTrue(verifier.verify(signature),
                "Signature over response with matchedFile must verify");

        // Tamper with matchedFile — signature must fail
        RemoteSearchResponse tampered = RemoteSearchResponse.builder()
                .nonce(unsigned.nonce())
                .timestamp(unsigned.timestamp())
                .addRow(new byte[20], "ubuntu", 1000, 1, rawPub, null, "TAMPERED.txt")
                .signature(signature)
                .build();
        java.security.Signature verifier2 = java.security.Signature.getInstance("Ed25519");
        verifier2.initVerify(kp.getPublic());
        verifier2.update(tampered.canonicalBytes());
        assertFalse(verifier2.verify(signature),
                "Signature must NOT verify after matchedFile tampering — regression test for signature bypass");
    }
}
