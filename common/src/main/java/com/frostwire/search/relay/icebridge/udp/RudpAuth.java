/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;

/**
 * Authentication helpers for rUDP session setup.
 *
 * <p>A {@link RudpPacket.Type#HELLO} payload is expected to contain:
 * <pre>
 *   [32 bytes] Ed25519 public key (raw)
 *   [8 bytes]  Unix timestamp (big-endian, seconds)
 *   [64 bytes] Ed25519 signature over (connectionId || timestamp)
 * </pre>
 *
 * <p>The timestamp prevents indefinite replay of captured HELLO packets.
 * A HELLO with a timestamp older than {@link #MAX_HELLO_SKEW_SEC} seconds
 * is rejected.
 */
final class RudpAuth {

    /** Maximum acceptable age of a HELLO packet (seconds). */
    static final long MAX_HELLO_SKEW_SEC = 300;

    private RudpAuth() {
    }

    static boolean verifyHello(long connectionId, byte[] payload) {
        // New format: 32 (pub) + 8 (timestamp) + 64 (sig) = 104 bytes
        // Legacy format: 32 (pub) + 64 (sig) = 96 bytes (no timestamp — replay-vulnerable)
        if (payload == null) {
            return false;
        }
        if (payload.length == 104) {
            return verifyHelloWithTimestamp(connectionId, payload);
        }
        // Legacy 96-byte format: accept but log (backward compat).
        if (payload.length == 96) {
            return verifyLegacyHello(connectionId, payload);
        }
        return false;
    }

    private static boolean verifyHelloWithTimestamp(long connectionId, byte[] payload) {
        byte[] pub = Arrays.copyOfRange(payload, 0, 32);
        long timestamp = readLongBE(payload, 32);
        byte[] sig = Arrays.copyOfRange(payload, 40, 104);

        // Reject stale HELLOs to prevent replay.
        long now = System.currentTimeMillis() / 1000L;
        long diff = now - timestamp;
        long skew = diff >= 0 ? diff : -diff;
        if (skew > MAX_HELLO_SKEW_SEC) {
            return false;
        }

        // Signed message: connectionId (8 bytes) || timestamp (8 bytes)
        byte[] message = new byte[16];
        writeLongBE(message, 0, connectionId);
        writeLongBE(message, 8, timestamp);

        return verifySignature(pub, sig, message);
    }

    private static boolean verifyLegacyHello(long connectionId, byte[] payload) {
        byte[] pub = Arrays.copyOfRange(payload, 0, 32);
        byte[] sig = Arrays.copyOfRange(payload, 32, 96);
        byte[] message = new byte[8];
        writeLongBE(message, 0, connectionId);
        return verifySignature(pub, sig, message);
    }

    private static boolean verifySignature(byte[] pub, byte[] sig, byte[] message) {
        PublicKey key;
        try {
            key = com.frostwire.search.relay.icebridge.IceBridgeAuth.publicKeyFromRaw(pub);
        } catch (Throwable t) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(sig);
        } catch (Throwable t) {
            return false;
        }
    }

    static byte[] createHelloPayload(com.frostwire.search.relay.IdentityKeys identity, long connectionId)
            throws Exception {
        long timestamp = System.currentTimeMillis() / 1000L;
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        // Signed message: connectionId (8 bytes) || timestamp (8 bytes)
        byte[] message = new byte[16];
        writeLongBE(message, 0, connectionId);
        writeLongBE(message, 8, timestamp);
        signer.update(message);
        byte[] sig = signer.sign();
        byte[] out = new byte[32 + 8 + 64]; // pub + timestamp + sig
        System.arraycopy(identity.ed25519PubRaw(), 0, out, 0, 32);
        writeLongBE(out, 32, timestamp);
        System.arraycopy(sig, 0, out, 40, 64);
        return out;
    }

    private static void writeLongBE(byte[] buf, int offset, long value) {
        buf[offset] = (byte) (value >>> 56);
        buf[offset + 1] = (byte) (value >>> 48);
        buf[offset + 2] = (byte) (value >>> 40);
        buf[offset + 3] = (byte) (value >>> 32);
        buf[offset + 4] = (byte) (value >>> 24);
        buf[offset + 5] = (byte) (value >>> 16);
        buf[offset + 6] = (byte) (value >>> 8);
        buf[offset + 7] = (byte) value;
    }

    private static long readLongBE(byte[] buf, int offset) {
        return ((long) (buf[offset] & 0xff) << 56)
                | ((long) (buf[offset + 1] & 0xff) << 48)
                | ((long) (buf[offset + 2] & 0xff) << 40)
                | ((long) (buf[offset + 3] & 0xff) << 32)
                | ((long) (buf[offset + 4] & 0xff) << 24)
                | ((long) (buf[offset + 5] & 0xff) << 16)
                | ((long) (buf[offset + 6] & 0xff) << 8)
                | (buf[offset + 7] & 0xff);
    }
}