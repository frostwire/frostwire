/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

/**
 * Payload of a {@link MeshProtocolId#NODE_META} announcement: a versioned, fixed-size encoding of
 * the sender's {@link com.frostwire.search.relay.NodeCapabilities} bitmask.
 *
 * <p>Layout (9 bytes): {@code [version:1][capabilities:8 big-endian long]}. The fixed small size
 * keeps the announcement inside a single non-fragmented rUDP frame and makes malformed frames
 * trivially rejectable; older peers ignore the unknown protocol id entirely.
 */
public final class NodeMetaPayload {

    public static final int VERSION = 1;
    public static final int LENGTH = 9;

    private NodeMetaPayload() {
    }

    public static byte[] encode(long capabilities) {
        byte[] out = new byte[LENGTH];
        out[0] = (byte) VERSION;
        for (int i = 0; i < 8; i++) {
            out[1 + i] = (byte) (capabilities >>> (8 * (7 - i)));
        }
        return out;
    }

    /**
     * Decode a NODE_META payload.
     *
     * @return the advertised capabilities, or {@code -1} when the frame is not a valid
     *     announcement of the current version (capabilities are a non-negative bitmask).
     */
    public static long decode(byte[] payload) {
        if (payload == null || payload.length != LENGTH || payload[0] != (byte) VERSION) {
            return -1L;
        }
        long capabilities = 0L;
        for (int i = 0; i < 8; i++) {
            capabilities = (capabilities << 8) | (payload[1 + i] & 0xFFL);
        }
        return capabilities;
    }
}
