/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Identity record published to the DHT so other nodes can discover
 * this node's ed25519/x25519 public keys and uTP port.
 *
 * Stored as a BEP 46 mutable item in the DHT under key
 * SHA-256("frostwire-identity-v1:" + node_id)[:32].
 */
public final class IdentityRecord {
    public static final int VERSION = 1;
    public static final int NODE_ID_LENGTH = 20;
    public static final int ED25519_PUB_LENGTH = 32;
    public static final int X25519_PUB_LENGTH = 32;
    public static final String DHT_KEY_PREFIX = "frostwire-identity-v1";

    private final byte[] nodeId;
    private final byte[] ed25519Pub;
    private final byte[] x25519Pub;
    private final int utpPort;
    private final long firstSeen;
    private final long lastSeen;
    private final byte[] signature;
    private final byte[] dhtKey;

    private IdentityRecord(byte[] nodeId, byte[] ed25519Pub, byte[] x25519Pub,
                           int utpPort, long firstSeen, long lastSeen,
                           byte[] signature) {
        this.nodeId = nodeId.clone();
        this.ed25519Pub = ed25519Pub.clone();
        this.x25519Pub = x25519Pub.clone();
        this.utpPort = utpPort;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.signature = signature.clone();
        this.dhtKey = computeDhtKey(nodeId);
    }

    public byte[] nodeId() { return nodeId.clone(); }
    public byte[] ed25519Pub() { return ed25519Pub.clone(); }
    public byte[] x25519Pub() { return x25519Pub.clone(); }
    public int utpPort() { return utpPort; }
    public long firstSeen() { return firstSeen; }
    public long lastSeen() { return lastSeen; }
    public byte[] signature() { return signature.clone(); }
    public byte[] dhtKey() { return dhtKey.clone(); }

    public static byte[] computeDhtKey(byte[] nodeId) {
        if (nodeId == null || nodeId.length != NODE_ID_LENGTH) {
            throw new IllegalArgumentException("nodeId must be 20 bytes");
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(DHT_KEY_PREFIX.getBytes(StandardCharsets.US_ASCII));
            sha256.update(nodeId);
            byte[] hash = sha256.digest();
            return Arrays.copyOf(hash, 32);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String toCanonicalJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{");
        sb.append("\"v\":").append(VERSION).append(",");
        sb.append("\"node_id\":\"").append(toHex(nodeId)).append("\",");
        sb.append("\"ed25519_pub\":\"").append(b64(ed25519Pub)).append("\",");
        sb.append("\"x25519_pub\":\"").append(b64(x25519Pub)).append("\",");
        sb.append("\"utp_port\":").append(utpPort).append(",");
        sb.append("\"first_seen\":").append(firstSeen).append(",");
        sb.append("\"last_seen\":").append(lastSeen);
        sb.append("}");
        return sb.toString();
    }

    public byte[] canonicalBytes() {
        return toCanonicalJson().getBytes(StandardCharsets.US_ASCII);
    }

    public static IdentityRecord parse(Map<String, String> map) {
        int v = Integer.parseInt(map.get("v"));
        if (v != VERSION) {
            throw new IllegalArgumentException("Unsupported identity version: " + v);
        }
        byte[] nodeId = fromHex(map.get("node_id"));
        byte[] ed25519 = Base64.getDecoder().decode(map.get("ed25519_pub"));
        byte[] x25519 = Base64.getDecoder().decode(map.get("x25519_pub"));
        int port = Integer.parseInt(map.get("utp_port"));
        long first = Long.parseLong(map.get("first_seen"));
        long last = Long.parseLong(map.get("last_seen"));
        byte[] sig = Base64.getDecoder().decode(map.get("sig"));
        return new IdentityRecord(nodeId, ed25519, x25519, port, first, last, sig);
    }

    public static IdentityRecord create(byte[] nodeId, PublicKey ed25519, byte[] x25519,
                                       int utpPort) {
        if (nodeId.length != NODE_ID_LENGTH) {
            throw new IllegalArgumentException("nodeId must be 20 bytes");
        }
        if (ed25519.getEncoded().length != ED25519_PUB_LENGTH) {
            throw new IllegalArgumentException("ed25519 pub must be 32 bytes");
        }
        if (x25519.length != X25519_PUB_LENGTH) {
            throw new IllegalArgumentException("x25519 pub must be 32 bytes");
        }
        long now = Instant.now().getEpochSecond();
        return new IdentityRecord(nodeId, ed25519.getEncoded(), x25519, utpPort, now, now,
                new byte[64]);
    }

    public IdentityRecord withUpdatedLastSeen(long ts) {
        return new IdentityRecord(nodeId, ed25519Pub, x25519Pub, utpPort, firstSeen, ts,
                signature);
    }

    public IdentityRecord withSignature(byte[] sig) {
        return new IdentityRecord(nodeId, ed25519Pub, x25519Pub, utpPort, firstSeen, lastSeen, sig);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return result;
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdentityRecord)) return false;
        IdentityRecord that = (IdentityRecord) o;
        return Arrays.equals(nodeId, that.nodeId)
                && Arrays.equals(ed25519Pub, that.ed25519Pub)
                && Arrays.equals(x25519Pub, that.x25519Pub)
                && utpPort == that.utpPort
                && Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(nodeId);
        result = 31 * result + Arrays.hashCode(ed25519Pub);
        result = 31 * result + Arrays.hashCode(x25519Pub);
        result = 31 * result + utpPort;
        result = 31 * result + Arrays.hashCode(signature);
        return result;
    }
}
