/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import java.util.Arrays;

/**
 * Application payload carried by rUDP {@link RudpPacket.Type#RELAY} packets
 * for multi-hop mesh delivery between IceBridge nodes.
 *
 * <pre>
 *   sourcePub (32) | targetPub (32) | hopTtl (1) | appPayload...
 * </pre>
 *
 * <p>{@code sourcePub} must match the authenticated rUDP session of the
 * immediate sender (anti-spoof). {@code hopTtl} is decremented at each
 * intermediate forwarder when the target is not in the local registry.
 */
public final class RelayFrame {

    public static final int HEADER_LENGTH = 32 + 32 + 1;
    /** Default hop budget for mesh multi-hop delivery (kept small vs amplification). */
    public static final int DEFAULT_HOP_TTL = 3;
    /**
     * Max application payload inside a RELAY frame so the whole frame fits a
     * single rUDP datagram without fragmentation (header + app <= 1024).
     */
    public static final int MAX_APP_PAYLOAD =
            RudpPacket.MAX_FRAGMENT_PAYLOAD - HEADER_LENGTH;

    private final byte[] sourcePub;
    private final byte[] targetPub;
    private final int hopTtl;
    private final byte[] appPayload;

    private RelayFrame(byte[] sourcePub, byte[] targetPub, int hopTtl, byte[] appPayload) {
        this.sourcePub = sourcePub;
        this.targetPub = targetPub;
        this.hopTtl = hopTtl;
        this.appPayload = appPayload;
    }

    public byte[] sourcePub() {
        return sourcePub.clone();
    }

    public byte[] targetPub() {
        return targetPub.clone();
    }

    public int hopTtl() {
        return hopTtl;
    }

    public byte[] appPayload() {
        return appPayload.clone();
    }

    public static byte[] encode(byte[] sourcePub, byte[] targetPub, int hopTtl, byte[] appPayload) {
        if (sourcePub == null || sourcePub.length != 32) {
            throw new IllegalArgumentException("sourcePub must be 32 bytes");
        }
        if (targetPub == null || targetPub.length != 32) {
            throw new IllegalArgumentException("targetPub must be 32 bytes");
        }
        if (appPayload == null || appPayload.length == 0) {
            throw new IllegalArgumentException("appPayload must be non-empty");
        }
        if (appPayload.length > MAX_APP_PAYLOAD) {
            throw new IllegalArgumentException(
                    "appPayload exceeds RELAY max " + MAX_APP_PAYLOAD + " bytes");
        }
        int ttl = Math.max(0, Math.min(255, hopTtl));
        byte[] out = new byte[HEADER_LENGTH + appPayload.length];
        System.arraycopy(sourcePub, 0, out, 0, 32);
        System.arraycopy(targetPub, 0, out, 32, 32);
        out[64] = (byte) ttl;
        System.arraycopy(appPayload, 0, out, HEADER_LENGTH, appPayload.length);
        return out;
    }

    public static RelayFrame decode(byte[] wire) {
        if (wire == null || wire.length <= HEADER_LENGTH) {
            throw new IllegalArgumentException("relay frame too short");
        }
        if (wire.length - HEADER_LENGTH > MAX_APP_PAYLOAD) {
            throw new IllegalArgumentException("relay app payload too large");
        }
        byte[] sourcePub = Arrays.copyOfRange(wire, 0, 32);
        byte[] targetPub = Arrays.copyOfRange(wire, 32, 64);
        int hopTtl = wire[64] & 0xFF;
        byte[] app = Arrays.copyOfRange(wire, HEADER_LENGTH, wire.length);
        return new RelayFrame(sourcePub, targetPub, hopTtl, app);
    }
}
