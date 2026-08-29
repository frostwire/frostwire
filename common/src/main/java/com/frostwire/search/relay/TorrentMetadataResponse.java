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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holder-signed answer to a {@link TorrentMetadataRequest}, carrying the
 * full .torrent bytes (including BEP 52 piece layers) in chunks sized to fit
 * the ~1 KB mesh RELAY frame cap.
 *
 * <p>Every chunk is signed by the holder over a canonical domain that
 * includes {@link #payloadDigest()} — the SHA-256 of the <b>complete</b>
 * payload (the full .torrent bytes, or the error code) — plus this chunk's
 * index and final flag. The requester verifies every chunk under the
 * holder's pub and, after reassembly, checks that the SHA-256 of the
 * concatenated bytes equals the signed digest: forwarders cannot substitute,
 * reorder, or truncate chunks without failing verification.
 *
 * <p>Wire map: {@code {v, nonce, ih, pd, ci, fin, ts, data?, err?, sig}} —
 * {@code data} (base64 .torrent chunk) and {@code err} (error code) are
 * mutually exclusive.
 */
public final class TorrentMetadataResponse {
    public static final int VERSION = 1;

    /** Chunk payload bytes; base64 + JSON envelope must fit the ~1 KB mesh RELAY frame cap. */
    public static final int CHUNK_DATA_BYTES = 512;

    /** Maximum .torrent size we will relay (anti-amplification). */
    public static final long MAX_TORRENT_BYTES = 256L * 1024;

    public static final String ERR_NOT_FOUND = "NOT_FOUND";
    public static final String ERR_TOO_LARGE = "TOO_LARGE";

    private final int version;
    private final byte[] nonce;
    private final byte[] infoHash;
    private final byte[] payloadDigest;
    private final int chunkIndex;
    private final boolean finalChunk;
    private final long timestamp;
    private final byte[] data;
    private final String error;
    private final byte[] signature;

    private TorrentMetadataResponse(int version, byte[] nonce, byte[] infoHash,
                                    byte[] payloadDigest, int chunkIndex, boolean finalChunk,
                                    long timestamp, byte[] data, String error, byte[] signature) {
        this.version = version;
        this.nonce = nonce.clone();
        this.infoHash = infoHash.clone();
        this.payloadDigest = payloadDigest.clone();
        this.chunkIndex = chunkIndex;
        this.finalChunk = finalChunk;
        this.timestamp = timestamp;
        this.data = data == null ? null : data.clone();
        this.error = error;
        this.signature = signature.clone();
    }

    public int version() {
        return version;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] infoHash() {
        return infoHash.clone();
    }

    /** SHA-256 of the complete payload (full .torrent bytes, or error code). */
    public byte[] payloadDigest() {
        return payloadDigest.clone();
    }

    public int chunkIndex() {
        return chunkIndex;
    }

    public boolean isFinalChunk() {
        return finalChunk;
    }

    public long timestamp() {
        return timestamp;
    }

    /** Chunk bytes, or null on error responses. */
    public byte[] data() {
        return data == null ? null : data.clone();
    }

    /** Error code, or null on data responses. */
    public String error() {
        return error;
    }

    public boolean isError() {
        return error != null;
    }

    public byte[] signature() {
        return signature.clone();
    }

    /**
     * Signature domain: {@code v|nonce|ih|pd|ci|fin|ts}.
     */
    public byte[] canonicalBytes() {
        ByteBuffer buf = ByteBuffer.allocate(
                4 + 4 + nonce.length + 4 + infoHash.length + 32 + 4 + 1 + 8);
        buf.putInt(version);
        buf.putInt(nonce.length);
        buf.put(nonce);
        buf.putInt(infoHash.length);
        buf.put(infoHash);
        buf.put(payloadDigest);
        buf.putInt(chunkIndex);
        buf.put((byte) (finalChunk ? 1 : 0));
        buf.putLong(timestamp);
        return buf.array();
    }

    /**
     * Verify this chunk's holder signature under {@code holderPub}
     * (32-byte raw Ed25519).
     */
    public boolean verifySignature(byte[] holderPub) {
        if (holderPub == null || holderPub.length != 32) {
            return false;
        }
        try {
            byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
            byte[] encoded = new byte[prefix.length + holderPub.length];
            System.arraycopy(prefix, 0, encoded, 0, prefix.length);
            System.arraycopy(holderPub, 0, encoded, prefix.length, holderPub.length);
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

    /**
     * Split the full torrent bytes into unsigned chunks ({@code data} set,
     * {@code finalChunk} on the last one), each carrying the SHA-256 of the
     * FULL payload in its signature domain. Callers sign each chunk over its
     * {@link #canonicalBytes()}.
     */
    public static List<TorrentMetadataResponse> buildChunks(
            byte[] nonce, byte[] infoHash, long timestamp, byte[] fullTorrentBytes) {
        if (fullTorrentBytes == null || fullTorrentBytes.length == 0) {
            throw new IllegalArgumentException("fullTorrentBytes is empty");
        }
        byte[] digest = sha256(fullTorrentBytes);
        List<TorrentMetadataResponse> chunks = new ArrayList<>();
        int total = fullTorrentBytes.length;
        int chunkCount = (total + CHUNK_DATA_BYTES - 1) / CHUNK_DATA_BYTES;
        for (int i = 0; i < chunkCount; i++) {
            int from = i * CHUNK_DATA_BYTES;
            int to = Math.min(from + CHUNK_DATA_BYTES, total);
            byte[] chunkData = new byte[to - from];
            System.arraycopy(fullTorrentBytes, from, chunkData, 0, chunkData.length);
            chunks.add(TorrentMetadataResponse.builder()
                    .nonce(nonce)
                    .infoHash(infoHash)
                    .payloadDigest(digest)
                    .timestamp(timestamp)
                    .chunkIndex(i)
                    .finalChunk(i == chunkCount - 1)
                    .data(chunkData)
                    .signature(new byte[64])
                    .build());
        }
        return chunks;
    }

    /**
     * Reassemble chunks (ascending {@code chunkIndex}, ending with the final
     * one) into the full payload. Returns null when chunks are missing,
     * duplicated, out of order, or inconsistent (nonce/infoHash/digest
     * mismatch across the set).
     */
    public static byte[] assemble(List<TorrentMetadataResponse> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        TorrentMetadataResponse last = chunks.get(chunks.size() - 1);
        if (!last.isFinalChunk() || last.isError()) {
            return null;
        }
        int expected = last.chunkIndex() + 1;
        if (chunks.size() != expected) {
            return null;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < chunks.size(); i++) {
            TorrentMetadataResponse c = chunks.get(i);
            if (c.isError() || c.chunkIndex() != i
                    || !Arrays.equals(c.nonce(), last.nonce())
                    || !Arrays.equals(c.infoHash(), last.infoHash())
                    || !Arrays.equals(c.payloadDigest(), last.payloadDigest())) {
                return null;
            }
            byte[] d = c.data();
            out.write(d, 0, d.length);
        }
        byte[] full = out.toByteArray();
        if (!Arrays.equals(sha256(full), last.payloadDigest())) {
            return null;
        }
        return full;
    }

    /**
     * Build an unsigned single-frame error response ({@code err} set,
     * {@code payloadDigest} = SHA-256 of the error code).
     */
    public static TorrentMetadataResponse buildError(
            byte[] nonce, byte[] infoHash, long timestamp, String errorCode) {
        return TorrentMetadataResponse.builder()
                .nonce(nonce)
                .infoHash(infoHash)
                .payloadDigest(sha256(errorCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .timestamp(timestamp)
                .chunkIndex(0)
                .finalChunk(true)
                .error(errorCode)
                .signature(new byte[64])
                .build();
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public Map<String, Object> toBencodeableMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", version);
        m.put("nonce", Base64.getEncoder().withoutPadding().encodeToString(nonce));
        m.put("ih", Hex.encode(infoHash));
        m.put("pd", Base64.getEncoder().withoutPadding().encodeToString(payloadDigest));
        m.put("ci", chunkIndex);
        m.put("fin", finalChunk);
        m.put("ts", timestamp);
        if (error != null) {
            m.put("err", error);
        } else if (data != null) {
            m.put("data", Base64.getEncoder().withoutPadding().encodeToString(data));
        }
        m.put("sig", Base64.getEncoder().withoutPadding().encodeToString(signature));
        return m;
    }

    public static TorrentMetadataResponse fromBencodeableMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        try {
            Object vObj = m.get("v");
            Object nonceObj = m.get("nonce");
            Object ihObj = m.get("ih");
            Object pdObj = m.get("pd");
            Object ciObj = m.get("ci");
            Object finObj = m.get("fin");
            Object tsObj = m.get("ts");
            Object dataObj = m.get("data");
            Object errObj = m.get("err");
            Object sigObj = m.get("sig");
            if (vObj == null || nonceObj == null || ihObj == null || pdObj == null
                    || ciObj == null || finObj == null || tsObj == null || sigObj == null) {
                return null;
            }
            byte[] payloadDigest = Base64.getDecoder().decode((String) pdObj);
            if (payloadDigest.length != 32) {
                return null;
            }
            byte[] data = null;
            if (dataObj instanceof String) {
                data = Base64.getDecoder().decode((String) dataObj);
                if (data.length == 0 || data.length > CHUNK_DATA_BYTES) {
                    return null;
                }
            }
            String error = errObj instanceof String ? (String) errObj : null;
            if ((data == null) == (error == null)) {
                return null; // exactly one of data/err required
            }
            boolean isFinal;
            if (finObj instanceof Boolean) {
                isFinal = (Boolean) finObj;
            } else if (finObj instanceof Number) {
                isFinal = ((Number) finObj).intValue() != 0;
            } else {
                return null;
            }
            return TorrentMetadataResponse.builder()
                    .version(((Number) vObj).intValue())
                    .nonce(Base64.getDecoder().decode((String) nonceObj))
                    .infoHash(Hex.decode((String) ihObj))
                    .payloadDigest(payloadDigest)
                    .chunkIndex(((Number) ciObj).intValue())
                    .finalChunk(isFinal)
                    .timestamp(((Number) tsObj).longValue())
                    .data(data)
                    .error(error)
                    .signature(Base64.getDecoder().decode((String) sigObj))
                    .build();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "TorrentMetadataResponse{v=" + version + ", ih=" + Hex.encode(infoHash)
                + ", ci=" + chunkIndex + ", fin=" + finalChunk
                + (error != null ? ", err=" + error : "")
                + ", data=" + (data == null ? 0 : data.length) + "B}";
    }

    public static final class Builder {
        private int version = VERSION;
        private byte[] nonce;
        private byte[] infoHash;
        private byte[] payloadDigest;
        private int chunkIndex = 0;
        private boolean finalChunk = true;
        private long timestamp;
        private byte[] data;
        private String error;
        private byte[] signature;

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder nonce(byte[] nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder infoHash(byte[] infoHash) {
            this.infoHash = infoHash;
            return this;
        }

        public Builder payloadDigest(byte[] payloadDigest) {
            this.payloadDigest = payloadDigest;
            return this;
        }

        public Builder chunkIndex(int chunkIndex) {
            this.chunkIndex = Math.max(0, chunkIndex);
            return this;
        }

        public Builder finalChunk(boolean finalChunk) {
            this.finalChunk = finalChunk;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        public TorrentMetadataResponse build() {
            if (nonce == null || nonce.length == 0) {
                throw new IllegalStateException("nonce is required");
            }
            if (infoHash == null || infoHash.length != 20) {
                throw new IllegalStateException("infoHash must be 20 bytes");
            }
            if (payloadDigest == null || payloadDigest.length != 32) {
                throw new IllegalStateException("payloadDigest must be 32 bytes");
            }
            if (signature == null || signature.length != 64) {
                throw new IllegalStateException("signature must be 64 bytes");
            }
            if ((data == null) == (error == null)) {
                throw new IllegalStateException("exactly one of data/error is required");
            }
            if (data != null && (data.length == 0 || data.length > CHUNK_DATA_BYTES)) {
                throw new IllegalStateException("chunk data must be in (0, " + CHUNK_DATA_BYTES + "] bytes");
            }
            if (error != null && error.length() > 64) {
                throw new IllegalStateException("error code too long");
            }
            return new TorrentMetadataResponse(version, nonce, infoHash, payloadDigest,
                    chunkIndex, finalChunk, timestamp, data, error, signature);
        }
    }
}