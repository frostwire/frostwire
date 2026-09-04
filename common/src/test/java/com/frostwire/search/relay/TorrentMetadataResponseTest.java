/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TorrentMetadataResponseTest {

    private static KeyPair ed25519() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static byte[] raw32(KeyPair kp) {
        byte[] encoded = kp.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        return raw;
    }

    private static byte[] holderPub(KeyPair kp) {
        return raw32(kp);
    }

    private static TorrentMetadataResponse sign(TorrentMetadataResponse unsigned, KeyPair kp) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(unsigned.canonicalBytes());
        return TorrentMetadataResponse.builder()
                .version(unsigned.version())
                .nonce(unsigned.nonce())
                .infoHash(unsigned.infoHash())
                .payloadDigest(unsigned.payloadDigest())
                .chunkIndex(unsigned.chunkIndex())
                .finalChunk(unsigned.isFinalChunk())
                .timestamp(unsigned.timestamp())
                .data(unsigned.data())
                .error(unsigned.error())
                .signature(signer.sign())
                .build();
    }

    private static List<TorrentMetadataResponse> signAll(List<TorrentMetadataResponse> unsigned, KeyPair kp) throws Exception {
        List<TorrentMetadataResponse> signed = new ArrayList<>();
        for (TorrentMetadataResponse c : unsigned) {
            signed.add(sign(c, kp));
        }
        return signed;
    }

    @Test
    void chunkSplitAssembleAndVerifyRoundTrip() throws Exception {
        KeyPair holder = ed25519();
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        byte[] infoHash = new byte[20];
        new SecureRandom().nextBytes(infoHash);
        byte[] torrent = new byte[TorrentMetadataResponse.CHUNK_DATA_BYTES * 3 + 137];
        new SecureRandom().nextBytes(torrent);

        List<TorrentMetadataResponse> chunks =
                signAll(TorrentMetadataResponse.buildChunks(nonce, infoHash, 1000L, torrent), holder);

        assertEquals(4, chunks.size());
        assertTrue(chunks.get(3).isFinalChunk());
        for (TorrentMetadataResponse chunk : chunks) {
            assertTrue(chunk.verifySignature(holderPub(holder)));
            assertNotNull(SearchPayloadCodec.decodeTorrentMetadataResponse(
                    SearchPayloadCodec.encodeTorrentMetadataResponse(chunk)));
        }

        byte[] reassembled = TorrentMetadataResponse.assemble(chunks);
        assertNotNull(reassembled);
        assertArrayEquals(torrent, reassembled);
    }

    @Test
    void tamperedChunkDataFailsAssembleDigestCheck() throws Exception {
        KeyPair holder = ed25519();
        byte[] nonce = new byte[32];
        byte[] infoHash = new byte[20];
        byte[] torrent = new byte[TorrentMetadataResponse.CHUNK_DATA_BYTES + 5];
        new SecureRandom().nextBytes(torrent);

        List<TorrentMetadataResponse> chunks =
                signAll(TorrentMetadataResponse.buildChunks(nonce, infoHash, 1000L, torrent), holder);

        // Swap chunk 0's data for attacker bytes, keep the holder signature:
        // per-chunk verification still passes (canonical bytes exclude the
        // data slice), but the whole-payload digest in the signature domain
        // no longer matches the reassembled bytes — assemble must reject.
        TorrentMetadataResponse original = chunks.get(0);
        byte[] evil = new byte[original.data().length];
        new SecureRandom().nextBytes(evil);
        TorrentMetadataResponse tampered = TorrentMetadataResponse.builder()
                .version(original.version())
                .nonce(original.nonce())
                .infoHash(original.infoHash())
                .payloadDigest(original.payloadDigest())
                .chunkIndex(original.chunkIndex())
                .finalChunk(original.isFinalChunk())
                .timestamp(original.timestamp())
                .data(evil)
                .signature(original.signature())
                .build();
        assertTrue(tampered.verifySignature(holderPub(holder)));

        List<TorrentMetadataResponse> tamperedSet = new ArrayList<>(chunks);
        tamperedSet.set(0, tampered);
        assertNull(TorrentMetadataResponse.assemble(tamperedSet));
    }

    @Test
    void assembleRejectsMissingAndReorderedChunks() throws Exception {
        KeyPair holder = ed25519();
        byte[] nonce = new byte[32];
        byte[] infoHash = new byte[20];
        byte[] torrent = new byte[TorrentMetadataResponse.CHUNK_DATA_BYTES * 2 + 10];
        new SecureRandom().nextBytes(torrent);

        List<TorrentMetadataResponse> chunks =
                signAll(TorrentMetadataResponse.buildChunks(nonce, infoHash, 1000L, torrent), holder);

        List<TorrentMetadataResponse> missing = new ArrayList<>(chunks);
        missing.remove(1);
        assertNull(TorrentMetadataResponse.assemble(missing));

        List<TorrentMetadataResponse> reordered = new ArrayList<>(chunks);
        java.util.Collections.swap(reordered, 0, 1);
        assertNull(TorrentMetadataResponse.assemble(reordered));
    }

    @Test
    void errorResponseRoundTripAndVerification() throws Exception {
        KeyPair holder = ed25519();
        byte[] nonce = new byte[32];
        byte[] infoHash = new byte[20];
        TorrentMetadataResponse err = sign(
                TorrentMetadataResponse.buildError(nonce, infoHash, 1000L, TorrentMetadataResponse.ERR_NOT_FOUND),
                holder);

        assertTrue(err.isError());
        assertEquals(TorrentMetadataResponse.ERR_NOT_FOUND, err.error());
        assertTrue(err.verifySignature(holderPub(holder)));

        TorrentMetadataResponse decoded = SearchPayloadCodec.decodeTorrentMetadataResponse(
                SearchPayloadCodec.encodeTorrentMetadataResponse(err));
        assertNotNull(decoded);
        assertTrue(decoded.isError());
        assertTrue(decoded.verifySignature(holderPub(holder)));
        assertNull(TorrentMetadataResponse.assemble(java.util.Collections.singletonList(decoded)));
    }

    @Test
    void restampedCopyVerifiesForNewNonce() throws Exception {
        KeyPair holder = ed25519();
        byte[] nonceA = new byte[32];
        byte[] nonceB = new byte[32];
        new SecureRandom().nextBytes(nonceA);
        new SecureRandom().nextBytes(nonceB);
        byte[] infoHash = new byte[20];
        new SecureRandom().nextBytes(infoHash);
        byte[] torrent = new byte[TorrentMetadataResponse.CHUNK_DATA_BYTES + 7];
        new SecureRandom().nextBytes(torrent);

        List<TorrentMetadataResponse> chunks =
                signAll(TorrentMetadataResponse.buildChunks(nonceA, infoHash, 1000L, torrent), holder);
        TorrentMetadataResponse restamped = chunks.get(0).withNonceTimestamp(nonceB, 2000L);

        // Same holder signature, new correlation fields — must verify for request B.
        assertArrayEquals(nonceB, restamped.nonce());
        assertEquals(2000L, restamped.timestamp());
        assertTrue(restamped.verifySignature(holderPub(holder)));
        // And the restamped frame still round-trips through the wire codec.
        TorrentMetadataResponse decoded = SearchPayloadCodec.decodeTorrentMetadataResponse(
                SearchPayloadCodec.encodeTorrentMetadataResponse(restamped));
        assertNotNull(decoded);
        assertTrue(decoded.verifySignature(holderPub(holder)));

        // Same-domain tampering (chunk index) must still fail verification.
        TorrentMetadataResponse reindexed = TorrentMetadataResponse.builder()
                .version(restamped.version())
                .nonce(restamped.nonce())
                .infoHash(restamped.infoHash())
                .payloadDigest(restamped.payloadDigest())
                .chunkIndex(restamped.chunkIndex() + 1)
                .finalChunk(restamped.isFinalChunk())
                .timestamp(restamped.timestamp())
                .data(restamped.data())
                .signature(restamped.signature())
                .build();
        assertFalse(reindexed.verifySignature(holderPub(holder)));
    }

    @Test
    void fromMapRejectsStaleV1Frames() throws Exception {
        KeyPair holder = ed25519();
        byte[] nonce = new byte[32];
        byte[] infoHash = new byte[20];
        TorrentMetadataResponse err = sign(
                TorrentMetadataResponse.buildError(nonce, infoHash, 1000L, TorrentMetadataResponse.ERR_NOT_FOUND),
                holder);
        Map<String, Object> stale = err.toBencodeableMap();
        stale.put("v", 1);
        assertNull(TorrentMetadataResponse.fromBencodeableMap(stale),
                "v1 frames (nonce-bound domain) must fail fast on v2 peers");
    }

    @Test
    void builderEnforcesExactlyOneOfDataOrError() {
        byte[] nonce = new byte[32];
        byte[] infoHash = new byte[20];
        byte[] digest = new byte[32];
        assertThrows(IllegalStateException.class, () ->
                TorrentMetadataResponse.builder()
                        .nonce(nonce).infoHash(infoHash).payloadDigest(digest)
                        .signature(new byte[64]).build());
        assertThrows(IllegalStateException.class, () ->
                TorrentMetadataResponse.builder()
                        .nonce(nonce).infoHash(infoHash).payloadDigest(digest)
                        .data(new byte[10]).error("X")
                        .signature(new byte[64]).build());
        assertThrows(IllegalStateException.class, () ->
                TorrentMetadataResponse.builder()
                        .nonce(nonce).infoHash(infoHash).payloadDigest(digest)
                        .data(new byte[TorrentMetadataResponse.CHUNK_DATA_BYTES + 1])
                        .signature(new byte[64]).build());
    }

    @Test
    void fromMapRejectsBadDigestAndBothFields() {
        byte[] nonceB64 = java.util.Base64.getEncoder().withoutPadding().encode(new byte[32]);
        byte[] ih = new byte[20];
        new SecureRandom().nextBytes(ih);
        String ihHex = com.frostwire.util.Hex.encode(ih);
        String sig = java.util.Base64.getEncoder().withoutPadding().encodeToString(new byte[64]);
        Map<String, Object> badDigest = new java.util.HashMap<>();
        badDigest.put("v", TorrentMetadataResponse.VERSION);
        badDigest.put("nonce", nonceB64);
        badDigest.put("ih", ihHex);
        badDigest.put("pd", "short");
        badDigest.put("ci", 0);
        badDigest.put("fin", true);
        badDigest.put("ts", 1L);
        badDigest.put("data", "abcd");
        badDigest.put("sig", sig);
        assertNull(TorrentMetadataResponse.fromBencodeableMap(badDigest));

        Map<String, Object> both = new java.util.HashMap<>(badDigest);
        both.put("pd", java.util.Base64.getEncoder().withoutPadding().encodeToString(new byte[32]));
        both.put("err", "NOT_FOUND");
        assertNull(TorrentMetadataResponse.fromBencodeableMap(both));
    }
}