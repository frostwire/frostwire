/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class IdentityRecordTest {

    @Test
    void extractRawEd25519HandlesX509Encoding() throws Exception {
        KeyPair kp = ed25519();
        byte[] raw = IdentityRecord.extractRawEd25519(kp.getPublic());
        assertEquals(32, raw.length, "Raw Ed25519 key should be 32 bytes");
    }

    @Test
    void createSignedProducesValidRecord() throws Exception {
        KeyPair kp = ed25519();
        byte[] nodeId = randomBytes(20);
        byte[] x25519 = randomBytes(32);

        IdentityRecord record = IdentityRecord.createSigned(nodeId, kp, x25519, 49152);
        assertArrayEquals(nodeId, record.nodeId());
        assertEquals(32, record.ed25519Pub().length);
        assertArrayEquals(x25519, record.x25519Pub());
        assertEquals(49152, record.utpPort());
        assertTrue(record.firstSeen() > 0);
        assertTrue(record.lastSeen() > 0);
        assertEquals(64, record.signature().length);
        assertTrue(record.verifySignature());
    }

    @Test
    void canonicalBytesAreStableBencodeWithoutSignature() throws Exception {
        KeyPair kp = ed25519();
        byte[] nodeId = fixedBytes(20, 1);
        byte[] x25519 = fixedBytes(32, 100);

        IdentityRecord record = IdentityRecord.createSigned(nodeId, kp, x25519, 49152);
        byte[] canonical1 = record.canonicalBytes();
        byte[] canonical2 = Entry.bdecode(record.canonicalBytes()).bencode();

        assertArrayEquals(canonical1, canonical2);
        assertFalse(new String(canonical1).contains("sig"));
        assertTrue(record.toEntry().dictionary().containsKey("sig"));
    }

    @Test
    void toEntryFromEntryRoundTripsAndVerifies() throws Exception {
        IdentityRecord record = IdentityRecord.createSigned(randomBytes(20), ed25519(), randomBytes(32), 49152);

        IdentityRecord parsed = IdentityRecord.fromEntry(record.toEntry());
        assertEquals(record, parsed);
        assertTrue(parsed.verifySignature());
    }

    @Test
    void fromEntryRejectsTamperedRecord() throws Exception {
        IdentityRecord record = IdentityRecord.createSigned(randomBytes(20), ed25519(), randomBytes(32), 49152);
        java.util.Map<String, Object> tampered = new java.util.TreeMap<>();
        java.util.Map<String, Entry> dict = record.toEntry().dictionary();
        tampered.put("ed25519_pub", new Entry(dict.get("ed25519_pub").string()));
        tampered.put("first_seen", new Entry(dict.get("first_seen").integer()));
        tampered.put("last_seen", new Entry(dict.get("last_seen").integer()));
        tampered.put("node_id", new Entry(dict.get("node_id").string()));
        tampered.put("sig", new Entry(dict.get("sig").string()));
        tampered.put("utp_port", new Entry(12345L));
        tampered.put("v", new Entry(dict.get("v").integer()));
        tampered.put("x25519_pub", new Entry(dict.get("x25519_pub").string()));

        assertThrows(IllegalArgumentException.class, () -> IdentityRecord.fromEntry(Entry.fromMap(tampered)));
    }

    @Test
    void withUpdatedLastSeenResignsRecord() throws Exception {
        KeyPair kp = ed25519();
        IdentityRecord r1 = IdentityRecord.createSigned(randomBytes(20), kp, randomBytes(32), 49152);
        IdentityRecord r2 = r1.withUpdatedLastSeen(r1.lastSeen() + 100, kp.getPrivate());

        assertEquals(r1.firstSeen(), r2.firstSeen());
        assertEquals(r1.lastSeen() + 100, r2.lastSeen());
        assertTrue(r2.verifySignature());
        assertNotEquals(java.util.Arrays.toString(r1.signature()), java.util.Arrays.toString(r2.signature()));
    }

    @Test
    void createSignedRejectsInvalidLengths() throws Exception {
        KeyPair kp = ed25519();
        assertThrows(IllegalArgumentException.class,
                () -> IdentityRecord.createSigned(new byte[19], kp, randomBytes(32), 49152));
        assertThrows(IllegalArgumentException.class,
                () -> IdentityRecord.createSigned(randomBytes(20), kp, new byte[31], 49152));
    }

    @Test
    void equalsAndHashCodeWork() throws Exception {
        KeyPair kp = ed25519();
        byte[] nodeId = randomBytes(20);
        byte[] x25519 = randomBytes(32);

        IdentityRecord r1 = IdentityRecord.createSigned(nodeId, kp, x25519, 49152);
        IdentityRecord r2 = IdentityRecord.fromEntry(r1.toEntry());
        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    private static KeyPair ed25519() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static byte[] fixedBytes(int n, int start) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (start + i);
        }
        return b;
    }
}
