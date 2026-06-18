/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable rUDP packet exchanged between IceBridge servents.
 *
 * <p>The on-wire header is:
 * <pre>
 *   magic(2), version(1), type(1),
 *   connectionId(8), sequence(4), ackThrough(4), payloadLen(2),
 *   payload(n)
 * </pre>
 *
 * <p>For fragmented data ({@link Type#DATA_FRAG} and {@link Type#DATA_END}),
 * the fields are repurposed:
 * <ul>
 *   <li>{@code sequence} — fragment index within the group (0-based)</li>
 *   <li>{@code ackThrough} — fragment group id (random, shared by all
 *       fragments of the same logical payload)</li>
 * </ul>
 */
public final class RudpPacket {

    public static final short MAGIC = (short) 0x4677; // "Fw"
    public static final int VERSION = 1;
    public static final int HEADER_SIZE = 2 + 1 + 1 + 8 + 4 + 4 + 2; // 22

    /**
     * Maximum payload size that fits in a single UDP datagram without IP
     * fragmentation. 1472 = 1500 (MTU) − 20 (IP header) − 8 (UDP header).
     * We use 1024 to leave comfortable headroom and stay well under typical
     * path MTU after tunneling overhead.
     */
    public static final int MAX_FRAGMENT_PAYLOAD = 1024;

    public enum Type {
        HELLO(0x01),
        HELLO_ACK(0x02),
        DATA(0x03),
        DATA_ACK(0x04),
        HOLE_PUNCH(0x05),
        HOLE_PUNCH_RESPONSE(0x06),
        RELAY(0x07),
        RELAY_RESPONSE(0x08),
        DATA_FRAG(0x09),
        DATA_END(0x0A);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Type fromCode(int code) {
            for (Type t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
            return null;
        }
    }

    private final Type type;
    private final long connectionId;
    private final int sequence;
    private final int ackThrough;
    private final byte[] payload;

    public RudpPacket(Type type, long connectionId, int sequence, int ackThrough, byte[] payload) {
        this.type = Objects.requireNonNull(type, "type");
        this.connectionId = connectionId;
        this.sequence = sequence;
        this.ackThrough = ackThrough;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    public Type type() {
        return type;
    }

    public long connectionId() {
        return connectionId;
    }

    public int sequence() {
        return sequence;
    }

    public int ackThrough() {
        return ackThrough;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public int size() {
        return HEADER_SIZE + payload.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RudpPacket)) return false;
        RudpPacket that = (RudpPacket) o;
        return connectionId == that.connectionId
                && sequence == that.sequence
                && ackThrough == that.ackThrough
                && type == that.type
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, connectionId, sequence, ackThrough);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
