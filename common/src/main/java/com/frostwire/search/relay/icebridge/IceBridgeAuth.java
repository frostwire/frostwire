/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.search.relay.IdentityKeys;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Small authentication helpers for the IceBridge control plane.
 *
 * <p>The relay mesh itself performs its own Ed25519 handshake; this class is
 * only for verifying that registration and lookup requests arriving over the
 * local HTTP API were signed by the private key matching the claimed public
 * key.
 */
public final class IceBridgeAuth {

    /**
     * ASN.1 prefix (X.509 SubjectPublicKeyInfo) for a raw 32-byte Ed25519 public key.
     * {@code 30 2a 30 05 06 03 2b 65 70 03 21 00}
     */
    private static final byte[] ED25519_X509_PREFIX = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private IceBridgeAuth() {
        // utility class
    }

    /**
     * Reconstruct a JDK {@link PublicKey} from a raw 32-byte Ed25519 public key.
     */
    public static PublicKey publicKeyFromRaw(byte[] raw) throws Exception {
        if (raw == null || raw.length != 32) {
            throw new IllegalArgumentException("raw Ed25519 public key must be 32 bytes");
        }
        byte[] x509 = new byte[ED25519_X509_PREFIX.length + raw.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, x509, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(raw, 0, x509, ED25519_X509_PREFIX.length, raw.length);
        KeyFactory kf = IdentityKeys.softwareKeyFactory("Ed25519");
        return kf.generatePublic(new X509EncodedKeySpec(x509));
    }

    /**
     * Decode a base64url-no-padding string to raw bytes.
     */
    public static byte[] decodeBase64(String s) {
        if (s == null || s.isEmpty()) {
            return new byte[0];
        }
        return BASE64_DECODER.decode(s);
    }

    /**
     * Verify an Ed25519 signature over UTF-8 bytes of a message.
     */
    public static boolean verify(byte[] rawPub, byte[] message, byte[] signature) {
        if (rawPub == null || rawPub.length != 32 || message == null || signature == null) {
            return false;
        }
        try {
            PublicKey pub = publicKeyFromRaw(rawPub);
            Signature verifier = IdentityKeys.softwareSignature("Ed25519");
            verifier.initVerify(pub);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (Throwable t) {
            return false;
        }
    }
}