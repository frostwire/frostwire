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
 *   [64 bytes] Ed25519 signature over the connection id (big-endian long)
 * </pre>
 */
final class RudpAuth {

    private RudpAuth() {
    }

    static boolean verifyHello(long connectionId, byte[] payload) {
        if (payload == null || payload.length != 32 + 64) {
            return false;
        }
        byte[] pub = Arrays.copyOfRange(payload, 0, 32);
        byte[] sig = Arrays.copyOfRange(payload, 32, 96);
        PublicKey key;
        try {
            key = com.frostwire.search.relay.icebridge.IceBridgeAuth.publicKeyFromRaw(pub);
        } catch (Throwable t) {
            return false;
        }
        byte[] message = new byte[8];
        for (int i = 7; i >= 0; i--) {
            message[i] = (byte) connectionId;
            connectionId >>>= 8;
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
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        byte[] message = new byte[8];
        long cid = connectionId;
        for (int i = 7; i >= 0; i--) {
            message[i] = (byte) cid;
            cid >>>= 8;
        }
        signer.update(message);
        byte[] sig = signer.sign();
        byte[] out = new byte[32 + 64];
        System.arraycopy(identity.ed25519PubRaw(), 0, out, 0, 32);
        System.arraycopy(sig, 0, out, 32, 64);
        return out;
    }
}