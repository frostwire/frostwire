/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable request message sent by a peer asking a target peer to
 * return its full shared torrent catalog (as opposed to a keyword
 * search via {@link RemoteSearchRequest}).
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code v}: protocol version (currently 1)</li>
 *   <li>{@code pub}: 32-byte raw Ed25519 public key of the requester</li>
 *   <li>{@code target}: 32-byte raw Ed25519 public key of the target peer
 *       whose catalog is being browsed</li>
 *   <li>{@code nonce}: 32 random bytes, anti-replay</li>
 *   <li>{@code ts}: epoch seconds, anti-replay window</li>
 *   <li>{@code sig}: Ed25519 signature over the canonical bytes
 *       (all fields except {@code sig} itself)</li>
 * </ul>
 *
 * <p>Canonical bytes: a fixed-order concatenation of
 * {@code v|pub|target|nonce|ts} using length-prefixed encoding
 * (4-byte big-endian length followed by bytes for variable-length
 * fields; 8-byte big-endian for the timestamp).
 *
 * <p>The request is sent directly to the target peer via
 * {@link DistributedSearchTransport#send(byte[], byte[])}.
 */
public final class RemoteCatalogBrowseRequest {

    public static final int VERSION = 1;
    public static final long MAX_TIMESTAMP_SKEW_SEC = 5 * 60;

    private final int version;
    private final byte[] requesterPub;
    private final byte[] targetPub;
    private final byte[] nonce;
    private final long timestamp;
    private final byte[] signature;

    private RemoteCatalogBrowseRequest(int version, byte[] requesterPub,
                                       byte[] targetPub, byte[] nonce,
                                       long timestamp, byte[] signature) {
        this.version = version;
        this.requesterPub = requesterPub.clone();
        this.targetPub = targetPub.clone();
        this.nonce = nonce.clone();
        this.timestamp = timestamp;
        this.signature = signature.clone();
    }

    public int version() {
        return version;
    }

    public byte[] requesterPub() {
        return requesterPub.clone();
    }

    public byte[] targetPub() {
        return targetPub.clone();
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public long timestamp() {
        return timestamp;
    }

    public byte[] signature() {
        return signature.clone();
    }

    /**
     * Build the canonical signing bytes for this request. The signature
     * is over these bytes; the target peer uses them to verify the
     * requester's identity.
     */
    public byte[] canonicalBytes() {
        ByteBuffer buf = ByteBuffer.allocate(
                4                                // version
                        + 4 + requesterPub.length          // requester pub
                        + 4 + targetPub.length             // target pub
                        + 4 + nonce.length                 // nonce
                        + 8                                // timestamp
        );
        buf.putInt(version);
        putLenPrefixed(buf, requesterPub);
        putLenPrefixed(buf, targetPub);
        putLenPrefixed(buf, nonce);
        buf.putLong(timestamp);
        return buf.array();
    }

    private static void putLenPrefixed(ByteBuffer buf, byte[] data) {
        buf.putInt(data.length);
        buf.put(data);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "RemoteCatalogBrowseRequest{v=" + version +
                ", pub=" + Hex.encode(requesterPub).substring(0, 8) +
                ", target=" + Hex.encode(targetPub).substring(0, 8) + '}';
    }

    /**
     * Convert to a bencodeable map for transport. Used by the wire
     * codec to serialize the request as JSON.
     */
    public Map<String, Object> toBencodeableMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", version);
        m.put("pub", Base64.getEncoder().withoutPadding().encodeToString(requesterPub));
        m.put("target", Base64.getEncoder().withoutPadding().encodeToString(targetPub));
        m.put("nonce", Base64.getEncoder().withoutPadding().encodeToString(nonce));
        m.put("ts", timestamp);
        m.put("sig", Base64.getEncoder().withoutPadding().encodeToString(signature));
        return m;
    }

    /**
     * Reconstruct a request from a bencodeable map (the inverse of
     * {@link #toBencodeableMap()}). Returns null if the map is null
     * or missing required fields.
     */
    public static RemoteCatalogBrowseRequest fromBencodeableMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        try {
            Object vObj = m.get("v");
            Object pubObj = m.get("pub");
            Object targetObj = m.get("target");
            Object nonceObj = m.get("nonce");
            Object tsObj = m.get("ts");
            Object sigObj = m.get("sig");
            if (vObj == null || pubObj == null || targetObj == null
                    || nonceObj == null || tsObj == null || sigObj == null) {
                return null;
            }
            byte[] requesterPub = Base64.getDecoder().decode((String) pubObj);
            byte[] targetPub = Base64.getDecoder().decode((String) targetObj);
            byte[] nonce = Base64.getDecoder().decode((String) nonceObj);
            byte[] sig = Base64.getDecoder().decode((String) sigObj);
            return RemoteCatalogBrowseRequest.builder()
                    .requesterPub(requesterPub)
                    .targetPub(targetPub)
                    .nonce(nonce)
                    .timestamp(((Number) tsObj).longValue())
                    .signature(sig)
                    .build();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Builder for {@link RemoteCatalogBrowseRequest}. */
    public static final class Builder {
        private int version = VERSION;
        private byte[] requesterPub;
        private byte[] targetPub;
        private byte[] nonce;
        private long timestamp;
        private byte[] signature;

        public Builder requesterPub(byte[] requesterPub) {
            this.requesterPub = requesterPub;
            return this;
        }

        public Builder targetPub(byte[] targetPub) {
            this.targetPub = targetPub;
            return this;
        }

        public Builder nonce(byte[] nonce) {
            this.nonce = nonce;
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

        public RemoteCatalogBrowseRequest build() {
            if (requesterPub == null || requesterPub.length != 32) {
                throw new IllegalStateException("requesterPub must be 32 bytes");
            }
            if (targetPub == null || targetPub.length != 32) {
                throw new IllegalStateException("targetPub must be 32 bytes");
            }
            if (nonce == null || nonce.length == 0) {
                throw new IllegalStateException("nonce is required");
            }
            if (signature == null || signature.length != 64) {
                throw new IllegalStateException("signature must be 64 bytes");
            }
            return new RemoteCatalogBrowseRequest(version, requesterPub,
                    targetPub, nonce, timestamp, signature);
        }
    }
}
