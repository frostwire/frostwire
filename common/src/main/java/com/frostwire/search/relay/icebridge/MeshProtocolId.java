/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

/**
 * Application-protocol identifiers carried by {@link MeshEnvelope}.
 *
 * <p>IceBridge routes opaque bytes and never interprets these IDs beyond
 * demultiplexing. Application handlers above the mesh decide semantics.
 */
public final class MeshProtocolId {

    /** Unspecified — treated as {@link #SEARCH} by demux helpers. */
    public static final int UNSPECIFIED = 0;

    /** FrostWire distributed search (Protocol #1). */
    public static final int SEARCH = 1;

    /** Reserved for peer chat / messaging. */
    public static final int CHAT = 2;

    /** Reserved for torrent / index metadata exchange. */
    public static final int METADATA = 3;

    /** Reserved for pub/sub channels. */
    public static final int PUBSUB = 4;

    /** Reserved for distributed AI / compute tasks. */
    public static final int AI = 5;

    /** Reserved for node telemetry / health. */
    public static final int TELEMETRY = 6;

    /** Reserved for file synchronization. */
    public static final int FILESYNC = 7;

    private MeshProtocolId() {
    }

    /**
     * Normalize for demux: {@link #UNSPECIFIED} maps to {@link #SEARCH}.
     */
    public static int effective(int protocolId) {
        return protocolId == UNSPECIFIED ? SEARCH : protocolId;
    }

    public static boolean isKnown(int protocolId) {
        int id = effective(protocolId);
        return id >= SEARCH && id <= FILESYNC;
    }

    /**
     * Human-readable name for logs / ops (unknown ids as {@code PROTO_n}).
     */
    public static String name(int protocolId) {
        int id = effective(protocolId);
        switch (id) {
            case SEARCH:
                return "SEARCH";
            case CHAT:
                return "CHAT";
            case METADATA:
                return "METADATA";
            case PUBSUB:
                return "PUBSUB";
            case AI:
                return "AI";
            case TELEMETRY:
                return "TELEMETRY";
            case FILESYNC:
                return "FILESYNC";
            default:
                return "PROTO_" + id;
        }
    }
}
