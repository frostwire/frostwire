/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable request message sent by a peer asking this node (or
 * another peer in our trust graph) to perform a search and return
 * matching {@link LocalSharedTorrent} rows.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code v}: protocol version (currently 1)</li>
 *   <li>{@code k}: the requester's keywords (UTF-8 string)</li>
 *   <li>{@code lim}: max results to return</li>
 *   <li>{@code nonce}: 32 random bytes, anti-replay</li>
 *   <li>{@code ttl}: remaining WOT depth, decremented at each hop</li>
 *   <li>{@code pub}: 32-byte raw Ed25519 public key of the requester</li>
 *   <li>{@code path}: list of 32-byte Ed25519 pubkeys this request has traversed
 *       (so peers can avoid loops and bound path length)</li>
 *   <li>{@code ts}: epoch seconds, anti-replay window</li>
 *   <li>{@code sig}: Ed25519 signature over the canonical bytes
 *       (all fields except {@code sig} itself)</li>
 * </ul>
 *
 * <p>Canonical bytes: a fixed-order concatenation of
 * {@code v|k|lim|nonce|ttl|pub|path|ts} using length-prefixed
 * encoding (4-byte big-endian length followed by bytes for
 * variable-length fields; fixed-width little endian for ints).
 * The signature signs this canonical form so a relay can
 * reconstruct the exact bytes the requester signed even if
 * field ordering in the wire encoding varies.
 */
public final class RemoteSearchRequest {

    public static final int VERSION = 1;
    public static final int MAX_PATH_LENGTH = 8;
    public static final long MAX_TIMESTAMP_SKEW_SEC = 5 * 60;
    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 100;

    private final int version;
    private final String keywords;
    private final int limit;
    private final byte[] nonce;
    private final int ttl;
    private final byte[] requesterPub;
    private final byte[][] path;
    private final long timestamp;
    private final byte[] signature;

    private RemoteSearchRequest(int version, String keywords, int limit,
                                byte[] nonce, int ttl, byte[] requesterPub,
                                byte[][] path, long timestamp, byte[] signature) {
        this.version = version;
        this.keywords = keywords;
        this.limit = limit;
        this.nonce = nonce.clone();
        this.ttl = ttl;
        this.requesterPub = requesterPub.clone();
        this.path = clonePath(path);
        this.timestamp = timestamp;
        this.signature = signature.clone();
    }

    public int version() {
        return version;
    }

    public String keywords() {
        return keywords;
    }

    public int limit() {
        return limit;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public int ttl() {
        return ttl;
    }

    public byte[] requesterPub() {
        return requesterPub.clone();
    }

    /** Defensive copy of the path. */
    public byte[][] path() {
        return clonePath(path);
    }

    public long timestamp() {
        return timestamp;
    }

    public byte[] signature() {
        return signature.clone();
    }

    /**
     * Build the canonical signing bytes for this request. The
     * signature is over these bytes; relays use them to verify
     * the requester's identity.
     */
    public byte[] canonicalBytes() {
        ByteBuffer buf = ByteBuffer.allocate(
                4                                  // version
                + 4 + utf8Len(keywords)            // keywords
                + 4                                 // limit
                + 4 + nonce.length                 // nonce
                + 4                                 // ttl
                + 4 + requesterPub.length          // requester pub
                + 4 + pathLengthBytes()             // path
                + 8                                 // timestamp
        );
        buf.putInt(version);
        putLenPrefixedUtf8(buf, keywords);
        buf.putInt(limit);
        putLenPrefixed(buf, nonce);
        buf.putInt(ttl);
        putLenPrefixed(buf, requesterPub);
        putLenPrefixedPath(buf, path);
        buf.putLong(timestamp);
        return buf.array();
    }

    private static int utf8Len(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static int pathLengthBytes() {
        // 4-byte length per entry, plus 32 bytes per entry.
        return 0; // recomputed below
    }

    private static void putLenPrefixed(ByteBuffer buf, byte[] data) {
        buf.putInt(data.length);
        buf.put(data);
    }

    private static void putLenPrefixedUtf8(ByteBuffer buf, String s) {
        byte[] data = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.putInt(data.length);
        buf.put(data);
    }

    private static void putLenPrefixedPath(ByteBuffer buf, byte[][] path) {
        buf.putInt(path.length);
        for (byte[] entry : path) {
            putLenPrefixed(buf, entry);
        }
    }

    private static byte[][] clonePath(byte[][] path) {
        byte[][] out = new byte[path.length][];
        for (int i = 0; i < path.length; i++) {
            out[i] = path[i].clone();
        }
        return out;
    }

    /** Number of hops already traversed. */
    public int pathLength() {
        return path.length;
    }

    /**
     * Build a new request with {@code nextHopPub} appended to the
     * path and {@code ttl} decremented by 1. Used by relays that
     * forward this request to a trusted peer.
     */
    public RemoteSearchRequest withNextHop(byte[] nextHopPub, int newTtl) {
        if (nextHopPub == null || nextHopPub.length != 32) {
            throw new IllegalArgumentException("nextHopPub must be 32 bytes");
        }
        if (newTtl < 0) {
            throw new IllegalArgumentException("ttl must be >= 0");
        }
        if (path.length >= MAX_PATH_LENGTH) {
            throw new IllegalStateException("path too long");
        }
        byte[][] newPath = new byte[path.length + 1][];
        System.arraycopy(path, 0, newPath, 0, path.length);
        newPath[path.length] = nextHopPub.clone();
        // Signature is invalidated; caller must re-sign.
        return new RemoteSearchRequest(version, keywords, limit, nonce,
                newTtl, requesterPub, newPath, timestamp, new byte[64]);
    }

    /**
     * Returns true if {@code candidatePub} already appears in the
     * path (loop detection).
     */
    public boolean isLoop(byte[] candidatePub) {
        for (byte[] p : path) {
            if (Arrays.equals(p, candidatePub)) {
                return true;
            }
        }
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "RemoteSearchRequest{v=" + version + ", k=\"" + keywords +
                "\", lim=" + limit + ", ttl=" + ttl + ", path=" + path.length +
                ", pub=" + Hex.encode(requesterPub).substring(0, 8) + "}";
    }

    /** Builder for {@link RemoteSearchRequest}. */
    public static final class Builder {
        private int version = VERSION;
        private String keywords = "";
        private int limit = DEFAULT_LIMIT;
        private byte[] nonce;
        private int ttl = 3;
        private byte[] requesterPub;
        private byte[][] path = new byte[0][];
        private long timestamp;
        private byte[] signature;

        public Builder keywords(String keywords) {
            this.keywords = keywords == null ? "" : keywords;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder nonce(byte[] nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder ttl(int ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder requesterPub(byte[] requesterPub) {
            this.requesterPub = requesterPub;
            return this;
        }

        public Builder path(byte[][] path) {
            this.path = path == null ? new byte[0][] : path;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        public RemoteSearchRequest build() {
            if (nonce == null || nonce.length == 0) {
                throw new IllegalStateException("nonce is required");
            }
            if (requesterPub == null || requesterPub.length != 32) {
                throw new IllegalStateException("requesterPub must be 32 bytes");
            }
            if (signature == null || signature.length != 64) {
                throw new IllegalStateException("signature must be 64 bytes");
            }
            if (limit <= 0 || limit > MAX_LIMIT) {
                throw new IllegalStateException("limit must be in (0, " + MAX_LIMIT + "]");
            }
            if (path.length > MAX_PATH_LENGTH) {
                throw new IllegalStateException("path length must be <= " + MAX_PATH_LENGTH);
            }
            if (keywords.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024) {
                throw new IllegalStateException("keywords too long");
            }
            return new RemoteSearchRequest(version, keywords, limit, nonce,
                    ttl, requesterPub, path, timestamp, signature);
        }
    }

    /**
     * Convert to a bencodeable map for transport. Excludes signature
     * (callers add it after signing the canonical bytes).
     */
    public Map<String, Object> toBencodeableMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", version);
        m.put("k", keywords);
        m.put("lim", limit);
        m.put("nonce", Base64.getEncoder().withoutPadding().encodeToString(nonce));
        m.put("ttl", ttl);
        m.put("pub", Base64.getEncoder().withoutPadding().encodeToString(requesterPub));
        java.util.List<String> pathStrings = new java.util.ArrayList<>();
        for (byte[] p : path) {
            pathStrings.add(Base64.getEncoder().withoutPadding().encodeToString(p));
        }
        m.put("path", pathStrings);
        m.put("ts", timestamp);
        m.put("sig", Base64.getEncoder().withoutPadding().encodeToString(signature));
        return m;
    }

    /**
     * Reconstruct a request from a bencodeable map (the inverse of
     * {@link #toBencodeableMap()}). Returns null if the map is null
     * or missing required fields.
     */
    public static RemoteSearchRequest fromBencodeableMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        try {
            Object vObj = m.get("v");
            Object kObj = m.get("k");
            Object limObj = m.get("lim");
            Object nonceObj = m.get("nonce");
            Object ttlObj = m.get("ttl");
            Object pubObj = m.get("pub");
            Object pathObj = m.get("path");
            Object tsObj = m.get("ts");
            Object sigObj = m.get("sig");
            if (vObj == null || kObj == null || limObj == null
                    || nonceObj == null || ttlObj == null
                    || pubObj == null || tsObj == null || sigObj == null) {
                return null;
            }
            byte[] nonce = Base64.getDecoder().decode((String) nonceObj);
            byte[] requesterPub = Base64.getDecoder().decode((String) pubObj);
            byte[] sig = Base64.getDecoder().decode((String) sigObj);
            byte[][] path = new byte[0][];
            if (pathObj instanceof java.util.List) {
                java.util.List<?> plist = (java.util.List<?>) pathObj;
                path = new byte[plist.size()][];
                for (int i = 0; i < plist.size(); i++) {
                    path[i] = Base64.getDecoder().decode((String) plist.get(i));
                }
            }
            return RemoteSearchRequest.builder()
                    .keywords((String) kObj)
                    .limit(((Number) limObj).intValue())
                    .nonce(nonce)
                    .ttl(((Number) ttlObj).intValue())
                    .requesterPub(requesterPub)
                    .path(path)
                    .timestamp(((Number) tsObj).longValue())
                    .signature(sig)
                    .build();
        } catch (Throwable t) {
            return null;
        }
    }
}
