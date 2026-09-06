/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Hex;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable response message answering a {@link RemoteSearchRequest}.
 * Carries the request's {@code nonce} (correlation) and a list of
 * result rows, plus a signature by the responder.
 *
 * <p>Each row is a bencoded dict:
 * <pre>
 * {
 *   ih:   hex info hash v1 (20 bytes)
 *   n:    name (UTF-8)
 *   s:    size in bytes
 *   fc:   file count
 *   pub:  base64url raw Ed25519 pub of the publisher (32 bytes)
 *   nid:  hex SHA-1 node id of the publisher (20 bytes, optional)
 *   bt:   comma-separated host:port BT listen endpoints of the responder
 *         (optional, v3+; signed inside the row domain so mesh hops
 *         cannot rewrite where the requester fetches the torrent from)
 * }
 * </pre>
 *
 * <p>The signature covers a canonical byte form: {@code v|nonce|ts|rows_hash}
 * where {@code rows_hash} is SHA-256 of the bencoded rows. This
 * lets a single signature attest to the whole result set without
 * having to sign each row individually.
 */
public final class RemoteSearchResponse {

    /** Wire version 2: signature domain always includes chunk + final flags. */
    public static final int VERSION_2 = 2;
    /** Wire version 3: rows may carry optional {@code bt} seeder endpoints. */
    public static final int VERSION = 3;

    /**
     * Default max rows per streamed RESULT frame. Full sets larger than this
     * are split into multiple signed chunks ending with {@code final=true}.
     */
    public static final int DEFAULT_STREAM_CHUNK_SIZE = 5;
    public static final int MAX_STREAM_CHUNKS = 128;
    public static final int MAX_STREAM_BYTES = 256 * 1024;

    private final int version;
    private final byte[] nonce;
    private final long timestamp;
    private final List<Row> rows;
    private final byte[] signature;
    /** Chunk index within a streamed response (0 for a single-frame reply). */
    private final int chunkIndex;
    /** True when this is the last frame for the nonce. */
    private final boolean finalChunk;

    private RemoteSearchResponse(int version, byte[] nonce, long timestamp,
                                 List<Row> rows, byte[] signature,
                                 int chunkIndex, boolean finalChunk) {
        this.version = version;
        this.nonce = nonce.clone();
        this.timestamp = timestamp;
        this.rows = new ArrayList<>(rows.size());
        for (Row r : rows) {
            this.rows.add(new Row(r.infoHash.clone(), r.name, r.sizeBytes,
                    r.fileCount, r.publisherEd25519Pub.clone(),
                    r.publisherNodeId == null ? null : r.publisherNodeId.clone(),
                    r.matchedFile, r.seederEndpoints));
        }
        this.signature = signature.clone();
        this.chunkIndex = chunkIndex;
        this.finalChunk = finalChunk;
    }

    public int version() {
        return version;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public long timestamp() {
        return timestamp;
    }

    public List<Row> rows() {
        List<Row> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            out.add(new Row(r.infoHash.clone(), r.name, r.sizeBytes,
                    r.fileCount, r.publisherEd25519Pub.clone(),
                    r.publisherNodeId == null ? null : r.publisherNodeId.clone(),
                    r.matchedFile, r.seederEndpoints));
        }
        return out;
    }

    public byte[] signature() {
        return signature.clone();
    }

    public int chunkIndex() {
        return chunkIndex;
    }

    public boolean isFinalChunk() {
        return finalChunk;
    }

    public byte[] canonicalBytes() {
        try {
            byte[] rowsHash = sha256(rowsHashBytes());
            // v|nonce|ts|rows_hash|chunk|final — chunk/final are part of the
            // signature domain so stream frames cannot be reordered or forged.
            ByteBuffer buf = ByteBuffer.allocate(4 + 4 + nonce.length + 8 + 32 + 4 + 1);
            buf.putInt(version);
            buf.putInt(nonce.length);
            buf.put(nonce);
            buf.putLong(timestamp);
            buf.put(rowsHash);
            buf.putInt(chunkIndex);
            buf.put((byte) (finalChunk ? 1 : 0));
            return buf.array();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private byte[] rowsHashBytes() {
        // Concatenate the bencoded rows in a deterministic order
        // (the row list is already in insertion order).
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (Row r : rows) {
            appendRowBencode(out, r);
        }
        return out.toByteArray();
    }

    private static void appendRowBencode(java.io.ByteArrayOutputStream out, Row r) {
        // Bencode a dict with sorted keys: bt?, fc, ih, mf?, n, nid?, pub, s
        java.util.Map<String, byte[]> parts = new java.util.TreeMap<>();
        if (!r.seederEndpoints.isEmpty()) {
            parts.put("bt", String.join(",", r.seederEndpoints)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        parts.put("fc", Long.toString(r.fileCount).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        parts.put("ih", Hex.encode(r.infoHash).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (r.matchedFile != null) {
            parts.put("mf", r.matchedFile.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        parts.put("n", r.name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (r.publisherNodeId != null) {
            parts.put("nid", Hex.encode(r.publisherNodeId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        parts.put("pub", Base64.getEncoder().withoutPadding()
                .encodeToString(r.publisherEd25519Pub).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        parts.put("s", Long.toString(r.sizeBytes).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            out.write("d".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> e : parts.entrySet()) {
                out.write(encodeBencodeString(e.getKey()));
                out.write(encodeBencodeString(new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8)));
            }
            out.write("e".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("ByteArrayOutputStream should not throw", ex);
        }
    }

    private static byte[] encodeBencodeString(String s) {
        byte[] data = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return (data.length + ":" + new String(data, java.nio.charset.StandardCharsets.UTF_8))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return java.security.MessageDigest.getInstance("SHA-256").digest(data);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "RemoteSearchResponse{v=" + version + ", rows=" + rows.size() +
                ", ts=" + timestamp + "}";
    }

    /**
     * Convert to a bencodeable map for transport. Used by the wire
     * codec to serialize the response.
     */
    public Map<String, Object> toBencodeableMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", version);
        m.put("nonce", Base64.getEncoder().withoutPadding().encodeToString(nonce));
        m.put("ts", timestamp);
        List<Map<String, Object>> rowMaps = new ArrayList<>(rows.size());
        for (Row r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ih", Hex.encode(r.infoHash));
            row.put("n", r.name);
            row.put("s", r.sizeBytes);
            row.put("fc", (long) r.fileCount);
            row.put("pub", Base64.getEncoder().withoutPadding()
                    .encodeToString(r.publisherEd25519Pub));
            if (r.publisherNodeId != null) {
                row.put("nid", Hex.encode(r.publisherNodeId));
            }
            if (r.matchedFile != null) {
                row.put("mf", r.matchedFile);
            }
            if (!r.seederEndpoints.isEmpty()) {
                row.put("bt", new ArrayList<>(r.seederEndpoints));
            }
            rowMaps.add(row);
        }
        m.put("rows", rowMaps);
        m.put("chunk", chunkIndex);
        m.put("final", finalChunk);
        m.put("sig", Base64.getEncoder().withoutPadding()
                .encodeToString(signature));
        return m;
    }

    /**
     * Reconstruct a response from a bencodeable map (the inverse of
     * {@link #toBencodeableMap()}). Returns null if the map is null
     * or missing required fields.
     */
    @SuppressWarnings("unchecked")
    public static RemoteSearchResponse fromBencodeableMap(Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        try {
            Object vObj = m.get("v");
            Object nonceObj = m.get("nonce");
            Object tsObj = m.get("ts");
            Object rowsObj = m.get("rows");
            Object sigObj = m.get("sig");
            if (vObj == null || nonceObj == null || tsObj == null
                    || sigObj == null || !(rowsObj instanceof List)
                    || ((String) nonceObj).length() > 88 || ((String) sigObj).length() > 88) {
                return null;
            }
            byte[] nonce = Base64.getDecoder().decode((String) nonceObj);
            byte[] sig = Base64.getDecoder().decode((String) sigObj);
            int chunk = 0;
            Object chunkObj = m.get("chunk");
            if (chunkObj instanceof Number) {
                if (((Number) chunkObj).longValue() < 0
                        || ((Number) chunkObj).longValue() >= MAX_STREAM_CHUNKS
                        || ((Number) chunkObj).doubleValue() != ((Number) chunkObj).longValue()) {
                    return null;
                }
                chunk = ((Number) chunkObj).intValue();
            } else if (chunkObj != null) {
                return null;
            }
            boolean isFinal = true;
            Object finalObj = m.get("final");
            if (finalObj instanceof Boolean) {
                isFinal = (Boolean) finalObj;
            } else if (finalObj instanceof Number) {
                if (((Number) finalObj).doubleValue() != 0 && ((Number) finalObj).doubleValue() != 1) {
                    return null;
                }
                isFinal = ((Number) finalObj).intValue() != 0;
            } else if (finalObj != null) {
                return null;
            }
            RemoteSearchResponse.Builder b = RemoteSearchResponse.builder()
                    .version(((Number) vObj).intValue())
                    .nonce(nonce)
                    .timestamp(((Number) tsObj).longValue())
                    .chunkIndex(chunk)
                    .finalChunk(isFinal)
                    .signature(sig);
            if (rowsObj instanceof List) {
                List<?> rlist = (List<?>) rowsObj;
                if (rlist.size() > RemoteSearchRequest.MAX_LIMIT) {
                    return null;
                }
                for (Object ro : rlist) {
                    if (!(ro instanceof Map)) {
                        return null;
                    }
                    Map<String, Object> row = (Map<String, Object>) ro;
                    Object ihObj = row.get("ih");
                    Object nObj = row.get("n");
                    Object sObj = row.get("s");
                    Object fcObj = row.get("fc");
                    Object pubObj = row.get("pub");
                    if (ihObj == null || nObj == null || sObj == null
                            || fcObj == null || pubObj == null) {
                        return null;
                    }
                    if (((String) ihObj).length() != 40 || ((String) pubObj).length() > 44) {
                        return null;
                    }
                    byte[] ih = Hex.decode((String) ihObj);
                    byte[] pub = Base64.getDecoder().decode((String) pubObj);
                    byte[] nid = null;
                    Object nidObj = row.get("nid");
                    if (nidObj != null) {
                        if (((String) nidObj).length() != 40) {
                            return null;
                        }
                        nid = Hex.decode((String) nidObj);
                    }
                    String matchedFile = null;
                    Object mfObj = row.get("mf");
                    if (mfObj != null) {
                        matchedFile = (String) mfObj;
                    }
                    List<String> seederEndpoints = new ArrayList<>();
                    Object btObj = row.get("bt");
                    if (btObj instanceof List) {
                        for (Object ep : (List<?>) btObj) {
                            if (ep instanceof String
                                    && !((String) ep).isEmpty()
                                    && ((String) ep).length() <= 256
                                    && seederEndpoints.size() < Row.MAX_SEEDER_ENDPOINTS) {
                                seederEndpoints.add((String) ep);
                            }
                        }
                    }
                    b.addRow(ih, (String) nObj, ((Number) sObj).longValue(),
                            ((Number) fcObj).intValue(), pub, nid, matchedFile,
                            seederEndpoints);
                }
            }
            return b.build();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Immutable search result row. */
    public static final class Row {
        /** Max seeder endpoints carried per row (anti-bloat on untrusted wire data). */
        public static final int MAX_SEEDER_ENDPOINTS = 8;

        public final byte[] infoHash;
        public final String name;
        public final long sizeBytes;
        public final int fileCount;
        public final byte[] publisherEd25519Pub;
        public final byte[] publisherNodeId; // nullable
        /** File path within the torrent that matched the search, or null if matched on name. */
        public final String matchedFile;
        /** Responder BT listen endpoints (host:port, v3+; empty when none advertised). */
        public final List<String> seederEndpoints;

        public Row(byte[] infoHash, String name, long sizeBytes, int fileCount,
                  byte[] publisherEd25519Pub, byte[] publisherNodeId) {
            this(infoHash, name, sizeBytes, fileCount, publisherEd25519Pub, publisherNodeId, null);
        }

        public Row(byte[] infoHash, String name, long sizeBytes, int fileCount,
                  byte[] publisherEd25519Pub, byte[] publisherNodeId, String matchedFile) {
            this(infoHash, name, sizeBytes, fileCount, publisherEd25519Pub, publisherNodeId,
                    matchedFile, null);
        }

        public Row(byte[] infoHash, String name, long sizeBytes, int fileCount,
                  byte[] publisherEd25519Pub, byte[] publisherNodeId, String matchedFile,
                  List<String> seederEndpoints) {
            if (infoHash == null || infoHash.length != 20) {
                throw new IllegalArgumentException("infoHash must be 20 bytes");
            }
            if (name == null || name.length() > 2048
                    || matchedFile != null && matchedFile.length() > 8192) {
                throw new IllegalArgumentException("row text exceeds bounds");
            }
            if (publisherEd25519Pub == null || publisherEd25519Pub.length != 32) {
                throw new IllegalArgumentException("publisherEd25519Pub must be 32 bytes");
            }
            this.infoHash = infoHash.clone();
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.fileCount = fileCount;
            this.publisherEd25519Pub = publisherEd25519Pub.clone();
            if (publisherNodeId != null && publisherNodeId.length != 20) {
                throw new IllegalArgumentException("publisherNodeId must be 20 bytes");
            }
            this.publisherNodeId = publisherNodeId == null ? null : publisherNodeId.clone();
            this.matchedFile = matchedFile;
            if (seederEndpoints == null || seederEndpoints.isEmpty()) {
                this.seederEndpoints = java.util.Collections.emptyList();
            } else {
                List<String> copy = new ArrayList<>(seederEndpoints.size());
                for (String ep : seederEndpoints) {
                    if (ep != null && !ep.isEmpty() && ep.length() <= 256
                            && copy.size() < MAX_SEEDER_ENDPOINTS) {
                        copy.add(ep);
                    }
                }
                this.seederEndpoints = java.util.Collections.unmodifiableList(copy);
            }
        }
    }

    /**
     * Bounded single-holder stream. Callers authenticate and correlate before add.
     * An identical duplicate is harmless; conflicts permanently invalidate the
     * stream. Results become available only after every index through final.
     */
    public static final class Collector {
        private final int maxRows;
        private final int maxBytes;
        private final Map<Integer, RemoteSearchResponse> chunks = new LinkedHashMap<>();
        private int finalIndex = -1;
        private int rowCount;
        private int byteCount;
        private byte[] nonce;
        private boolean failed;

        public Collector(int maxRows, int maxBytes) {
            if (maxRows <= 0 || maxRows > RemoteSearchRequest.MAX_LIMIT
                    || maxBytes <= 0 || maxBytes > MAX_STREAM_BYTES) {
                throw new IllegalArgumentException("invalid stream budget");
            }
            this.maxRows = maxRows;
            this.maxBytes = maxBytes;
        }

        public synchronized boolean add(RemoteSearchResponse response, int encodedBytes) {
            if (failed || response == null) {
                return false;
            }
            int index = response.chunkIndex;
            RemoteSearchResponse prior = chunks.get(index);
            if (prior != null) {
                if (Arrays.equals(prior.canonicalBytes(), response.canonicalBytes())) {
                    return true;
                }
                failed = true;
            } else if (index < 0 || index >= MAX_STREAM_CHUNKS
                    || encodedBytes <= 0 || encodedBytes > maxBytes - byteCount
                    || response.rows.size() > maxRows - rowCount
                    || nonce != null && !Arrays.equals(nonce, response.nonce)
                    || finalIndex >= 0 && (index > finalIndex || response.finalChunk && index != finalIndex)
                    || response.finalChunk && chunks.keySet().stream().anyMatch(i -> i > index)) {
                failed = true;
            } else {
                nonce = response.nonce;
                chunks.put(index, response);
                byteCount += encodedBytes;
                rowCount += response.rows.size();
                if (response.finalChunk) {
                    finalIndex = index;
                }
                return true;
            }
            chunks.clear();
            return false;
        }

        public synchronized boolean isComplete() {
            return !failed && finalIndex >= 0 && chunks.size() == finalIndex + 1;
        }

        public synchronized boolean isFailed() {
            return failed;
        }

        public synchronized int byteCount() {
            return byteCount;
        }

        public synchronized int rowCount() {
            return rowCount;
        }

        public synchronized List<RemoteSearchResponse> responses() {
            if (!isComplete()) {
                return Collections.emptyList();
            }
            List<RemoteSearchResponse> ordered = new ArrayList<>(chunks.size());
            for (int i = 0; i <= finalIndex; i++) {
                ordered.add(chunks.get(i));
            }
            return ordered;
        }
    }

    /** Builder for {@link RemoteSearchResponse}. */
    public static final class Builder {
        private int version = VERSION;
        private byte[] nonce;
        private long timestamp;
        private List<Row> rows = new ArrayList<>();
        private byte[] signature;
        private int chunkIndex = 0;
        private boolean finalChunk = true;

        public Builder nonce(byte[] nonce) {
            this.nonce = nonce;
            return this;
        }

        /**
         * Carry the transmitted wire version when decoding. Responses are
         * re-canonicalized for signature verification, so a parsed response
         * must keep its original version or older responses fail verification
         * after a version bump.
         */
        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder chunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
            return this;
        }

        public Builder finalChunk(boolean finalChunk) {
            this.finalChunk = finalChunk;
            return this;
        }

        public Builder addRow(byte[] infoHash, String name, long sizeBytes, int fileCount,
                              byte[] publisherEd25519Pub) {
            return addRow(infoHash, name, sizeBytes, fileCount, publisherEd25519Pub, null);
        }

        public Builder addRow(byte[] infoHash, String name, long sizeBytes, int fileCount,
                              byte[] publisherEd25519Pub, byte[] publisherNodeId) {
            rows.add(new Row(infoHash, name, sizeBytes, fileCount,
                    publisherEd25519Pub, publisherNodeId, null));
            return this;
        }

        public Builder addRow(byte[] infoHash, String name, long sizeBytes, int fileCount,
                              byte[] publisherEd25519Pub, byte[] publisherNodeId, String matchedFile) {
            rows.add(new Row(infoHash, name, sizeBytes, fileCount,
                    publisherEd25519Pub, publisherNodeId, matchedFile));
            return this;
        }

        public Builder addRow(byte[] infoHash, String name, long sizeBytes, int fileCount,
                              byte[] publisherEd25519Pub, byte[] publisherNodeId, String matchedFile,
                              List<String> seederEndpoints) {
            rows.add(new Row(infoHash, name, sizeBytes, fileCount,
                    publisherEd25519Pub, publisherNodeId, matchedFile, seederEndpoints));
            return this;
        }

        public Builder signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        public RemoteSearchResponse build() {
            if (chunkIndex < 0 || chunkIndex >= MAX_STREAM_CHUNKS
                    || rows.size() > RemoteSearchRequest.MAX_LIMIT) {
                throw new IllegalStateException("response exceeds stream bounds");
            }
            if (nonce == null || nonce.length == 0 || nonce.length > 64) {
                throw new IllegalStateException("nonce is required");
            }
            if (signature == null || signature.length != 64) {
                throw new IllegalStateException("signature must be 64 bytes");
            }
            return new RemoteSearchResponse(version, nonce, timestamp, rows, signature,
                    chunkIndex, finalChunk);
        }
    }
}
