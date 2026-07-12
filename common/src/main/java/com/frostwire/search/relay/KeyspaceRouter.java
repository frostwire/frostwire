/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Foundation for responsibility-based routing of search queries.
 *
 * <p>Maps keywords to a 20-byte keyspace target (SHA-1) and ranks peers by
 * XOR distance of that target to each peer's Ed25519 public key prefix
 * (first 20 bytes). Closer peers are preferred for answering; IceBridge
 * itself stays protocol-agnostic — this ranking is an application choice.
 *
 * <p>v1 usage: reorder trust-selected peers before fanout. Full exclusive
 * keyspace ownership is future work.
 */
public final class KeyspaceRouter {

    private KeyspaceRouter() {
    }

    /**
     * SHA-1 of UTF-8 keywords (trimmed lower-case) as the keyspace target.
     */
    public static byte[] keyspaceTarget(String keywords) {
        if (keywords == null) {
            throw new IllegalArgumentException("keywords is null");
        }
        String normalized = keywords.trim().toLowerCase();
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return sha1.digest(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    /**
     * XOR distance between two equal-length digests (big-endian magnitude
     * comparison via byte-wise compare of the XOR result).
     */
    public static int compareXorDistance(byte[] target, byte[] a, byte[] b) {
        if (target == null || a == null || b == null) {
            throw new IllegalArgumentException("null arg");
        }
        int len = Math.min(target.length, Math.min(a.length, b.length));
        for (int i = 0; i < len; i++) {
            int xa = (target[i] ^ a[i]) & 0xFF;
            int xb = (target[i] ^ b[i]) & 0xFF;
            if (xa != xb) {
                return Integer.compare(xa, xb);
            }
        }
        return 0;
    }

    /**
     * Stable key for a peer: first 20 bytes of Ed25519 pub (or whole if shorter).
     */
    public static byte[] peerKeyspaceId(byte[] peerPub) {
        if (peerPub == null || peerPub.length == 0) {
            throw new IllegalArgumentException("peerPub empty");
        }
        int n = Math.min(20, peerPub.length);
        byte[] out = new byte[20];
        System.arraycopy(peerPub, 0, out, 0, n);
        return out;
    }

    /**
     * Return a new list sorted by ascending XOR distance to the keyword
     * keyspace target. Ties preserve relative order of the input (stable).
     */
    public static List<PeerDirectory.PeerInfo> rankByKeyspace(
            String keywords, List<PeerDirectory.PeerInfo> peers) {
        if (peers == null || peers.isEmpty()) {
            return peers == null ? List.of() : peers;
        }
        byte[] target = keyspaceTarget(keywords);
        List<PeerDirectory.PeerInfo> ranked = new ArrayList<>(peers);
        ranked.sort(Comparator.comparing(
                p -> peerKeyspaceId(p.peerPub()),
                (a, b) -> compareXorDistance(target, a, b)));
        return ranked;
    }
}
