/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class IdentityRecordTest {

    @Test
    void computeDhtKeyProducesCorrectLength() {
        byte[] nodeId = new byte[20];
        new SecureRandom().nextBytes(nodeId);
        byte[] key = IdentityRecord.computeDhtKey(nodeId);
        assertEquals(32, key.length, "DHT key should be 32 bytes (truncated SHA-256)");
    }

    @Test
    void computeDhtKeyIsDeterministic() {
        byte[] nodeId = new byte[20];
        for (int i = 0; i < 20; i++) nodeId[i] = (byte) i;
        byte[] key1 = IdentityRecord.computeDhtKey(nodeId);
        byte[] key2 = IdentityRecord.computeDhtKey(nodeId);
        assertArrayEquals(key1, key2, "DHT key should be deterministic");
    }

    @Test
    void computeDhtKeyRejectsInvalidNodeIdLength() {
        assertThrows(IllegalArgumentException.class,
                () -> IdentityRecord.computeDhtKey(new byte[19]));
        assertThrows(IllegalArgumentException.class,
                () -> IdentityRecord.computeDhtKey(new byte[21]));
        assertThrows(IllegalArgumentException.class,
                () -> IdentityRecord.computeDhtKey(null));
    }

    @Test
    void extractRawEd25519HandlesX509Encoding() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        byte[] raw = IdentityRecord.extractRawEd25519(kp.getPublic());
        assertEquals(32, raw.length, "Raw Ed25519 key should be 32 bytes");
    }

    @Test
    void createProducesValidRecord() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        byte[] nodeId = new byte[20];
        new SecureRandom().nextBytes(nodeId);
        byte[] x25519 = new byte[32];
        new SecureRandom().nextBytes(x25519);

        IdentityRecord record = IdentityRecord.create(nodeId, kp.getPublic(), x25519, 49152);
        assertArrayEquals(nodeId, record.nodeId());
        assertEquals(32, record.ed25519Pub().length);
        assertArrayEquals(x25519, record.x25519Pub());
        assertEquals(49152, record.utpPort());
        assertTrue(record.firstSeen() > 0);
        assertTrue(record.lastSeen() > 0);
    }

    @Test
    void canonicalJsonIsStable() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        byte[] nodeId = new byte[20];
        for (int i = 0; i < 20; i++) nodeId[i] = (byte) i;
        byte[] x25519 = new byte[32];
        for (int i = 0; i < 32; i++) x25519[i] = (byte) (i + 100);

        IdentityRecord r1 = IdentityRecord.create(nodeId, kp.getPublic(), x25519, 49152);
        IdentityRecord r2 = r1.withUpdatedLastSeen(r1.firstSeen());
        assertEquals(r1.toCanonicalJson(), r2.toCanonicalJson(),
                "Canonical JSON should be stable for same fields");
    }

    @Test
    void withUpdatedLastSeenChangesTimestamp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        byte[] nodeId = new byte[20];
        new SecureRandom().nextBytes(nodeId);
        byte[] x25519 = new byte[32];
        new SecureRandom().nextBytes(x25519);

        IdentityRecord r1 = IdentityRecord.create(nodeId, kp.getPublic(), x25519, 49152);
        IdentityRecord r2 = r1.withUpdatedLastSeen(r1.lastSeen() + 100);
        assertEquals(r1.firstSeen(), r2.firstSeen(), "first_seen should not change");
        assertEquals(r1.lastSeen() + 100, r2.lastSeen(), "last_seen should be updated");
    }

    @Test
    void withSignatureReplacesSignature() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        byte[] nodeId = new byte[20];
        new SecureRandom().nextBytes(nodeId);
        byte[] x25519 = new byte[32];
        new SecureRandom().nextBytes(x25519);

        IdentityRecord record = IdentityRecord.create(nodeId, kp.getPublic(), x25519, 49152);
        byte[] sig = new byte[64];
        new SecureRandom().nextBytes(sig);
        IdentityRecord signed = record.withSignature(sig);
        assertArrayEquals(sig, signed.signature());
        assertArrayEquals(new byte[64], record.signature(),
                "Original record should have zero signature");
    }

    @Test
    void canonicalBytesAreAscii() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        byte[] nodeId = new byte[20];
        new SecureRandom().nextBytes(nodeId);
        byte[] x25519 = new byte[32];
        new SecureRandom().nextBytes(x25519);

        IdentityRecord record = IdentityRecord.create(nodeId, kp.getPublic(), x25519, 49152);
        byte[] canonical = record.canonicalBytes();
        String s = new String(canonical, StandardCharsets.US_ASCII);
        assertEquals(s.getBytes(StandardCharsets.US_ASCII).length, canonical.length,
                "Canonical bytes should be valid ASCII");
    }

    @Test
    void equalsAndHashCodeWork() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        byte[] nodeId = new byte[20];
        new SecureRandom().nextBytes(nodeId);
        byte[] x25519 = new byte[32];
        new SecureRandom().nextBytes(x25519);

        IdentityRecord r1 = IdentityRecord.create(nodeId, kp.getPublic(), x25519, 49152);
        IdentityRecord r2 = IdentityRecord.create(nodeId, kp.getPublic(), x25519, 49152);
        byte[] sig = new byte[64];
        sig[0] = 1;
        r1 = r1.withSignature(sig);
        r2 = r2.withSignature(sig);
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }
}
