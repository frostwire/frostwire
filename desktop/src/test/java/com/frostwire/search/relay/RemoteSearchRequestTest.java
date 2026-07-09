/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.util.Hex;
import java.security.KeyPair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RemoteSearchRequestTest {

  private static KeyPair keyPair;
  private static byte[] pubRaw;

  @BeforeAll
  static void setUpClass() throws Exception {
    keyPair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    pubRaw = IdentityRecord.extractRawEd25519(keyPair.getPublic());
  }

  @Test
  void builderRejectsMissingNonce() {
    assertThrows(
        IllegalStateException.class,
        () -> RemoteSearchRequest.builder().requesterPub(pubRaw).signature(new byte[64]).build());
  }

  @Test
  void builderRejectsMissingPub() {
    byte[] nonce = new byte[32];
    assertThrows(
        IllegalStateException.class,
        () -> RemoteSearchRequest.builder().nonce(nonce).signature(new byte[64]).build());
  }

  @Test
  void builderRejectsBadLimit() {
    byte[] nonce = new byte[32];
    assertThrows(
        IllegalStateException.class,
        () ->
            RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(pubRaw)
                .limit(0)
                .signature(new byte[64])
                .build());
    assertThrows(
        IllegalStateException.class,
        () ->
            RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(pubRaw)
                .limit(99999)
                .signature(new byte[64])
                .build());
  }

  @Test
  void builderRejectsMissingSignature() {
    byte[] nonce = new byte[32];
    assertThrows(
        IllegalStateException.class,
        () -> RemoteSearchRequest.builder().nonce(nonce).requesterPub(pubRaw).build());
  }

  @Test
  void builderRejectsLongKeywords() {
    byte[] nonce = new byte[32];
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 2000; i++) sb.append('a');
    assertThrows(
        IllegalStateException.class,
        () ->
            RemoteSearchRequest.builder()
                .nonce(nonce)
                .requesterPub(pubRaw)
                .keywords(sb.toString())
                .signature(new byte[64])
                .build());
  }

  @Test
  void builderAcceptsMinimalValid() {
    byte[] nonce = new byte[32];
    RemoteSearchRequest r =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .signature(new byte[64])
            .build();
    assertNotNull(r);
    assertEquals(RemoteSearchRequest.VERSION, r.version());
    assertEquals(RemoteSearchRequest.DEFAULT_LIMIT, r.limit());
    assertEquals(0, r.pathLength());
    assertEquals("", r.keywords());
  }

  @Test
  void canonicalBytesAreDeterministic() {
    byte[] nonce = new byte[32];
    for (int i = 0; i < 32; i++) nonce[i] = (byte) i;
    long ts = 1700000000L;
    RemoteSearchRequest a =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .keywords("ubuntu")
            .limit(10)
            .timestamp(ts)
            .signature(new byte[64])
            .build();
    RemoteSearchRequest b =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .keywords("ubuntu")
            .limit(10)
            .timestamp(ts)
            .signature(new byte[64])
            .build();
    assertArrayEquals(
        a.canonicalBytes(), b.canonicalBytes(), "canonical bytes are stable for identical input");
  }

  @Test
  void canonicalBytesWithNonEmptyPathAreStableAndVerifiable() throws Exception {
    byte[] nonce = new byte[32];
    for (int i = 0; i < 32; i++) nonce[i] = (byte) i;
    byte[] hop1 = new byte[32];
    hop1[31] = 0x01;
    byte[] hop2 = new byte[32];
    hop2[31] = 0x02;
    long ts = 1700000000L;
    RemoteSearchRequest unsigned =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .keywords("ubuntu")
            .limit(10)
            .ttl(2)
            .timestamp(ts)
            .path(new byte[][] {hop1, hop2})
            .signature(new byte[64])
            .build();
    java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
    signer.initSign(keyPair.getPrivate());
    signer.update(unsigned.canonicalBytes());
    byte[] sig = signer.sign();

    RemoteSearchRequest signed =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .keywords("ubuntu")
            .limit(10)
            .ttl(2)
            .timestamp(ts)
            .path(new byte[][] {hop1, hop2})
            .signature(sig)
            .build();

    java.security.Signature verifier = java.security.Signature.getInstance("Ed25519");
    verifier.initVerify(keyPair.getPublic());
    verifier.update(signed.canonicalBytes());
    assertTrue(
        verifier.verify(signed.signature()),
        "signature over query envelope must verify even with non-empty path");

    // Dual-envelope: hop must not invalidate requester signature.
    byte[] hop3 = new byte[32];
    hop3[31] = 0x03;
    RemoteSearchRequest hopped = signed.withNextHop(hop3, 1);
    assertArrayEquals(signed.signature(), hopped.signature());
    java.security.Signature v2 = java.security.Signature.getInstance("Ed25519");
    v2.initVerify(keyPair.getPublic());
    v2.update(hopped.queryCanonicalBytes());
    assertTrue(
        v2.verify(hopped.signature()), "after withNextHop, requester signature still verifies");
  }

  @Test
  void canonicalBytesDifferOnKeywordsChange() {
    byte[] nonce = new byte[32];
    long ts = 1700000000L;
    RemoteSearchRequest a =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .keywords("ubuntu")
            .timestamp(ts)
            .signature(new byte[64])
            .build();
    RemoteSearchRequest b =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .keywords("debian")
            .timestamp(ts)
            .signature(new byte[64])
            .build();
    assertFalse(java.util.Arrays.equals(a.canonicalBytes(), b.canonicalBytes()));
  }

  @Test
  void defensiveCopiesOnAllByteArrays() {
    byte[] nonce = new byte[32];
    byte[] pub = pubRaw.clone();
    byte[] sig = new byte[64];

    RemoteSearchRequest r =
        RemoteSearchRequest.builder().nonce(nonce).requesterPub(pub).signature(sig).build();

    // Mutate the inputs
    java.util.Arrays.fill(nonce, (byte) 0xff);
    java.util.Arrays.fill(pub, (byte) 0xff);
    java.util.Arrays.fill(sig, (byte) 0xff);

    // Internal state is unaffected
    assertFalse(java.util.Arrays.equals(r.nonce(), nonce));
    assertFalse(java.util.Arrays.equals(r.requesterPub(), pub));
    assertFalse(java.util.Arrays.equals(r.signature(), sig));
  }

  @Test
  void withNextHopAppendsToPathAndDecrementsTtl() {
    byte[] nonce = new byte[32];
    byte[] nextHop = new byte[32];
    nextHop[31] = 0x01;
    RemoteSearchRequest r =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .ttl(3)
            .signature(new byte[64])
            .build();
    RemoteSearchRequest forwarded = r.withNextHop(nextHop, 2);
    assertEquals(1, forwarded.pathLength());
    assertEquals(2, forwarded.ttl());
    assertEquals(0, r.pathLength(), "original request is immutable");
    assertEquals(3, r.ttl());
    assertArrayEquals(nextHop, forwarded.path()[0]);
  }

  @Test
  void withNextHopInvalidatesSignature() {
    byte[] nonce = new byte[32];
    byte[] nextHop = new byte[32];
    nextHop[31] = 0x01;
    RemoteSearchRequest r =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .signature(new byte[64])
            .build();
    RemoteSearchRequest forwarded = r.withNextHop(nextHop, 2);
    // Signature bytes should be all zero (invalidated) — caller must re-sign
    byte[] fwdSig = forwarded.signature();
    for (byte b : fwdSig) {
      assertEquals(0, b, "Forwarded request's signature must be zeroed");
    }
  }

  @Test
  void withNextHopRejectsBadInputs() {
    byte[] nonce = new byte[32];
    RemoteSearchRequest r =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .signature(new byte[64])
            .build();
    assertThrows(IllegalArgumentException.class, () -> r.withNextHop(null, 2));
    assertThrows(IllegalArgumentException.class, () -> r.withNextHop(new byte[31], 2));
    assertThrows(IllegalArgumentException.class, () -> r.withNextHop(new byte[32], -1));
  }

  @Test
  void isLoopDetectsPathMembership() {
    byte[] nonce = new byte[32];
    byte[] hop1 = new byte[32];
    hop1[31] = 0x01;
    byte[] hop2 = new byte[32];
    hop2[31] = 0x02;
    byte[] hop3 = new byte[32];
    hop3[31] = 0x03;
    RemoteSearchRequest r =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .path(new byte[][] {hop1, hop2})
            .signature(new byte[64])
            .build();
    assertTrue(r.isLoop(hop1));
    assertTrue(r.isLoop(hop2));
    assertFalse(r.isLoop(hop3));
  }

  @Test
  void withNextHopEnforcesPathLengthLimit() {
    byte[] nonce = new byte[32];
    byte[][] bigPath = new byte[RemoteSearchRequest.MAX_PATH_LENGTH][];
    for (int i = 0; i < bigPath.length; i++) {
      bigPath[i] = new byte[32];
      bigPath[i][31] = (byte) i;
    }
    RemoteSearchRequest r =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .path(bigPath)
            .signature(new byte[64])
            .build();
    assertEquals(RemoteSearchRequest.MAX_PATH_LENGTH, r.pathLength());
    byte[] nextHop = new byte[32];
    nextHop[31] = 0x7f;
    assertThrows(IllegalStateException.class, () -> r.withNextHop(nextHop, 0));
  }

  @Test
  void toBencodeableMapIncludesAllFields() {
    byte[] nonce = new byte[32];
    nonce[31] = 0x42;
    RemoteSearchRequest r =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .keywords("test")
            .limit(7)
            .ttl(2)
            .timestamp(1700000000L)
            .signature(new byte[64])
            .build();
    java.util.Map<String, Object> m = r.toBencodeableMap();
    assertEquals(RemoteSearchRequest.VERSION, m.get("v"));
    assertEquals("test", m.get("k"));
    assertEquals(7, m.get("lim"));
    assertEquals(2, m.get("ttl"));
    assertEquals(1700000000L, m.get("ts"));
    assertNotNull(m.get("pub"));
    assertNotNull(m.get("sig"));
    assertNotNull(m.get("path"));
  }

  @Test
  void roundTripThroughBencodeableMap() {
    byte[] nonce = new byte[32];
    for (int i = 0; i < 32; i++) nonce[i] = (byte) (i * 7);
    RemoteSearchRequest r =
        RemoteSearchRequest.builder()
            .nonce(nonce)
            .requesterPub(pubRaw)
            .keywords("round-trip")
            .limit(15)
            .ttl(2)
            .timestamp(1700000123L)
            .signature(new byte[64])
            .build();
    java.util.Map<String, Object> m = r.toBencodeableMap();
    // The nonce in the map should be base64(nonce) which decodes back to the same bytes
    String nonceB64 = (String) m.get("nonce");
    byte[] decodedNonce = java.util.Base64.getDecoder().decode(nonceB64);
    assertArrayEquals(nonce, decodedNonce);
    assertEquals(
        Hex.encode(pubRaw),
        Hex.encode(java.util.Base64.getDecoder().decode((String) m.get("pub"))));
  }
}
