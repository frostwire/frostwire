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
import org.junit.jupiter.api.Test;

class TorrentMetadataRequestTest {

  private static KeyPair ed25519() throws Exception {
    return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  private static TorrentMetadataRequest signedRequest(KeyPair kp, byte[] infoHash, long ts)
      throws Exception {
    byte[] nonce = new byte[32];
    new java.security.SecureRandom().nextBytes(nonce);
    byte[] pubRaw = kp.getPublic().getEncoded();
    // strip the 12-byte X509 prefix
    byte[] raw = new byte[32];
    System.arraycopy(pubRaw, pubRaw.length - 32, raw, 0, 32);
    TorrentMetadataRequest.Builder b =
        TorrentMetadataRequest.builder()
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
        SearchPayloadCodec.decodeTorrentMetadataRequest(
            tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertNotNull(decoded);
    assertFalse(decoded.verifySignature());
  }

  @Test
  void malformedWireReturnsNull() {
    assertNull(SearchPayloadCodec.decodeTorrentMetadataRequest(new byte[0]));
    assertNull(
        SearchPayloadCodec.decodeTorrentMetadataRequest(
            "{not json".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    assertNull(TorrentMetadataRequest.fromBencodeableMap(null));
  }

  @Test
  void builderRejectsInvalidFields() {
    assertThrows(
        IllegalStateException.class,
        () ->
            TorrentMetadataRequest.builder()
                .infoHash(new byte[19])
                .nonce(new byte[32])
                .requesterPub(new byte[32])
                .signature(new byte[64])
                .build());
    assertThrows(
        IllegalStateException.class,
        () ->
            TorrentMetadataRequest.builder()
                .infoHash(new byte[20])
                .nonce(new byte[32])
                .requesterPub(new byte[32])
                .signature(new byte[63])
                .build());
  }

  @Test
  void haveBitmapRoundTripsOutsideTheSignature() throws Exception {
    KeyPair kp = ed25519();
    TorrentMetadataRequest signed = signedRequest(kp, new byte[20], 1000L);
    TorrentMetadataRequest withHave =
        TorrentMetadataRequest.builder()
            .infoHash(signed.infoHash())
            .nonce(signed.nonce())
            .requesterPub(signed.requesterPub())
            .timestamp(signed.timestamp())
            .signature(signed.signature())
            .have(new byte[] {0b0000_0101, 0, (byte) 0x80})
            .build();

    TorrentMetadataRequest decoded =
        SearchPayloadCodec.decodeTorrentMetadataRequest(
            SearchPayloadCodec.encodeTorrentMetadataRequest(withHave));

    assertNotNull(decoded);
    assertTrue(decoded.verifySignature(), "have is a hint outside the signed bytes");
    assertTrue(decoded.hasChunk(0));
    assertFalse(decoded.hasChunk(1));
    assertTrue(decoded.hasChunk(2));
    assertTrue(decoded.hasChunk(23));
    assertFalse(decoded.hasChunk(24), "indexes past the bitmap are wanted");
    assertFalse(decoded.hasChunk(-1));
    assertNull(signed.have());
    assertFalse(signed.hasChunk(0), "no bitmap means every chunk is wanted");
  }

  @Test
  void oversizedHaveBitmapIsRejected() {
    assertThrows(
        IllegalStateException.class,
        () ->
            TorrentMetadataRequest.builder()
                .infoHash(new byte[20])
                .nonce(new byte[32])
                .requesterPub(new byte[32])
                .signature(new byte[64])
                .have(new byte[TorrentMetadataRequest.MAX_HAVE_BYTES + 1])
                .build());
  }
}
