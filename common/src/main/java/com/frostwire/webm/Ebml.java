/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.webm;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal EBML (RFC 8794 / WebM) reader/writer: variable-length integers,
 * element headers, and child scanning. No schema validation beyond what the
 * muxer needs; unknown elements are skipped by size.
 */
public final class Ebml {

    private Ebml() {
    }

    /** -1 sentinel for unknown (all-ones) data sizes, which file inputs never carry. */
    public static final long UNKNOWN_SIZE = -1;

    /** Read an EBML vint at an absolute file offset. Returns value + total length. */
    public static long[] readVint(RandomAccessFile raf, long pos) throws IOException {
        raf.seek(pos);
        int b = raf.read();
        if (b < 0) {
            throw new IOException("truncated vint at " + pos);
        }
        int len = 1;
        int mask = 0x80;
        while (len <= 8 && (b & mask) == 0) {
            len++;
            mask >>= 1;
        }
        if (len > 8) {
            throw new IOException("invalid vint at " + pos);
        }
        long value = b & (mask - 1);
        for (int i = 1; i < len; i++) {
            int next = raf.read();
            if (next < 0) {
                throw new IOException("truncated vint at " + pos);
            }
            value = (value << 8) | next;
        }
        if (len == 8 && value == 0x00FFFFFFFFFFFFFFL) {
            return new long[]{UNKNOWN_SIZE, len};
        }
        if (value == (1L << (7 * len)) - 1) {
            return new long[]{UNKNOWN_SIZE, len};
        }
        return new long[]{value, len};
    }

    /** Read an EBML ID at an absolute offset: full value (length marker
     * included, per the EBML spec IDs carry their marker) + total length. */
    public static long[] readId(RandomAccessFile raf, long pos) throws IOException {
        long[] raw = readVint(raf, pos);
        int len = (int) raw[1];
        long id = raw[0] | (1L << (7 * len));
        return new long[]{id, len};
    }

    /** Minimal vint length (with marker bit) holding {@code value} as an ID or size. */
    public static int vintLength(long value) {
        for (int len = 1; len <= 8; len++) {
            if (value < (1L << (7 * len)) - 1) {
                return len;
            }
        }
        throw new IllegalArgumentException("vint value too large: " + value);
    }

    /** Encode a data size (marker bit set, minimal length). */
    public static byte[] encodeSize(long size) {
        int len = vintLength(size);
        byte[] out = new byte[len];
        long withMarker = size | (1L << (7 * len));
        for (int i = len - 1; i >= 0; i--) {
            out[i] = (byte) (withMarker & 0xFF);
            withMarker >>= 8;
        }
        return out;
    }

    /** One parsed element header: raw ID bytes, value, header/payload geometry. */
    public static final class Element {
        public final byte[] idBytes;
        public final long id;
        public final long size;
        public final long headerLength;
        public final long payloadOffset;
        public final long payloadEnd;

        Element(byte[] idBytes, long id, long size, long headerLength,
                long payloadOffset, long payloadEnd) {
            this.idBytes = idBytes;
            this.id = id;
            this.size = size;
            this.headerLength = headerLength;
            this.payloadOffset = payloadOffset;
            this.payloadEnd = payloadEnd;
        }
    }

    /** Read one element header at an absolute offset (size must be known). */
    public static Element readElement(RandomAccessFile raf, long pos) throws IOException {
        long[] id = readId(raf, pos);
        int idLen = (int) id[1];
        raf.seek(pos);
        byte[] idBytes = new byte[idLen];
        raf.readFully(idBytes);
        long[] size = readVint(raf, pos + idLen);
        if (size[0] == UNKNOWN_SIZE) {
            throw new IOException("unknown element size unsupported at offset " + pos);
        }
        long headerLength = idLen + size[1];
        long payloadOffset = pos + headerLength;
        return new Element(idBytes, id[0], size[0], headerLength, payloadOffset,
                payloadOffset + size[0]);
    }

    /** Direct children of a container payload range. */
    public static List<Element> children(RandomAccessFile raf, long start, long end)
            throws IOException {
        List<Element> out = new ArrayList<>();
        long pos = start;
        while (pos + 2 <= end) {
            Element e = readElement(raf, pos);
            if (e.payloadEnd > end || e.payloadEnd < pos) {
                throw new IOException("element overruns parent at offset " + pos);
            }
            out.add(e);
            pos = e.payloadEnd;
        }
        return out;
    }

    public static long readUint(RandomAccessFile raf, Element e) throws IOException {
        raf.seek(e.payloadOffset);
        long v = 0;
        for (long i = 0; i < e.size; i++) {
            int b = raf.read();
            if (b < 0) {
                throw new IOException("truncated uint");
            }
            v = (v << 8) | b;
        }
        return v;
    }

    public static double readFloat(RandomAccessFile raf, Element e) throws IOException {
        raf.seek(e.payloadOffset);
        if (e.size == 4) {
            int bits = raf.readInt();
            return Float.intBitsToFloat(bits);
        } else if (e.size == 8) {
            return raf.readDouble();
        }
        throw new IOException("bad float width " + e.size);
    }

    public static String readString(RandomAccessFile raf, Element e) throws IOException {
        byte[] raw = readBytes(raf, e);
        int end = raw.length;
        while (end > 0 && raw[end - 1] == 0) {
            end--;
        }
        return new String(raw, 0, end, StandardCharsets.UTF_8);
    }

    public static byte[] readBytes(RandomAccessFile raf, Element e) throws IOException {
        if (e.size > Integer.MAX_VALUE) {
            throw new IOException("element too large");
        }
        byte[] raw = new byte[(int) e.size];
        raf.seek(e.payloadOffset);
        raf.readFully(raw);
        return raw;
    }
}
