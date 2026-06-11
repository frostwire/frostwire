/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
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
     * Load an existing identity from {@code file}, or generate a fresh
     * one and persist it. The file parent directory is created if missing.
     */
    public static IdentityKeys loadOrCreate(File file) throws IOException, GeneralSecurityException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }
        if (file.exists() && file.length() > 0) {
            return load(file);
        }
        IdentityKeys keys = generate();
        save(keys, file);
        LOG.info("Generated new identity keys at " + file.getAbsolutePath());
        return keys;
    }

    /** Generate a fresh identity with random Ed25519 and X25519 key pairs. */
    public static IdentityKeys generate() throws GeneralSecurityException {
        KeyPair ed = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPairGenerator xdh = KeyPairGenerator.getInstance("XDH");
        xdh.initialize(NamedParameterSpec.X25519);
        KeyPair x = xdh.generateKeyPair();
        return new IdentityKeys(ed, x);
    }

    /**
     * Load identity keys from a file previously written by {@link #save}.
     */
    static IdentityKeys load(File file) throws IOException, GeneralSecurityException {
        byte[] data = Files.readAllBytes(file.toPath());
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
    static void save(IdentityKeys keys, File file) throws IOException {
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

        Files.write(file.toPath(), out);
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
