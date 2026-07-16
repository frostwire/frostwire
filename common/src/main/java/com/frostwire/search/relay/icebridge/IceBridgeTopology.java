/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.util.Logger;

/**
 * Configurable fan-out and hop limits for IceBridge mesh and distributed search.
 *
 * <p>Defaults mirror <b>LimeWire ultrapeer</b> Gnutella settings (LimeWire 5.x /
 * Pro-era core, SIMPP-remoteable):
 * <ul>
 *   <li><b>N = mesh broadcast fanout</b> — {@value #DEFAULT_MESH_BROADCAST_FANOUT}
 *       ({@code ConnectionSettings.NUM_CONNECTIONS} ultrapeer–ultrapeer degree)</li>
 *   <li><b>M = search peer fanout</b> — {@value #DEFAULT_SEARCH_PEER_FANOUT}
 *       ({@code UltrapeerSettings.MAX_LEAVES} last-hop leaf fanout)</li>
 *   <li><b>mesh hop TTL</b> — {@value #DEFAULT_MESH_HOP_TTL}
 *       ({@code ConnectionSettings.SOFT_MAX} hops+ttl horizon)</li>
 *   <li><b>search TTL</b> — {@value #DEFAULT_SEARCH_TTL}
 *       (soft-max horizon; LimeWire also had start {@code TTL=4} with soft clamp)</li>
 *   <li><b>leaf ultrapeer attachments</b> — {@value #DEFAULT_LEAF_ULTRAPEER_CONNECTIONS}
 *       (hardwired leaf preferred count in {@code ConnectionManagerImpl})</li>
 * </ul>
 *
 * <p>Process-wide singleton so {@code update.frostwire.com} can call
 * {@link #applyRemote(int, int, int, int)} (SIMPP analog). Env overrides:
 * {@code ICEBRIDGE_MESH_FANOUT}, {@code ICEBRIDGE_SEARCH_PEER_FANOUT},
 * {@code ICEBRIDGE_MESH_HOP_TTL}, {@code ICEBRIDGE_SEARCH_TTL}.
 *
 * <p>Hard caps match LimeWire remote setting ceilings (96 for degree/leaves).
 */
public final class IceBridgeTopology {

    private static final Logger LOG = Logger.getLogger(IceBridgeTopology.class);

    /**
     * N — max IceBridge peers for mesh RELAY broadcast.
     * LimeWire: {@code ConnectionSettings.NUM_CONNECTIONS = 32}.
     */
    public static final int DEFAULT_MESH_BROADCAST_FANOUT = 32;

    /**
     * M — max FrostWire peers queried / forwarded per search hop.
     * LimeWire: {@code UltrapeerSettings.MAX_LEAVES = 30}.
     */
    public static final int DEFAULT_SEARCH_PEER_FANOUT = 30;

    /**
     * Intermediate IceBridge hops for mesh RELAY.
     * LimeWire: {@code ConnectionSettings.SOFT_MAX = 3}.
     */
    public static final int DEFAULT_MESH_HOP_TTL = 3;

    /**
     * Dual-envelope search hop TTL (app layer).
     * Soft-max horizon (LimeWire start TTL=4 is clamped by SOFT_MAX=3 in practice).
     */
    public static final int DEFAULT_SEARCH_TTL = 3;

    /**
     * How many ultrapeer-class IceBridges a leaf-class FrostWire client
     * attaches to. LimeWire leaf preferred connections = 3.
     */
    public static final int DEFAULT_LEAF_ULTRAPEER_CONNECTIONS = 3;

    /**
     * LimeWire soft-max: if hops + remaining_ttl &gt; softMax, clamp remaining.
     * Same numeric default as {@link #DEFAULT_MESH_HOP_TTL}.
     */
    public static final int DEFAULT_SOFT_MAX = 3;

    /** LimeWire remote ceilings (NUM_CONNECTIONS / MAX_LEAVES max 96). */
    public static final int MAX_MESH_BROADCAST_FANOUT = 96;
    public static final int MAX_SEARCH_PEER_FANOUT = 96;
    /** LimeWire dynamic query max TTL was 6. */
    public static final int MAX_MESH_HOP_TTL = 6;
    public static final int MAX_SEARCH_TTL = 6;
    public static final int MAX_SOFT_MAX = 5;

    private static final IceBridgeTopology INSTANCE = new IceBridgeTopology();

    private volatile int meshBroadcastFanout = DEFAULT_MESH_BROADCAST_FANOUT;
    private volatile int searchPeerFanout = DEFAULT_SEARCH_PEER_FANOUT;
    private volatile int meshHopTtl = DEFAULT_MESH_HOP_TTL;
    private volatile int searchTtl = DEFAULT_SEARCH_TTL;
    private volatile int softMax = DEFAULT_SOFT_MAX;
    private volatile int leafUltrapeerConnections = DEFAULT_LEAF_ULTRAPEER_CONNECTIONS;

    private IceBridgeTopology() {
        meshBroadcastFanout = clamp(
                envInt("ICEBRIDGE_MESH_FANOUT", DEFAULT_MESH_BROADCAST_FANOUT),
                1, MAX_MESH_BROADCAST_FANOUT);
        searchPeerFanout = clamp(
                envInt("ICEBRIDGE_SEARCH_PEER_FANOUT", DEFAULT_SEARCH_PEER_FANOUT),
                1, MAX_SEARCH_PEER_FANOUT);
        meshHopTtl = clamp(
                envInt("ICEBRIDGE_MESH_HOP_TTL", DEFAULT_MESH_HOP_TTL),
                1, MAX_MESH_HOP_TTL);
        searchTtl = clamp(
                envInt("ICEBRIDGE_SEARCH_TTL", DEFAULT_SEARCH_TTL),
                1, MAX_SEARCH_TTL);
        softMax = clamp(
                envInt("ICEBRIDGE_SOFT_MAX", DEFAULT_SOFT_MAX),
                1, MAX_SOFT_MAX);
        leafUltrapeerConnections = clamp(
                envInt("ICEBRIDGE_LEAF_UP_CONNECTIONS", DEFAULT_LEAF_ULTRAPEER_CONNECTIONS),
                1, 16);
    }

    public static IceBridgeTopology get() {
        return INSTANCE;
    }

    /** Max other IceBridge peers for mesh RELAY flood (N). */
    public int meshBroadcastFanout() {
        return meshBroadcastFanout;
    }

    /** Max FrostWire peers per search query / hop (M). */
    public int searchPeerFanout() {
        return searchPeerFanout;
    }

    /** Default hop TTL for mesh RELAY frames. */
    public int meshHopTtl() {
        return meshHopTtl;
    }

    /** Default dual-envelope search TTL. */
    public int searchTtl() {
        return searchTtl;
    }

    /**
     * Soft max horizon (LimeWire {@code SOFT_MAX}): hops + remaining_ttl
     * should not exceed this.
     */
    public int softMax() {
        return softMax;
    }

    /** Leaf attachments to ultrapeer-class IceBridges. */
    public int leafUltrapeerConnections() {
        return leafUltrapeerConnections;
    }

    /**
     * LimeWire soft-max clamp: given hops already consumed and a requested
     * remaining TTL, return the allowed remaining TTL.
     */
    public int clampRemainingTtl(int hopsSoFar, int requestedRemainingTtl) {
        if (requestedRemainingTtl <= 0) {
            return 0;
        }
        int hops = Math.max(0, hopsSoFar);
        int req = requestedRemainingTtl;
        int sm = softMax;
        if (hops + req > sm) {
            return Math.max(0, sm - hops);
        }
        return req;
    }

    /**
     * Apply live limits (e.g. from remote config / SIMPP analog).
     * Pass {@code <= 0} to leave a field unchanged.
     */
    public void applyRemote(int meshFanout, int searchPeerFanout, int meshHopTtl, int searchTtl) {
        applyRemote(meshFanout, searchPeerFanout, meshHopTtl, searchTtl, 0, 0);
    }

    public void applyRemote(int meshFanout, int searchPeerFanout, int meshHopTtl, int searchTtl,
                            int softMax, int leafUpConnections) {
        if (meshFanout > 0) {
            this.meshBroadcastFanout = clamp(meshFanout, 1, MAX_MESH_BROADCAST_FANOUT);
        }
        if (searchPeerFanout > 0) {
            this.searchPeerFanout = clamp(searchPeerFanout, 1, MAX_SEARCH_PEER_FANOUT);
        }
        if (meshHopTtl > 0) {
            this.meshHopTtl = clamp(meshHopTtl, 1, MAX_MESH_HOP_TTL);
        }
        if (searchTtl > 0) {
            this.searchTtl = clamp(searchTtl, 1, MAX_SEARCH_TTL);
        }
        if (softMax > 0) {
            this.softMax = clamp(softMax, 1, MAX_SOFT_MAX);
        }
        if (leafUpConnections > 0) {
            this.leafUltrapeerConnections = clamp(leafUpConnections, 1, 16);
        }
        LOG.info("IceBridgeTopology applied: N(mesh)=" + this.meshBroadcastFanout
                + " M(searchPeers)=" + this.searchPeerFanout
                + " meshHopTtl=" + this.meshHopTtl
                + " searchTtl=" + this.searchTtl
                + " softMax=" + this.softMax
                + " leafUPs=" + this.leafUltrapeerConnections);
    }

    /**
     * Force LimeWire ultrapeer profile (compiled defaults).
     */
    public void applyLimeWireUltrapeerProfile() {
        applyRemote(
                DEFAULT_MESH_BROADCAST_FANOUT,
                DEFAULT_SEARCH_PEER_FANOUT,
                DEFAULT_MESH_HOP_TTL,
                DEFAULT_SEARCH_TTL,
                DEFAULT_SOFT_MAX,
                DEFAULT_LEAF_ULTRAPEER_CONNECTIONS);
    }

    /**
     * Worst-case mesh RELAY packet upper bound for one unknown-target flood
     * on an infinite tree (no overlap). Real meshes are much smaller.
     */
    public static long worstCaseMeshPackets(int n, int hopTtl) {
        if (n <= 0 || hopTtl <= 0) {
            return 0;
        }
        long total = 0;
        long layer = 1;
        for (int d = 0; d < hopTtl; d++) {
            if (layer > Long.MAX_VALUE / Math.max(1, n)) {
                return Long.MAX_VALUE;
            }
            layer = d == 0 ? n : layer * n;
            if (total > Long.MAX_VALUE - layer) {
                return Long.MAX_VALUE;
            }
            total += layer;
        }
        return total;
    }

    /**
     * Worst-case FrostWire peers that receive a search (path-pruned tree).
     */
    public static long worstCaseSearchPeers(int m, int searchTtl) {
        if (m <= 0 || searchTtl <= 0) {
            return 0;
        }
        long total = m;
        long layer = m;
        for (int d = 1; d < searchTtl; d++) {
            long branching = Math.max(1, m - 1);
            if (layer > Long.MAX_VALUE / branching) {
                return Long.MAX_VALUE;
            }
            layer = layer * branching;
            if (total > Long.MAX_VALUE - layer) {
                return Long.MAX_VALUE;
            }
            total += layer;
        }
        return total;
    }

    /** Reset to compiled LimeWire-ultrapeer defaults (tests). */
    public void resetToDefaults() {
        meshBroadcastFanout = DEFAULT_MESH_BROADCAST_FANOUT;
        searchPeerFanout = DEFAULT_SEARCH_PEER_FANOUT;
        meshHopTtl = DEFAULT_MESH_HOP_TTL;
        searchTtl = DEFAULT_SEARCH_TTL;
        softMax = DEFAULT_SOFT_MAX;
        leafUltrapeerConnections = DEFAULT_LEAF_ULTRAPEER_CONNECTIONS;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int envInt(String key, int def) {
        String v = System.getenv(key);
        if (v == null || v.isEmpty()) {
            v = System.getProperty(key);
        }
        if (v == null || v.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
