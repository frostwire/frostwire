/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Binary framing for multi-protocol mesh payloads.
 *
 * <pre>
 *   magic "IBP1" (4 bytes) | protocolId uint16 BE | payload...
 * </pre>
 *
 * <p>Every mesh application payload is framed. IceBridge never inspects
 * the application payload — only the framing header.
 */
public final class MeshEnvelope {

    public static final byte[] MAGIC = "IBP1".getBytes(StandardCharsets.US_ASCII);
    public static final int HEADER_LENGTH = MAGIC.length + 2;

    private final int protocolId;
    private final byte[] payload;

    private MeshEnvelope(int protocolId, byte[] payload) {
        this.protocolId = protocolId;
        this.payload = payload;
    }

    public int protocolId() {
        return protocolId;
    }

    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Wrap application bytes with an IBP1 header.
     * {@link MeshProtocolId#UNSPECIFIED} is stored as {@link MeshProtocolId#SEARCH}.
     */
    public static byte[] wrap(int protocolId, byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("payload must be non-empty");
        }
        int id = MeshProtocolId.effective(protocolId);
        if (id < 0 || id > 0xFFFF) {
            throw new IllegalArgumentException("protocolId out of u16 range");
        }
        byte[] out = new byte[HEADER_LENGTH + payload.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        out[MAGIC.length] = (byte) ((id >>> 8) & 0xFF);
        out[MAGIC.length + 1] = (byte) (id & 0xFF);
        System.arraycopy(payload, 0, out, HEADER_LENGTH, payload.length);
        return out;
    }

    /**
     * Unwrap mesh bytes. Requires a valid IBP1 header.
     */
    public static MeshEnvelope unwrap(byte[] wire) {
        if (wire == null || wire.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("wire too short for mesh envelope");
        }
        if (!startsWithMagic(wire)) {
            throw new IllegalArgumentException("missing IBP1 mesh envelope magic");
        }
        int id = ((wire[MAGIC.length] & 0xFF) << 8) | (wire[MAGIC.length + 1] & 0xFF);
        byte[] payload = Arrays.copyOfRange(wire, HEADER_LENGTH, wire.length);
        if (payload.length == 0) {
            throw new IllegalArgumentException("empty framed payload");
        }
        return new MeshEnvelope(id, payload);
    }

    /**
     * Encode application bytes for the mesh wire (always IBP1-framed).
     */
    public static byte[] encodeForWire(int protocolId, byte[] payload) {
        return wrap(protocolId, payload);
    }

    private static boolean startsWithMagic(byte[] wire) {
        for (int i = 0; i < MAGIC.length; i++) {
            if (wire[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
