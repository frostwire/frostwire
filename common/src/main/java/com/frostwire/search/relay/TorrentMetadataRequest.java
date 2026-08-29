/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable request asking the holder of a torrent to return its metadata
 * (full .torrent bytes) over the IceBridge mesh (Protocol #3 METADATA).
 *
 * <p>Targeted, single-hop: the requester already knows the holder's Ed25519
 * pub from the search row ({@code publisherEd25519Pub}) and sends directly
 * to it; forwarders relay opaque bytes without re-signing.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code v}: protocol version</li>
 *   <li>{@code ih}: hex info hash v1 (20 bytes) of the wanted torrent</li>
 *   <li>{@code nonce}: 32 random bytes, anti-replay + response correlation</li>
 *   <li>{@code pub}: 32-byte raw Ed25519 public key of the requester</li>
 *   <li>{@code ts}: epoch seconds, anti-replay window</li>
 *   <li>{@code sig}: Ed25519 signature over {@link #canonicalBytes()}</li>
 * </ul>
 */
public final class TorrentMetadataRequest {
    public static final int VERSION = 1;
    public static final long MAX_TIMESTAMP_SKEW_SEC = 5 * 60;

    private final int version;
    private final byte[] infoHash;
    private final byte[] nonce;
    private final byte[] requesterPub;
    private final long timestamp;
    private final byte[] signature;

    private TorrentMetadataRequest(int version, byte[] infoHash, byte[] nonce,
                                   byte[] requesterPub, long timestamp, byte[] signature) {
        this.version = version;
        this.infoHash = infoHash.clone();
        this.nonce = nonce.clone();
        this.requesterPub = requesterPub.clone();
        this.timestamp = timestamp;
        this.signature = signature.clone();
    }

    public int version() {
        return version;
    }

    public byte[] infoHash() {
        return infoHash.clone();
    }

    public String infoHashHex() {
        return Hex.encode(infoHash);
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] requesterPub() {
        return requesterPub.clone();
    }

    public long timestamp() {
        return timestamp;
    }

    public byte[] signature() {
        return signature.clone();
    }

    public byte[] canonicalBytes() {
        ByteBuffer buf = ByteBuffer.allocate(
                4                          // version
                        + 4 + infoHash.length
                        + 4 + nonce.length
                        + 4 + requesterPub.length
                        + 8);              // timestamp
        buf.putInt(version);
        buf.putInt(infoHash.length);
        buf.put(infoHash);
        buf.putInt(nonce.length);
        buf.put(nonce);
        buf.putInt(requesterPub.length);
        buf.put(requesterPub);
        buf.putLong(timestamp);
        return buf.array();
    }

    /**
     * Verify the requester's Ed25519 signature over {@link #canonicalBytes()}.
     */
    public boolean verifySignature() {
        try {
            byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
            byte[] encoded = new byte[prefix.length + requesterPub.length];
            System.arraycopy(prefix, 0, encoded, 0, prefix.length);
            System.arraycopy(requesterPub, 0, encoded, prefix.length, requesterPub.length);
            PublicKey pub = IdentityKeys.softwareKeyFactory("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encoded));
            Signature verifier = IdentityKeys.softwareSignature("Ed25519");
            verifier.initVerify(pub);
            verifier.update(canonicalBytes());
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> toBencodeableMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", version);
        m.put("ih", Hex.encode(infoHash));
        m.put("nonce", Base64.getEncoder().withoutPadding().encodeToString(nonce));
        m.put("pub", Base64.getEncoder().withoutPadding().encodeToString(requesterPub));
        m.put("ts", timestamp);
        m.put("sig", Base64.getEncoder().withoutPadding().encodeToString(signature));
        return m;
    }

    public static TorrentMetadataRequest fromBencodeableMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        try {
            Object vObj = m.get("v");
            Object ihObj = m.get("ih");
            Object nonceObj = m.get("nonce");
            Object pubObj = m.get("pub");
            Object tsObj = m.get("ts");
            Object sigObj = m.get("sig");
            if (vObj == null || ihObj == null || nonceObj == null
                    || pubObj == null || tsObj == null || sigObj == null) {
                return null;
            }
            return TorrentMetadataRequest.builder()
                    .version(((Number) vObj).intValue())
                    .infoHash(Hex.decode((String) ihObj))
                    .nonce(Base64.getDecoder().decode((String) nonceObj))
                    .requesterPub(Base64.getDecoder().decode((String) pubObj))
                    .timestamp(((Number) tsObj).longValue())
                    .signature(Base64.getDecoder().decode((String) sigObj))
                    .build();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "TorrentMetadataRequest{v=" + version + ", ih=" + infoHashHex()
                + ", pub=" + Hex.encode(requesterPub).substring(0, 8) + "}";
    }

    public static final class Builder {
        private int version = VERSION;
        private byte[] infoHash;
        private byte[] nonce;
        private byte[] requesterPub;
        private long timestamp;
        private byte[] signature;

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder infoHash(byte[] infoHash) {
            this.infoHash = infoHash;
            return this;
        }

        public Builder nonce(byte[] nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder requesterPub(byte[] requesterPub) {
            this.requesterPub = requesterPub;
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

        public TorrentMetadataRequest build() {
            if (infoHash == null || infoHash.length != 20) {
                throw new IllegalStateException("infoHash must be 20 bytes");
            }
            if (nonce == null || nonce.length == 0) {
                throw new IllegalStateException("nonce is required");
            }
            if (requesterPub == null || requesterPub.length != 32) {
                throw new IllegalStateException("requesterPub must be 32 bytes");
            }
            if (signature == null || signature.length != 64) {
                throw new IllegalStateException("signature must be 64 bytes");
            }
            return new TorrentMetadataRequest(version, infoHash, nonce,
                    requesterPub, timestamp, signature);
        }
    }
}