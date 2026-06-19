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
import java.util.Base64;
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
 * }
 * </pre>
 *
 * <p>The signature covers a canonical byte form: {@code v|nonce|ts|rows_hash}
 * where {@code rows_hash} is SHA-256 of the bencoded rows. This
 * lets a single signature attest to the whole result set without
 * having to sign each row individually.
 */
public final class RemoteSearchResponse {

    public static final int VERSION = 1;

    private final int version;
    private final byte[] nonce;
    private final long timestamp;
    private final List<Row> rows;
    private final byte[] signature;

    private RemoteSearchResponse(int version, byte[] nonce, long timestamp,
                                 List<Row> rows, byte[] signature) {
        this.version = version;
        this.nonce = nonce.clone();
        this.timestamp = timestamp;
        this.rows = new ArrayList<>(rows.size());
        for (Row r : rows) {
            this.rows.add(new Row(r.infoHash.clone(), r.name, r.sizeBytes,
                    r.fileCount, r.publisherEd25519Pub.clone(),
                    r.publisherNodeId == null ? null : r.publisherNodeId.clone(),
                    r.matchedFile));
        }
        this.signature = signature.clone();
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
                    r.matchedFile));
        }
        return out;
    }

    public byte[] signature() {
        return signature.clone();
    }

    public byte[] canonicalBytes() {
        try {
            byte[] rowsHash = sha256(rowsHashBytes());
            ByteBuffer buf = ByteBuffer.allocate(4 + 4 + nonce.length + 8 + 32);
            buf.putInt(version);
            buf.putInt(nonce.length);
            buf.put(nonce);
            buf.putLong(timestamp);
            buf.put(rowsHash);
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
        // Bencode a dict with sorted keys: fc, ih, mf?, n, nid?, pub, s
        java.util.Map<String, byte[]> parts = new java.util.TreeMap<>();
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
            rowMaps.add(row);
        }
        m.put("rows", rowMaps);
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
                    || sigObj == null || rowsObj == null) {
                return null;
            }
            byte[] nonce = Base64.getDecoder().decode((String) nonceObj);
            byte[] sig = Base64.getDecoder().decode((String) sigObj);
            RemoteSearchResponse.Builder b = RemoteSearchResponse.builder()
                    .nonce(nonce)
                    .timestamp(((Number) tsObj).longValue())
                    .signature(sig);
            if (rowsObj instanceof List) {
                List<?> rlist = (List<?>) rowsObj;
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
                    byte[] ih = Hex.decode((String) ihObj);
                    byte[] pub = Base64.getDecoder().decode((String) pubObj);
                    byte[] nid = null;
                    Object nidObj = row.get("nid");
                    if (nidObj != null) {
                        nid = Hex.decode((String) nidObj);
                    }
                    String matchedFile = null;
                    Object mfObj = row.get("mf");
                    if (mfObj != null) {
                        matchedFile = (String) mfObj;
                    }
                    b.addRow(ih, (String) nObj, ((Number) sObj).longValue(),
                            ((Number) fcObj).intValue(), pub, nid, matchedFile);
                }
            }
            return b.build();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Immutable search result row. */
    public static final class Row {
        public final byte[] infoHash;
        public final String name;
        public final long sizeBytes;
        public final int fileCount;
        public final byte[] publisherEd25519Pub;
        public final byte[] publisherNodeId; // nullable
        /** File path within the torrent that matched the search, or null if matched on name. */
        public final String matchedFile;

        public Row(byte[] infoHash, String name, long sizeBytes, int fileCount,
                  byte[] publisherEd25519Pub, byte[] publisherNodeId) {
            this(infoHash, name, sizeBytes, fileCount, publisherEd25519Pub, publisherNodeId, null);
        }

        public Row(byte[] infoHash, String name, long sizeBytes, int fileCount,
                  byte[] publisherEd25519Pub, byte[] publisherNodeId, String matchedFile) {
            if (infoHash == null || infoHash.length != 20) {
                throw new IllegalArgumentException("infoHash must be 20 bytes");
            }
            if (name == null) {
                throw new IllegalArgumentException("name is null");
            }
            if (publisherEd25519Pub == null || publisherEd25519Pub.length != 32) {
                throw new IllegalArgumentException("publisherEd25519Pub must be 32 bytes");
            }
            this.infoHash = infoHash.clone();
            this.name = name;
            this.sizeBytes = sizeBytes;
            this.fileCount = fileCount;
            this.publisherEd25519Pub = publisherEd25519Pub.clone();
            this.publisherNodeId = publisherNodeId == null ? null : publisherNodeId.clone();
            this.matchedFile = matchedFile;
        }
    }

    /** Builder for {@link RemoteSearchResponse}. */
    public static final class Builder {
        private int version = VERSION;
        private byte[] nonce;
        private long timestamp;
        private List<Row> rows = new ArrayList<>();
        private byte[] signature;

        public Builder nonce(byte[] nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
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

        public Builder signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        public RemoteSearchResponse build() {
            if (nonce == null || nonce.length == 0) {
                throw new IllegalStateException("nonce is required");
            }
            if (signature == null || signature.length != 64) {
                throw new IllegalStateException("signature must be 64 bytes");
            }
            return new RemoteSearchResponse(version, nonce, timestamp, rows, signature);
        }
    }
}
