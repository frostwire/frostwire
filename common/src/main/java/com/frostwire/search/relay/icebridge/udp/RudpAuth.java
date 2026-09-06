/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeAuth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;

/**
 * Version 2 migration: Ed25519 signatures provide integrity, NOT encryption or
 * forward secrecy. This is a versioned application protocol, not TLS/DTLS and
 * not a claim of a formally verified secure channel. Do not accept v1 fallback.
 *
 * <p>HELLO contains initiator pub, intended responder (zero only for explicit
 * identity discovery), fresh 32-byte challenge and timestamp. HELLO_ACK binds
 * both peers, the complete HELLO digest and a fresh responder challenge. FINISH
 * proves the initiator saw that response; READY confirms responder acceptance.
 * Every later packet signs the transcript digest, sender, recipient, version,
 * type, CID, sequence, ACK and exact payload. No session or routing mutation may
 * precede verification. Addresses are deliberately not signed (NAT); replayed
 * packets must never authorize migration. Discovery without an expected key
 * proves possession only, not an externally trusted identity.
 */
final class RudpAuth {
    static final long MAX_HELLO_SKEW_SEC = 60;
    static final int SIGNATURE_LENGTH = 64;
    static final int HELLO_PAYLOAD_LENGTH = 168;
    static final int ACK_PAYLOAD_LENGTH = 192;
    private static final byte[] DOMAIN = "FrostWire-IceBridge-rUDP-v2".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private RudpAuth() {
    }

    static byte[] createHelloPayload(IdentityKeys identity, long cid) throws Exception {
        return createHelloPayload(identity, cid, null);
    }

    static byte[] createHelloPayload(IdentityKeys identity, long cid, byte[] expectedPub) throws Exception {
        byte[] nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        byte[] body = ByteBuffer.allocate(104).put(identity.ed25519PubRaw())
                .put(expectedPub == null ? new byte[32] : expectedPub).put(nonce)
                .putLong(System.currentTimeMillis() / 1000L).array();
        return signBody(identity, RudpPacket.Type.HELLO, cid, body);
    }

    static boolean verifyHello(long cid, byte[] payload) {
        if (payload == null || payload.length != HELLO_PAYLOAD_LENGTH) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000L;
        long timestamp = ByteBuffer.wrap(payload, 96, 8).getLong();
        return timestamp >= now - MAX_HELLO_SKEW_SEC && timestamp <= now + MAX_HELLO_SKEW_SEC
                && verifyBody(RudpPacket.Type.HELLO, cid, payload);
    }

    static boolean intendedFor(byte[] hello, byte[] pub) {
        byte[] expected = Arrays.copyOfRange(hello, 32, 64);
        return Arrays.equals(expected, new byte[32]) || Arrays.equals(expected, pub);
    }

    static byte[] createAckPayload(IdentityKeys identity, long cid, byte[] hello) throws Exception {
        byte[] nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        byte[] body = ByteBuffer.allocate(128).put(identity.ed25519PubRaw())
                .put(hello, 0, 32).put(digest(hello)).put(nonce).array();
        return signBody(identity, RudpPacket.Type.HELLO_ACK, cid, body);
    }

    static boolean verifyAck(long cid, byte[] hello, byte[] ack) {
        return hello != null && ack != null && ack.length == ACK_PAYLOAD_LENGTH
                && Arrays.equals(Arrays.copyOfRange(hello, 0, 32), Arrays.copyOfRange(ack, 32, 64))
                && Arrays.equals(digest(hello), Arrays.copyOfRange(ack, 64, 96))
                && intendedFor(hello, Arrays.copyOfRange(ack, 0, 32))
                && verifyBody(RudpPacket.Type.HELLO_ACK, cid, ack);
    }

    static byte[] transcript(byte[] hello, byte[] ack) {
        return digest(ByteBuffer.allocate(hello.length + ack.length).put(hello).put(ack).array());
    }

    static RudpPacket protect(IdentityKeys identity, byte[] peer, byte[] transcript, RudpPacket packet)
            throws Exception {
        byte[] body = packet.payload();
        Signature signer = IdentityKeys.softwareSignature("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        signer.update(packetBytes(identity.ed25519PubRaw(), peer, transcript, packet));
        byte[] payload = ByteBuffer.allocate(body.length + SIGNATURE_LENGTH).put(body).put(signer.sign()).array();
        return new RudpPacket(packet.type(), packet.connectionId(), packet.sequence(), packet.ackThrough(), payload);
    }

    static RudpPacket unprotect(byte[] peer, byte[] local, byte[] transcript, RudpPacket packet) {
        byte[] payload = packet.payload();
        if (peer == null || transcript == null || payload.length < SIGNATURE_LENGTH) {
            return null;
        }
        byte[] body = Arrays.copyOf(payload, payload.length - SIGNATURE_LENGTH);
        RudpPacket plain = new RudpPacket(packet.type(), packet.connectionId(), packet.sequence(), packet.ackThrough(), body);
        return verify(peer, Arrays.copyOfRange(payload, body.length, payload.length),
                packetBytes(peer, local, transcript, plain)) ? plain : null;
    }

    private static byte[] packetBytes(byte[] sender, byte[] recipient, byte[] transcript, RudpPacket packet) {
        byte[] body = packet.payload();
        return ByteBuffer.allocate(DOMAIN.length + 2 + 32 + 32 + 32 + 8 + 4 + 4 + 4 + body.length)
                .put(DOMAIN).put((byte) RudpPacket.VERSION).put((byte) packet.type().code())
                .put(sender).put(recipient).put(transcript).putLong(packet.connectionId())
                .putInt(packet.sequence()).putInt(packet.ackThrough()).putInt(body.length).put(body).array();
    }

    private static byte[] signBody(IdentityKeys identity, RudpPacket.Type role, long cid, byte[] body)
            throws Exception {
        Signature signer = IdentityKeys.softwareSignature("Ed25519");
        signer.initSign(identity.ed25519().getPrivate());
        signer.update(handshakeBytes(role, cid, body));
        return ByteBuffer.allocate(body.length + SIGNATURE_LENGTH).put(body).put(signer.sign()).array();
    }

    private static boolean verifyBody(RudpPacket.Type role, long cid, byte[] payload) {
        int length = payload.length - SIGNATURE_LENGTH;
        return verify(Arrays.copyOf(payload, 32), Arrays.copyOfRange(payload, length, payload.length),
                handshakeBytes(role, cid, Arrays.copyOf(payload, length)));
    }

    private static byte[] handshakeBytes(RudpPacket.Type role, long cid, byte[] body) {
        return ByteBuffer.allocate(DOMAIN.length + 2 + 8 + body.length).put(DOMAIN)
                .put((byte) RudpPacket.VERSION).put((byte) role.code()).putLong(cid).put(body).array();
    }

    private static boolean verify(byte[] pub, byte[] signature, byte[] bytes) {
        try {
            Signature verifier = IdentityKeys.softwareSignature("Ed25519");
            verifier.initVerify(IceBridgeAuth.publicKeyFromRaw(pub));
            verifier.update(bytes);
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
