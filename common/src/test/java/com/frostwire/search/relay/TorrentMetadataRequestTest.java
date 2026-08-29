/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class TorrentMetadataRequestTest {

    private static KeyPair ed25519() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static TorrentMetadataRequest signedRequest(KeyPair kp, byte[] infoHash, long ts) throws Exception {
        byte[] nonce = new byte[32];
        new java.security.SecureRandom().nextBytes(nonce);
        byte[] pubRaw = kp.getPublic().getEncoded();
        // strip the 12-byte X509 prefix
        byte[] raw = new byte[32];
        System.arraycopy(pubRaw, pubRaw.length - 32, raw, 0, 32);
        TorrentMetadataRequest.Builder b = TorrentMetadataRequest.builder()
                .infoHash(infoHash)
                .nonce(nonce)
                .requesterPub(raw)
                .timestamp(ts);
        TorrentMetadataRequest unsigned = b.signature(new byte[64]).build();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(unsigned.canonicalBytes());
        return b.signature(signer.sign()).build();
    }

    @Test
    void roundTripThroughCodecPreservesFields() throws Exception {
        KeyPair kp = ed25519();
        byte[] infoHash = new byte[20];
        new java.security.SecureRandom().nextBytes(infoHash);
        TorrentMetadataRequest request = signedRequest(kp, infoHash, 1000L);

        byte[] wire = SearchPayloadCodec.encodeTorrentMetadataRequest(request);
        TorrentMetadataRequest decoded = SearchPayloadCodec.decodeTorrentMetadataRequest(wire);

        assertNotNull(decoded);
        assertArrayEquals(request.infoHash(), decoded.infoHash());
        assertArrayEquals(request.nonce(), decoded.nonce());
        assertArrayEquals(request.requesterPub(), decoded.requesterPub());
        assertArrayEquals(request.signature(), decoded.signature());
        assertTrue(decoded.verifySignature());
    }

    @Test
    void tamperedRequestFailsVerification() throws Exception {
        KeyPair kp = ed25519();
        byte[] infoHash = new byte[20];
        infoHash[0] = 42;
        TorrentMetadataRequest request = signedRequest(kp, infoHash, 1000L);

        byte[] wire = SearchPayloadCodec.encodeTorrentMetadataRequest(request);
        String json = new String(wire, java.nio.charset.StandardCharsets.UTF_8);
        String tampered = json.replace("\"ts\":1000", "\"ts\":2000");
        TorrentMetadataRequest decoded =
                SearchPayloadCodec.decodeTorrentMetadataRequest(tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertNotNull(decoded);
        assertFalse(decoded.verifySignature());
    }

    @Test
    void malformedWireReturnsNull() {
        assertNull(SearchPayloadCodec.decodeTorrentMetadataRequest(new byte[0]));
        assertNull(SearchPayloadCodec.decodeTorrentMetadataRequest("{not json".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertNull(TorrentMetadataRequest.fromBencodeableMap(null));
    }

    @Test
    void builderRejectsInvalidFields() {
        assertThrows(IllegalStateException.class, () ->
                TorrentMetadataRequest.builder()
                        .infoHash(new byte[19])
                        .nonce(new byte[32])
                        .requesterPub(new byte[32])
                        .signature(new byte[64])
                        .build());
        assertThrows(IllegalStateException.class, () ->
                TorrentMetadataRequest.builder()
                        .infoHash(new byte[20])
                        .nonce(new byte[32])
                        .requesterPub(new byte[32])
                        .signature(new byte[63])
                        .build());
    }
}