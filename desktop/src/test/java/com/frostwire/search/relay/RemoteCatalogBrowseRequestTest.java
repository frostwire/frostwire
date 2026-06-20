/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RemoteCatalogBrowseRequestTest {

    private static KeyPair keyPair;
    private static byte[] requesterPub;
    private static byte[] targetPub;

    @BeforeAll
    static void setUpClass() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        requesterPub = IdentityRecord.extractRawEd25519(keyPair.getPublic());
        targetPub = new byte[32];
        for (int i = 0; i < 32; i++) {
            targetPub[i] = (byte) (0x80 + i);
        }
    }

    @Test
    void builderRejectsMissingNonce() {
        assertThrows(IllegalStateException.class, () ->
                RemoteCatalogBrowseRequest.builder()
                        .requesterPub(requesterPub)
                        .targetPub(targetPub)
                        .signature(new byte[64])
                        .build());
    }

    @Test
    void builderRejectsMissingRequesterPub() {
        assertThrows(IllegalStateException.class, () ->
                RemoteCatalogBrowseRequest.builder()
                        .targetPub(targetPub)
                        .nonce(new byte[32])
                        .signature(new byte[64])
                        .build());
    }

    @Test
    void builderRejectsMissingTargetPub() {
        assertThrows(IllegalStateException.class, () ->
                RemoteCatalogBrowseRequest.builder()
                        .requesterPub(requesterPub)
                        .nonce(new byte[32])
                        .signature(new byte[64])
                        .build());
    }

    @Test
    void builderRejectsBadRequesterPubLength() {
        assertThrows(IllegalStateException.class, () ->
                RemoteCatalogBrowseRequest.builder()
                        .requesterPub(new byte[31])
                        .targetPub(targetPub)
                        .nonce(new byte[32])
                        .signature(new byte[64])
                        .build());
    }

    @Test
    void builderRejectsBadTargetPubLength() {
        assertThrows(IllegalStateException.class, () ->
                RemoteCatalogBrowseRequest.builder()
                        .requesterPub(requesterPub)
                        .targetPub(new byte[33])
                        .nonce(new byte[32])
                        .signature(new byte[64])
                        .build());
    }

    @Test
    void builderRejectsMissingSignature() {
        assertThrows(IllegalStateException.class, () ->
                RemoteCatalogBrowseRequest.builder()
                        .requesterPub(requesterPub)
                        .targetPub(targetPub)
                        .nonce(new byte[32])
                        .build());
    }

    @Test
    void builderRejectsBadSignatureLength() {
        assertThrows(IllegalStateException.class, () ->
                RemoteCatalogBrowseRequest.builder()
                        .requesterPub(requesterPub)
                        .targetPub(targetPub)
                        .nonce(new byte[32])
                        .signature(new byte[63])
                        .build());
    }

    @Test
    void builderAcceptsMinimalValid() {
        RemoteCatalogBrowseRequest r = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(new byte[32])
                .timestamp(1700000000L)
                .signature(new byte[64])
                .build();
        assertNotNull(r);
        assertEquals(RemoteCatalogBrowseRequest.VERSION, r.version());
        assertArrayEquals(requesterPub, r.requesterPub());
        assertArrayEquals(targetPub, r.targetPub());
        assertEquals(1700000000L, r.timestamp());
    }

    @Test
    void canonicalBytesAreDeterministic() {
        byte[] nonce = new byte[32];
        for (int i = 0; i < 32; i++) nonce[i] = (byte) i;
        long ts = 1700000000L;

        RemoteCatalogBrowseRequest a = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(ts)
                .signature(new byte[64])
                .build();
        RemoteCatalogBrowseRequest b = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(ts)
                .signature(new byte[64])
                .build();
        assertArrayEquals(a.canonicalBytes(), b.canonicalBytes(),
                "canonical bytes are stable for identical input");
    }

    @Test
    void canonicalBytesDifferOnTargetPubChange() {
        byte[] nonce = new byte[32];
        long ts = 1700000000L;
        byte[] otherTarget = new byte[32];
        otherTarget[0] = 0x01;

        RemoteCatalogBrowseRequest a = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(ts)
                .signature(new byte[64])
                .build();
        RemoteCatalogBrowseRequest b = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(otherTarget)
                .nonce(nonce)
                .timestamp(ts)
                .signature(new byte[64])
                .build();
        assertFalse(Arrays.equals(a.canonicalBytes(), b.canonicalBytes()));
    }

    @Test
    void canonicalBytesDifferOnNonceChange() {
        byte[] nonce1 = new byte[32];
        byte[] nonce2 = new byte[32];
        nonce2[31] = 0x01;
        long ts = 1700000000L;

        RemoteCatalogBrowseRequest a = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce1)
                .timestamp(ts)
                .signature(new byte[64])
                .build();
        RemoteCatalogBrowseRequest b = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce2)
                .timestamp(ts)
                .signature(new byte[64])
                .build();
        assertFalse(Arrays.equals(a.canonicalBytes(), b.canonicalBytes()));
    }

    @Test
    void canonicalBytesDifferOnTimestampChange() {
        byte[] nonce = new byte[32];
        RemoteCatalogBrowseRequest a = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(1700000000L)
                .signature(new byte[64])
                .build();
        RemoteCatalogBrowseRequest b = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(1700000001L)
                .signature(new byte[64])
                .build();
        assertFalse(Arrays.equals(a.canonicalBytes(), b.canonicalBytes()));
    }

    @Test
    void canonicalBytesExcludeSignature() {
        byte[] nonce = new byte[32];
        byte[] sig1 = new byte[64];
        byte[] sig2 = new byte[64];
        sig2[0] = 0x42;

        RemoteCatalogBrowseRequest a = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(1700000000L)
                .signature(sig1)
                .build();
        RemoteCatalogBrowseRequest b = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(1700000000L)
                .signature(sig2)
                .build();
        assertArrayEquals(a.canonicalBytes(), b.canonicalBytes(),
                "signature must not affect canonical bytes");
    }

    @Test
    void signingAndVerificationSucceed() throws Exception {
        byte[] nonce = new byte[32];
        for (int i = 0; i < 32; i++) nonce[i] = (byte) (i * 3);
        long ts = System.currentTimeMillis() / 1000L;

        RemoteCatalogBrowseRequest unsigned = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(ts)
                .signature(new byte[64])
                .build();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(unsigned.canonicalBytes());
        byte[] sig = signer.sign();

        RemoteCatalogBrowseRequest signed = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(ts)
                .signature(sig)
                .build();

        PublicKey pub = SearchResponseVerifier.rawEd25519ToPublicKey(requesterPub);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pub);
        verifier.update(signed.canonicalBytes());
        assertTrue(verifier.verify(signed.signature()),
                "signature over canonical bytes must verify");
    }

    @Test
    void tamperDetectionFailsVerification() throws Exception {
        byte[] nonce = new byte[32];
        long ts = System.currentTimeMillis() / 1000L;

        RemoteCatalogBrowseRequest unsigned = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(ts)
                .signature(new byte[64])
                .build();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(unsigned.canonicalBytes());
        byte[] sig = signer.sign();

        byte[] tamperedTarget = targetPub.clone();
        tamperedTarget[31] ^= 0x01;

        RemoteCatalogBrowseRequest tampered = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(tamperedTarget)
                .nonce(nonce)
                .timestamp(ts)
                .signature(sig)
                .build();

        PublicKey pub = SearchResponseVerifier.rawEd25519ToPublicKey(requesterPub);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pub);
        verifier.update(tampered.canonicalBytes());
        assertFalse(verifier.verify(tampered.signature()),
                "signature must not verify after tampering with targetPub");
    }

    @Test
    void tamperDetectionOnTimestampFailsVerification() throws Exception {
        byte[] nonce = new byte[32];
        long ts = System.currentTimeMillis() / 1000L;

        RemoteCatalogBrowseRequest unsigned = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(ts)
                .signature(new byte[64])
                .build();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(unsigned.canonicalBytes());
        byte[] sig = signer.sign();

        RemoteCatalogBrowseRequest tampered = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(ts + 1)
                .signature(sig)
                .build();

        PublicKey pub = SearchResponseVerifier.rawEd25519ToPublicKey(requesterPub);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pub);
        verifier.update(tampered.canonicalBytes());
        assertFalse(verifier.verify(tampered.signature()),
                "signature must not verify after tampering with timestamp");
    }

    @Test
    void defensiveCopiesOnAllByteArrays() {
        byte[] pub = requesterPub.clone();
        byte[] target = targetPub.clone();
        byte[] nonce = new byte[32];
        nonce[0] = 0x11;
        byte[] sig = new byte[64];
        sig[0] = 0x22;

        RemoteCatalogBrowseRequest r = RemoteCatalogBrowseRequest.builder()
                .requesterPub(pub)
                .targetPub(target)
                .nonce(nonce)
                .timestamp(1700000000L)
                .signature(sig)
                .build();

        Arrays.fill(pub, (byte) 0xff);
        Arrays.fill(target, (byte) 0xff);
        Arrays.fill(nonce, (byte) 0xff);
        Arrays.fill(sig, (byte) 0xff);

        assertFalse(Arrays.equals(r.requesterPub(), pub));
        assertFalse(Arrays.equals(r.targetPub(), target));
        assertFalse(Arrays.equals(r.nonce(), nonce));
        assertFalse(Arrays.equals(r.signature(), sig));
    }

    @Test
    void toBencodeableMapIncludesAllFields() {
        byte[] nonce = new byte[32];
        nonce[31] = 0x42;
        RemoteCatalogBrowseRequest r = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(1700000000L)
                .signature(new byte[64])
                .build();
        Map<String, Object> m = r.toBencodeableMap();
        assertEquals(RemoteCatalogBrowseRequest.VERSION, m.get("v"));
        assertEquals(1700000000L, m.get("ts"));
        assertNotNull(m.get("pub"));
        assertNotNull(m.get("target"));
        assertNotNull(m.get("nonce"));
        assertNotNull(m.get("sig"));
    }

    @Test
    void roundTripThroughBencodeableMap() {
        byte[] nonce = new byte[32];
        for (int i = 0; i < 32; i++) nonce[i] = (byte) (i * 5);
        RemoteCatalogBrowseRequest original = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(nonce)
                .timestamp(1700000123L)
                .signature(new byte[64])
                .build();

        byte[] payload = SearchPayloadCodec.encodeCatalogBrowseRequest(original);
        RemoteCatalogBrowseRequest decoded = SearchPayloadCodec.decodeCatalogBrowseRequest(payload);
        assertNotNull(decoded);
        assertEquals(original.version(), decoded.version());
        assertEquals(original.timestamp(), decoded.timestamp());
        assertArrayEquals(original.requesterPub(), decoded.requesterPub());
        assertArrayEquals(original.targetPub(), decoded.targetPub());
        assertArrayEquals(original.nonce(), decoded.nonce());
        assertArrayEquals(original.signature(), decoded.signature());
    }

    @Test
    void fromBencodeableMapReturnsNullForMissingField() {
        java.util.Map<String, Object> incomplete = new java.util.LinkedHashMap<>();
        incomplete.put("v", 1);
        incomplete.put("pub", Base64.getEncoder().withoutPadding().encodeToString(requesterPub));
        incomplete.put("nonce", Base64.getEncoder().withoutPadding().encodeToString(new byte[32]));
        incomplete.put("ts", 1700000000L);
        incomplete.put("sig", Base64.getEncoder().withoutPadding().encodeToString(new byte[64]));
        assertNull(RemoteCatalogBrowseRequest.fromBencodeableMap(incomplete),
                "missing 'target' field must return null");
    }

    @Test
    void fromBencodeableMapReturnsNullForNullMap() {
        assertNull(RemoteCatalogBrowseRequest.fromBencodeableMap(null));
    }

    @Test
    void decodeCatalogBrowseRequestReturnsNullForEmptyBytes() {
        assertNull(SearchPayloadCodec.decodeCatalogBrowseRequest(null));
        assertNull(SearchPayloadCodec.decodeCatalogBrowseRequest(new byte[0]));
    }

    @Test
    void toStringContainsShortPubs() {
        RemoteCatalogBrowseRequest r = RemoteCatalogBrowseRequest.builder()
                .requesterPub(requesterPub)
                .targetPub(targetPub)
                .nonce(new byte[32])
                .timestamp(1700000000L)
                .signature(new byte[64])
                .build();
        String s = r.toString();
        assertTrue(s.contains(Hex.encode(requesterPub).substring(0, 8)));
        assertTrue(s.contains(Hex.encode(targetPub).substring(0, 8)));
    }
}
