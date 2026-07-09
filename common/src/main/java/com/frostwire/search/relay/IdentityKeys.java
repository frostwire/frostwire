/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Long-lived cryptographic identity for a FrostWire node.
 *
 * <p>Holds an Ed25519 key pair (signing), an X25519 key pair
 * (future encrypted relay channels), and a 20-byte DHT node ID
 * derived from SHA-1(raw Ed25519 public key).
 *
 * <p>On first run, {@link #loadOrCreate(File)} generates fresh keys
 * and persists them. On subsequent runs, it loads them from the
 * identity file. The file format is:
 * <pre>
 *   [4 bytes] Ed25519 PKCS#8  length (big-endian int)
 *   [N bytes] Ed25519 PKCS#8  private key
 *   [4 bytes] Ed25519 X.509   length (big-endian int)
 *   [M bytes] Ed25519 X.509   public key
 *   [4 bytes] X25519  PKCS#8  length (big-endian int)
 *   [P bytes] X25519  PKCS#8  private key
 *   [4 bytes] X25519  X.509   length (big-endian int)
 *   [Q bytes] X25519  X.509   public key
 * </pre>
 *
 * <p>Thread-safe: immutable after construction.
 */
public final class IdentityKeys {

    private static final Logger LOG = Logger.getLogger(IdentityKeys.class);

    /**
     * Byte offset of the raw 32-byte Ed25519 seed inside the JDK
     * PKCS#8 encoding. The structure is:
     * {@code 30 2e 02 01 00 30 05 06 03 2b 65 70 04 22 04 20 <32 bytes>}
     * so the seed starts at byte 16.
     */
    static final int ED25519_PKCS8_SEED_OFFSET = 16;

    private final KeyPair ed25519;
    private final KeyPair x25519;
    private final byte[] ed25519PubRaw;
    private final byte[] x25519PubRaw;
    private final byte[] nodeId;

    private IdentityKeys(KeyPair ed25519, KeyPair x25519) {
        this.ed25519 = ed25519;
        this.x25519 = x25519;
        this.ed25519PubRaw = IdentityRecord.extractRawEd25519(ed25519.getPublic());
        this.x25519PubRaw = extractRawX25519(x25519.getPublic());
        this.nodeId = sha1(this.ed25519PubRaw);
    }

    /** The Ed25519 key pair used for signing identity records and index announcements. */
    public KeyPair ed25519() {
        return ed25519;
    }

    /** The X25519 key pair reserved for future encrypted relay channels. */
    public KeyPair x25519() {
        return x25519;
    }

    /** Raw 32-byte Ed25519 public key (no X.509 wrapper). */
    public byte[] ed25519PubRaw() {
        return ed25519PubRaw.clone();
    }

    /** Raw 32-byte X25519 public key. */
    public byte[] x25519PubRaw() {
        return x25519PubRaw.clone();
    }

    /** 20-byte DHT node ID: SHA-1 of the raw Ed25519 public key. */
    public byte[] nodeId() {
        return nodeId.clone();
    }

    /**
     * 32-byte Ed25519 seed extracted from the PKCS#8 private key.
     *
     * <p>The JDK Ed25519 PKCS#8 encoding is 48 bytes: a 16-byte ASN.1
     * prefix followed by the 32-byte raw seed. This method extracts
     * the seed for use with libtorrent's NaCl-based
     * {@code Ed25519.createKeypair(seed)} or for constructing the
     * 64-byte NaCl secret key ({@code seed || pubkey}).
     */
    public byte[] ed25519Seed() {
        byte[] pkcs8 = ed25519.getPrivate().getEncoded();
        if (pkcs8.length < ED25519_PKCS8_SEED_OFFSET + 32) {
            throw new IllegalStateException(
                    "Unexpected PKCS#8 length: " + pkcs8.length);
        }
        return Arrays.copyOfRange(pkcs8,
                ED25519_PKCS8_SEED_OFFSET,
                ED25519_PKCS8_SEED_OFFSET + 32);
    }

    /**
     * 64-byte NaCl-format Ed25519 secret key (expanded form).
     *
     * <p>This is the format expected by libtorrent's
     * {@code SessionManager.dhtPutItem(pubkey, privkey, entry, salt)}
     * and {@code Ed25519.sign(message, pubkey, secretkey)}.
     *
     * <p>The NaCl secret key is derived from the seed via
     * {@code Ed25519.createKeypair(seed)} — it is NOT a simple
     * concatenation of seed and pubkey.
     */
    public byte[] ed25519SecretKeyNaCl() {
        byte[] seed = ed25519Seed();
        com.frostwire.jlibtorrent.Pair<byte[], byte[]> pair =
                com.frostwire.jlibtorrent.Ed25519.createKeypair(seed);
        return pair.second;
    }

    /**
     * Load an existing identity from {@code file}, or generate a fresh
     * one (with proof-of-work at {@link KarmaConstants#IDENTITY_DIFFICULTY}
     * leading zero bits) and persist it. The file parent directory is
     * created if missing.
     *
     * <p>Migration: if an existing identity does not meet the PoW
     * difficulty, it is accepted as-is. PoW is enforced on new identity
     * creation only; legacy identities from before the PoW feature
     * continue to work.
     */
    public static IdentityKeys loadOrCreate(File file) throws IOException, GeneralSecurityException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }
        if (file.exists() && file.length() > 0) {
            LOG.info("Loading identity from " + file.getAbsolutePath());
            return load(file);
        }
        int difficulty = KarmaConstants.IDENTITY_DIFFICULTY;
        LOG.info("No identity at " + file.getAbsolutePath()
                + " — mining proof-of-work identity (" + difficulty
                + " leading zero bits; typically a few seconds with native Ed25519)…");
        long t0 = System.currentTimeMillis();
        IdentityKeys keys = generate(difficulty);
        save(keys, file);
        long ms = System.currentTimeMillis() - t0;
        LOG.info("Generated new identity keys at " + file.getAbsolutePath()
                + " in " + ms + " ms (difficulty=" + difficulty + ")");
        return keys;
    }

    /** Generate a fresh identity with no proof-of-work requirement. */
    public static IdentityKeys generate() throws GeneralSecurityException {
        return generate(0);
    }

    /**
     * Reconstruct an identity from a 32-byte Ed25519 seed. The X25519
     * keypair is freshly generated (it cannot be derived from the
     * Ed25519 seed). No proof-of-work is performed.
     *
     * <p>The Ed25519 keypair is deterministically derived from the seed
     * via libtorrent's {@code Ed25519.createKeypair(seed)}, then
     * converted to JDK KeyPair form.
     *
     * <p>Used to restore an identity from a BIP39 mnemonic phrase.
     */
    public static IdentityKeys fromSeed(byte[] seed) throws GeneralSecurityException {
        if (seed == null || seed.length != 32) {
            throw new IllegalArgumentException("seed must be 32 bytes");
        }
        com.frostwire.jlibtorrent.Pair<byte[], byte[]> pair =
                com.frostwire.jlibtorrent.Ed25519.createKeypair(seed);
        byte[] rawPub = pair.first;

        KeyPair edPair = buildEd25519KeyPair(seed, rawPub);

        KeyPairGenerator xdh = KeyPairGenerator.getInstance("XDH");
        xdh.initialize(NamedParameterSpec.X25519);
        KeyPair xPair = xdh.generateKeyPair();

        return new IdentityKeys(edPair, xPair);
    }

    private static KeyPair buildEd25519KeyPair(byte[] seed, byte[] rawPub) throws GeneralSecurityException {
        byte[] pkcs8 = buildEd25519Pkcs8FromSeed(seed);
        byte[] x509 = buildEd25519X509FromRawPub(rawPub);
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(x509));
        return new KeyPair(pub, priv);
    }

    private static byte[] buildEd25519Pkcs8FromSeed(byte[] seed) {
        byte[] pkcs8 = new byte[48];
        pkcs8[0] = 0x30; pkcs8[1] = 0x2e;
        pkcs8[2] = 0x02; pkcs8[3] = 0x01; pkcs8[4] = 0x00;
        pkcs8[5] = 0x30; pkcs8[6] = 0x05; pkcs8[7] = 0x06;
        pkcs8[8] = 0x03; pkcs8[9] = 0x2b; pkcs8[10] = 0x65; pkcs8[11] = 0x70;
        pkcs8[12] = 0x04; pkcs8[13] = 0x22; pkcs8[14] = 0x04; pkcs8[15] = 0x20;
        System.arraycopy(seed, 0, pkcs8, 16, 32);
        return pkcs8;
    }

    private static byte[] buildEd25519X509FromRawPub(byte[] rawPub) {
        byte[] x509 = new byte[44];
        x509[0] = 0x30; x509[1] = 0x2a;
        x509[2] = 0x30; x509[3] = 0x05;
        x509[4] = 0x06; x509[5] = 0x03; x509[6] = 0x2b; x509[7] = 0x65; x509[8] = 0x70;
        x509[9] = 0x03; x509[10] = 0x21; x509[11] = 0x00;
        System.arraycopy(rawPub, 0, x509, 12, 32);
        return x509;
    }

    /**
     * Generate a PoW-qualified identity. Loops until
     * {@code SHA-1(ed25519PubRaw)} has at least {@code minDifficulty}
     * leading zero bits. With {@code minDifficulty=0}, the first
     * generated keypair is returned. With {@code minDifficulty=20},
     * the loop runs until ~1 million SHA-1 attempts yield a qualifying
     * pubkey (~1-2 seconds using native jlibtorrent Ed25519).
     *
     * <p>Uses jlibtorrent's native Ed25519 implementation for key
     * generation instead of the JDK's {@code KeyPairGenerator}, which
     * is a pure-Java implementation on some platforms and can be
     * 50-100x slower.
     */
    public static IdentityKeys generate(int minDifficulty) throws GeneralSecurityException {
        if (minDifficulty < 0) {
            throw new IllegalArgumentException("minDifficulty must be >= 0");
        }
        SecureRandom rng = new SecureRandom();
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        KeyPairGenerator xdh = KeyPairGenerator.getInstance("XDH");
        xdh.initialize(NamedParameterSpec.X25519);

        while (true) {
            byte[] seed = new byte[32];
            rng.nextBytes(seed);
            com.frostwire.jlibtorrent.Pair<byte[], byte[]> pair =
                    com.frostwire.jlibtorrent.Ed25519.createKeypair(seed);
            byte[] rawPub = pair.first;
            if (minDifficulty == 0 || countLeadingZeroBits(sha1.digest(rawPub)) >= minDifficulty) {
                KeyPair edPair = buildEd25519KeyPair(seed, rawPub);
                return new IdentityKeys(edPair, xdh.generateKeyPair());
            }
        }
    }

    /**
     * Count the number of leading zero bits in {@code hash}, from the
     * most significant bit of byte 0 downward. A hash of all zeros
     * returns 160 (SHA-1 length in bits). A hash whose first bit is
     * set returns 0.
     */
    public static int countLeadingZeroBits(byte[] hash) {
        if (hash == null) {
            throw new IllegalArgumentException("hash is null");
        }
        int count = 0;
        for (int i = 0; i < hash.length; i++) {
            int b = hash[i] & 0xFF;
            if (b == 0) {
                count += 8;
            } else {
                count += Integer.numberOfLeadingZeros(b) - 24;
                break;
            }
        }
        return count;
    }

    /**
     * Load identity keys from a file previously written by {@link #save}.
     */
    public static IdentityKeys load(File file) throws IOException, GeneralSecurityException {
        byte[] data = readAllBytes(file);
        int offset = 0;

        int edPrivLen = readInt(data, offset);
        offset += 4;
        byte[] edPkcs8 = Arrays.copyOfRange(data, offset, offset + edPrivLen);
        offset += edPrivLen;

        int edPubLen = readInt(data, offset);
        offset += 4;
        byte[] edX509 = Arrays.copyOfRange(data, offset, offset + edPubLen);
        offset += edPubLen;

        int xPrivLen = readInt(data, offset);
        offset += 4;
        byte[] xPkcs8 = Arrays.copyOfRange(data, offset, offset + xPrivLen);
        offset += xPrivLen;

        int xPubLen = readInt(data, offset);
        offset += 4;
        byte[] xX509 = Arrays.copyOfRange(data, offset, offset + xPubLen);

        KeyPair ed = reconstruct("Ed25519", edPkcs8, edX509);
        KeyPair x = reconstruct("XDH", xPkcs8, xX509);
        return new IdentityKeys(ed, x);
    }

    /**
     * Persist identity keys to a file. Both private (PKCS#8) and public
     * (X.509) encodings are stored so round-trip does not depend on
     * JDK-version-specific ASN.1 layout quirks.
     */
    public static void save(IdentityKeys keys, File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent);
        }
        byte[] edPriv = keys.ed25519.getPrivate().getEncoded();
        byte[] edPub = keys.ed25519.getPublic().getEncoded();
        byte[] xPriv = keys.x25519.getPrivate().getEncoded();
        byte[] xPub = keys.x25519.getPublic().getEncoded();

        byte[] out = new byte[4 + edPriv.length + 4 + edPub.length
                + 4 + xPriv.length + 4 + xPub.length];
        int offset = 0;
        offset = writeLenAndBytes(out, offset, edPriv);
        offset = writeLenAndBytes(out, offset, edPub);
        offset = writeLenAndBytes(out, offset, xPriv);
        writeLenAndBytes(out, offset, xPub);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(out);
        }
    }

    private static byte[] readAllBytes(File file) throws IOException {
        long len = file.length();
        if (len < 0 || len > Integer.MAX_VALUE) {
            throw new IOException("identity file too large: " + file);
        }
        byte[] data = new byte[(int) len];
        try (FileInputStream in = new FileInputStream(file)) {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) {
                    throw new IOException("truncated identity file: " + file);
                }
                off += n;
            }
        }
        return data;
    }

    private static KeyPair reconstruct(String algorithm, byte[] pkcs8, byte[] x509)
            throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance(algorithm);
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(x509));
        return new KeyPair(pub, priv);
    }

    static byte[] extractRawX25519(PublicKey x25519) {
        byte[] encoded = x25519.getEncoded();
        // X.509 SubjectPublicKeyInfo for X25519: 12-byte prefix + 32-byte key
        if (encoded.length == 44) {
            return Arrays.copyOfRange(encoded, 12, 44);
        }
        if (encoded.length == 32) {
            return encoded.clone();
        }
        throw new IllegalArgumentException(
                "Unexpected X25519 key encoding: " + encoded.length + " bytes");
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    private static int writeLenAndBytes(byte[] out, int offset, byte[] src) {
        writeInt(out, offset, src.length);
        offset += 4;
        System.arraycopy(src, 0, out, offset, src.length);
        return offset + src.length;
    }
}
