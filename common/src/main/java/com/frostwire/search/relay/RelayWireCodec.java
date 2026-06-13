/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Logger;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire codec for the relay search protocol.
 *
 * <p>Frame format: a 4-byte big-endian unsigned length prefix
 * followed by exactly that many bytes of bencoded payload.
 * Maximum payload size is {@link #MAX_FRAME_BYTES} (1 MB) to
 * prevent a malicious peer from exhausting our memory.
 *
 * <p>Payload for a request: bencoded map from
 * {@link RemoteSearchRequest#toBencodeableMap()}. Payload for a
 * response: bencoded map from
 * {@link RemoteSearchResponse#toBencodeableMap()}.
 *
 * <p>This codec is stateless: it does not own sockets or threads.
 * Use {@link IncomingRelayServer} and {@link OutgoingRelayClient}
 * for the network plumbing.
 */
public final class RelayWireCodec {

    private static final Logger LOG = Logger.getLogger(RelayWireCodec.class);

    /** Maximum single frame size (1 MB). Prevents memory exhaustion. */
    public static final int MAX_FRAME_BYTES = 1024 * 1024;

    /** Length of the frame length prefix. */
    public static final int LENGTH_PREFIX_BYTES = 4;

    private RelayWireCodec() {
    }

    /**
     * Encode a {@link RemoteSearchRequest} as a bencoded map payload.
     * The transport layer is responsible for framing (see
     * {@link #writeFrame}). Returns the payload bytes.
     */
    public static byte[] encodeRequest(RemoteSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
        return bencodeMap(request.toBencodeableMap());
    }

    /**
     * Encode a {@link RemoteSearchResponse} as a bencoded map payload.
     * The transport layer is responsible for framing (see
     * {@link #writeFrame}). Returns the payload bytes.
     */
    public static byte[] encodeResponse(RemoteSearchResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("response is null");
        }
        return bencodeMap(response.toBencodeableMap());
    }

    /**
     * Read a single length-prefixed frame from {@code in}. Returns
     * the unframed payload bytes, or null on EOF with no bytes read.
     *
     * @throws IOException if the stream fails, the frame exceeds
     *         {@link #MAX_FRAME_BYTES}, or the length prefix is
     *         invalid
     */
    public static byte[] readFrame(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("in is null");
        }
        DataInputStream din = (in instanceof DataInputStream)
                ? (DataInputStream) in : new DataInputStream(in);
        int b1 = din.read();
        if (b1 < 0) {
            return null; // clean EOF
        }
        int b2 = din.read();
        int b3 = din.read();
        int b4 = din.read();
        if (b2 < 0 || b3 < 0 || b4 < 0) {
            throw new EOFException("truncated length prefix");
        }
        int length = ((b1 & 0xff) << 24)
                | ((b2 & 0xff) << 16)
                | ((b3 & 0xff) << 8)
                | (b4 & 0xff);
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw new IOException("invalid frame length: " + length);
        }
        byte[] payload = new byte[length];
        din.readFully(payload);
        return payload;
    }

    /**
     * Convenience: read a frame and decode it as a
     * {@link RemoteSearchRequest}. Returns null on any failure
     * (frame error, decode error, missing fields).
     */
    public static RemoteSearchRequest readRequest(InputStream in) throws IOException {
        byte[] payload = readFrame(in);
        if (payload == null) {
            return null;
        }
        return decodeRequest(payload);
    }

    /**
     * Convenience: read a frame and decode it as a
     * {@link RemoteSearchResponse}. Returns null on any failure.
     */
    public static RemoteSearchResponse readResponse(InputStream in) throws IOException {
        byte[] payload = readFrame(in);
        if (payload == null) {
            return null;
        }
        return decodeResponse(payload);
    }

    /**
     * Write a length-prefixed frame containing the encoded
     * request payload to {@code out}. Convenience over
     * {@link #writeFrame} + {@link #encodeRequest}.
     */
    public static void writeRequest(OutputStream out, RemoteSearchRequest request)
            throws IOException {
        writeFrame(out, encodeRequest(request));
    }

    /**
     * Encode a {@link IdentityRecord} as a bencoded payload.
     */
    public static byte[] encodeIdentityRecord(IdentityRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record is null");
        }
        return record.toEntry().bencode();
    }

    /**
     * Decode a {@link IdentityRecord} from a bencoded payload.
     * Returns null on any failure.
     */
    public static IdentityRecord decodeIdentityRecord(byte[] payload) {
        if (payload == null) {
            return null;
        }
        try {
            Entry entry = Entry.bdecode(payload);
            return IdentityRecord.fromEntry(entry);
        } catch (Throwable t) {
            LOG.debug("Failed to decode identity record", t);
            return null;
        }
    }

    /**
     * Write a length-prefixed identity record frame to {@code out}.
     */
    public static void writeIdentityRecord(OutputStream out, IdentityRecord record)
            throws IOException {
        writeFrame(out, encodeIdentityRecord(record));
    }

    /**
     * Read a length-prefixed identity record frame from {@code in}.
     * Returns null on any failure.
     */
    public static IdentityRecord readIdentityRecord(InputStream in) throws IOException {
        byte[] payload = readFrame(in);
        if (payload == null) {
            return null;
        }
        return decodeIdentityRecord(payload);
    }

    /**
     * Write the one-byte identity-request probe to {@code out}.
     * The probe is a 4-byte length of 1 followed by the byte 0x01.
     */
    public static void writeIdentityRequest(OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("out is null");
        }
        DataOutputStream dout = (out instanceof DataOutputStream)
                ? (DataOutputStream) out : new DataOutputStream(out);
        dout.writeInt(1);
        dout.write(0x01);
        dout.flush();
    }

    /**
     * Returns true if the payload is the identity-request probe.
     */
    public static boolean isIdentityRequest(byte[] payload) {
        return payload != null && payload.length == 1 && payload[0] == 0x01;
    }

    /**
     * Write a length-prefixed frame containing the encoded
     * response payload to {@code out}.
     */
    public static void writeResponse(OutputStream out, RemoteSearchResponse response)
            throws IOException {
        writeFrame(out, encodeResponse(response));
    }

    /**
     * Write a length-prefixed frame to {@code out}. The transport
     * layer calls this with the bencoded payload produced by
     * {@link #encodeRequest} or {@link #encodeResponse}.
     */
    public static void writeFrame(OutputStream out, byte[] payload) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("out is null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is null");
        }
        if (payload.length > MAX_FRAME_BYTES) {
            throw new IOException("payload too large: " + payload.length);
        }
        DataOutputStream dout = (out instanceof DataOutputStream)
                ? (DataOutputStream) out : new DataOutputStream(out);
        dout.writeInt(payload.length);
        dout.write(payload);
        dout.flush();
    }

    /**
     * Decode a {@link RemoteSearchRequest} from a bencoded payload.
     * Returns null if the payload is not a valid request.
     */
    public static RemoteSearchRequest decodeRequest(byte[] payload) {
        if (payload == null) {
            return null;
        }
        try {
            Entry entry = Entry.bdecode(payload);
            return entryToRequest(entry);
        } catch (Throwable t) {
            LOG.debug("Failed to decode request", t);
            return null;
        }
    }

    /**
     * Decode a {@link RemoteSearchResponse} from a bencoded payload.
     * Returns null if the payload is not a valid response.
     */
    public static RemoteSearchResponse decodeResponse(byte[] payload) {
        if (payload == null) {
            return null;
        }
        try {
            Entry entry = Entry.bdecode(payload);
            return entryToResponse(entry);
        } catch (Throwable t) {
            LOG.debug("Failed to decode response", t);
            return null;
        }
    }

    /**
     * Bencoded map serialization. The bencoded keys preserve
     * insertion order via the LinkedHashMap input — bencode sorts
     * keys lexicographically by their UTF-8 bytes, so the wire
     * order matches the canonical signing order.
     */
    private static byte[] bencodeMap(Map<String, Object> map) {
        Map<String, Entry> bencodedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            bencodedMap.put(e.getKey(), toEntry(e.getValue()));
        }
        return Entry.fromMap(bencodedMap).bencode();
    }

    private static Entry toEntry(Object value) {
        if (value instanceof String) {
            return new Entry((String) value);
        } else if (value instanceof byte[]) {
            return new Entry(new String((byte[]) value,
                    java.nio.charset.StandardCharsets.UTF_8));
            // Note: in our protocol, byte[] fields are pre-encoded
            // as base64 strings in the toBencodeableMap methods,
            // so this branch is for already-stringified data only.
        } else if (value instanceof Number) {
            return new Entry(((Number) value).longValue());
        } else if (value instanceof Boolean) {
            return new Entry((Boolean) value ? 1L : 0L);
        } else if (value instanceof List) {
            List<Entry> list = new ArrayList<>();
            for (Object item : (List<?>) value) {
                list.add(toEntry(item));
            }
            return Entry.fromList(list);
        } else if (value instanceof Map) {
            Map<String, Entry> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                m.put(e.getKey().toString(), toEntry(e.getValue()));
            }
            return Entry.fromMap(m);
        }
        return new Entry(value == null ? "" : value.toString());
    }

    @SuppressWarnings("unchecked")
    private static RemoteSearchRequest entryToRequest(Entry entry) {
        if (entry == null) {
            return null;
        }
        Map<String, Entry> dict;
        try {
            dict = entry.dictionary();
        } catch (Throwable t) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        // jlibtorrent's EntryMap supports keySet() but not entrySet();
        // iterate via keySet+get to avoid UnsupportedOperationException.
        for (String key : dict.keySet()) {
            map.put(key, entryToObject(dict.get(key)));
        }
        return RemoteSearchRequest.fromBencodeableMap(map);
    }

    @SuppressWarnings("unchecked")
    private static RemoteSearchResponse entryToResponse(Entry entry) {
        if (entry == null) {
            return null;
        }
        Map<String, Entry> dict;
        try {
            dict = entry.dictionary();
        } catch (Throwable t) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : dict.keySet()) {
            map.put(key, entryToObject(dict.get(key)));
        }
        return RemoteSearchResponse.fromBencodeableMap(map);
    }

    @SuppressWarnings("unchecked")
    private static Object entryToObject(Entry e) {
        if (e == null) return null;
        try {
            // Try as a list
            return new ArrayList<Entry>() {{
                for (Entry child : e.list()) add(child);
            }}.stream().map(RelayWireCodec::entryToObject).collect(java.util.stream.Collectors.toList());
        } catch (Throwable notList) {
            try {
                // Try as a dict. jlibtorrent's EntryMap supports keySet()
                // but not entrySet(); iterate via keySet+get.
                Map<String, Entry> d = e.dictionary();
                Map<String, Object> m = new LinkedHashMap<>();
                for (String key : d.keySet()) {
                    m.put(key, entryToObject(d.get(key)));
                }
                return m;
            } catch (Throwable notDict) {
                try {
                    return e.integer();
                } catch (Throwable notInt) {
                    try {
                        return e.string();
                    } catch (Throwable t) {
                        return null;
                    }
                }
            }
        }
    }
}
