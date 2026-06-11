/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class IdentityKeysTest {

    private File tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("identity-keys-test-").toFile();
    }

    @AfterEach
    void tearDown() {
        deleteRecursive(tempDir);
    }

    @Test
    void generateProducesValidKeys() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();

        assertNotNull(keys.ed25519());
        assertNotNull(keys.x25519());
        assertEquals(32, keys.ed25519PubRaw().length, "Ed25519 raw pub is 32 bytes");
        assertEquals(32, keys.x25519PubRaw().length, "X25519 raw pub is 32 bytes");
        assertEquals(20, keys.nodeId().length, "Node ID is SHA-1 length (20 bytes)");
    }

    @Test
    void nodeIdIsSha1OfEd25519PubRaw() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        byte[] expected = MessageDigest.getInstance("SHA-1").digest(keys.ed25519PubRaw());
        assertArrayEquals(expected, keys.nodeId());
    }

    @Test
    void ed25519KeyCanSign() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        byte[] message = "hello frostwire".getBytes();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.ed25519().getPrivate());
        signer.update(message);
        byte[] sig = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(keys.ed25519().getPublic());
        verifier.update(message);
        assertTrue(verifier.verify(sig), "Signature must verify with the matching public key");
    }

    @Test
    void saveAndLoadRoundTrips() throws Exception {
        File file = new File(tempDir, "identity.dat");
        IdentityKeys original = IdentityKeys.generate();
        IdentityKeys.save(original, file);

        assertTrue(file.exists(), "File must be created");
        assertTrue(file.length() > 0, "File must not be empty");

        IdentityKeys loaded = IdentityKeys.load(file);

        assertArrayEquals(original.ed25519PubRaw(), loaded.ed25519PubRaw(),
                "Ed25519 public key must survive round-trip");
        assertArrayEquals(original.x25519PubRaw(), loaded.x25519PubRaw(),
                "X25519 public key must survive round-trip");
        assertArrayEquals(original.nodeId(), loaded.nodeId(),
                "Node ID must survive round-trip");
    }

    @Test
    void loadedKeyCanSignAndVerify() throws Exception {
        File file = new File(tempDir, "identity.dat");
        IdentityKeys original = IdentityKeys.generate();
        IdentityKeys.save(original, file);
        IdentityKeys loaded = IdentityKeys.load(file);

        byte[] message = "relay protocol v1".getBytes();

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(loaded.ed25519().getPrivate());
        signer.update(message);
        byte[] sig = signer.sign();

        // Verify with the original public key (cross-check)
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(original.ed25519().getPublic());
        verifier.update(message);
        assertTrue(verifier.verify(sig), "Loaded key signatures must verify against original pub");
    }

    @Test
    void loadOrCreateGeneratesWhenFileMissing() throws Exception {
        File file = new File(tempDir, "new-identity.dat");
        assertFalse(file.exists());

        IdentityKeys keys = IdentityKeys.loadOrCreate(file);

        assertTrue(file.exists(), "File must be created on first call");
        assertEquals(32, keys.ed25519PubRaw().length);
        assertEquals(20, keys.nodeId().length);
    }

    @Test
    void loadOrCreateLoadsExistingFile() throws Exception {
        File file = new File(tempDir, "identity.dat");
        IdentityKeys first = IdentityKeys.loadOrCreate(file);
        IdentityKeys second = IdentityKeys.loadOrCreate(file);

        assertArrayEquals(first.ed25519PubRaw(), second.ed25519PubRaw(),
                "Second call must return the same identity");
        assertArrayEquals(first.nodeId(), second.nodeId());
    }

    @Test
    void loadOrCreateRejectsNullFile() {
        assertThrows(IllegalArgumentException.class, () -> IdentityKeys.loadOrCreate(null));
    }

    @Test
    void identityRecordRoundTripWithIdentityKeys() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        IdentityRecord record = IdentityRecord.createSigned(
                keys.nodeId(),
                keys.ed25519(),
                keys.x25519PubRaw(),
                49152);

        assertTrue(record.verifySignature(), "Record signed with IdentityKeys must verify");
        assertArrayEquals(keys.nodeId(), record.nodeId());
        assertArrayEquals(keys.ed25519PubRaw(), record.ed25519Pub());
        assertArrayEquals(keys.x25519PubRaw(), record.x25519Pub());
    }

    @Test
    void clonedArraysAreDefensive() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        byte[] nodeId = keys.nodeId();
        byte[] edPub = keys.ed25519PubRaw();
        byte[] xPub = keys.x25519PubRaw();

        // Mutate the returned arrays
        Arrays.fill(nodeId, (byte) 0xff);
        Arrays.fill(edPub, (byte) 0xff);
        Arrays.fill(xPub, (byte) 0xff);

        // The internal state should be unchanged
        assertFalse(Arrays.equals(nodeId, keys.nodeId()), "nodeId must be a defensive copy");
        assertFalse(Arrays.equals(edPub, keys.ed25519PubRaw()), "ed25519PubRaw must be a defensive copy");
        assertFalse(Arrays.equals(xPub, keys.x25519PubRaw()), "x25519PubRaw must be a defensive copy");
    }

    @Test
    void ed25519SeedIs32Bytes() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        byte[] seed = keys.ed25519Seed();
        assertEquals(32, seed.length, "Ed25519 seed must be 32 bytes");
    }

    @Test
    void ed25519SecretKeyNaClIs64Bytes() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        byte[] sk = keys.ed25519SecretKeyNaCl();
        assertEquals(64, sk.length, "NaCl secret key is 64 bytes (expanded form)");
    }

    @Test
    void naclSecretKeyRoundTripsWithSeed() throws Exception {
        IdentityKeys keys = IdentityKeys.generate();
        byte[] seed = keys.ed25519Seed();

        // Derive the keypair using libtorrent's Ed25519 helper
        com.frostwire.jlibtorrent.Pair<byte[], byte[]> pair =
                com.frostwire.jlibtorrent.Ed25519.createKeypair(seed);
        byte[] naclPub = pair.first;
        byte[] naclSk = pair.second;

        // The NaCl pubkey derived from the seed must match our Java pubkey
        assertArrayEquals(keys.ed25519PubRaw(), naclPub,
                "NaCl pubkey from seed must match Java Ed25519 pubkey");
        // The NaCl secret key must match our constructed one
        assertArrayEquals(keys.ed25519SecretKeyNaCl(), naclSk,
                "NaCl sk = seed || pubkey must match Ed25519.createKeypair output");
    }

    @Test
    void generateProducesDifferentKeysEachTime() throws Exception {
        IdentityKeys k1 = IdentityKeys.generate();
        IdentityKeys k2 = IdentityKeys.generate();

        assertFalse(Arrays.equals(k1.ed25519PubRaw(), k2.ed25519PubRaw()),
                "Two generates should produce different Ed25519 keys");
        assertFalse(Arrays.equals(k1.nodeId(), k2.nodeId()),
                "Two generates should produce different node IDs");
    }

    // --- PoW mining tests ---

    @Test
    void countLeadingZeroBitsAllZeros() {
        assertEquals(160, IdentityKeys.countLeadingZeroBits(new byte[20]),
                "20 zero bytes = 160 leading zero bits");
    }

    @Test
    void countLeadingZeroBitsFirstBitSet() {
        byte[] hash = new byte[20];
        hash[0] = (byte) 0x80;
        assertEquals(0, IdentityKeys.countLeadingZeroBits(hash),
                "0x80 in byte 0 means 0 leading zero bits");
    }

    @Test
    void countLeadingZeroBitsMixedByte() {
        byte[] hash = new byte[20];
        hash[0] = 0x00;
        hash[1] = 0x01;
        // 8 zero bits from byte 0, then 7 leading zero bits in byte 1 (0x01 = 00000001)
        assertEquals(15, IdentityKeys.countLeadingZeroBits(hash));
    }

    @Test
    void countLeadingZeroBitsRejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> IdentityKeys.countLeadingZeroBits(null));
    }

    @Test
    void generateWithDifficultyProducesQualifyingNodeId() throws Exception {
        // Use a low difficulty so the test is fast; verify the contract.
        IdentityKeys keys = IdentityKeys.generate(8);
        int actual = IdentityKeys.countLeadingZeroBits(keys.nodeId());
        assertTrue(actual >= 8,
                "nodeId must have at least 8 leading zero bits, got " + actual);
    }

    @Test
    void generateZeroDifficultyAlwaysSucceeds() throws Exception {
        IdentityKeys keys = IdentityKeys.generate(0);
        assertNotNull(keys);
        assertEquals(32, keys.ed25519PubRaw().length);
    }

    @Test
    void generateRejectsNegativeDifficulty() {
        assertThrows(IllegalArgumentException.class,
                () -> IdentityKeys.generate(-1));
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
