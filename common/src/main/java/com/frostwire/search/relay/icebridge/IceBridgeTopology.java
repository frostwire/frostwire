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
 * <p>Inspired by Gnutella's out-degree of ~7, but split into two layers:
 * <ul>
 *   <li><b>N = mesh broadcast fanout</b> — max other IceBridge servents a
 *       RELAY flood visits when the target is not in the local registry
 *       (Gnutella-style ultrapeer fanout). Default {@value #DEFAULT_MESH_BROADCAST_FANOUT}.</li>
 *   <li><b>M = search peer fanout</b> — max FrostWire peers (SEARCH-capable)
 *       that receive a signed search query at the originator or at each
 *       dual-envelope hop. Default {@value #DEFAULT_SEARCH_PEER_FANOUT}.</li>
 * </ul>
 *
 * <p>Process-wide singleton so a future remote config channel
 * ({@code update.frostwire.com}) can call {@link #applyRemote(int, int, int, int)}
 * without rewiring every component. Env overrides at process start:
 * {@code ICEBRIDGE_MESH_FANOUT}, {@code ICEBRIDGE_SEARCH_PEER_FANOUT},
 * {@code ICEBRIDGE_MESH_HOP_TTL}, {@code ICEBRIDGE_SEARCH_TTL}.
 *
 * <p><b>Amplification sketch</b> (worst case, no overlap, no path pruning):
 * <pre>
 *   mesh msgs ≈ min(N^{meshHopTtl}, mesh_size)
 *   search nodes ≈ 1 + M + M*(M-1)^{searchTtl-1}  (with path loop skip)
 * </pre>
 * Keep N and M small; tune with the simulation test under ~30k peers.
 */
public final class IceBridgeTopology {

    private static final Logger LOG = Logger.getLogger(IceBridgeTopology.class);

    /**
     * N — max IceBridge peers for mesh RELAY broadcast (unknown-target flood).
     * Gnutella used ~7; start at 6.
     */
    public static final int DEFAULT_MESH_BROADCAST_FANOUT = 6;

    /**
     * M — max FrostWire peers queried / forwarded per search hop.
     * Slightly above historical performer default (5); enough for diversity
     * without exploding dual-envelope hops at TTL=2.
     */
    public static final int DEFAULT_SEARCH_PEER_FANOUT = 8;

    /** Intermediate IceBridge hops for mesh RELAY frames. */
    public static final int DEFAULT_MESH_HOP_TTL = 3;

    /** Dual-envelope search hop TTL (app layer). */
    public static final int DEFAULT_SEARCH_TTL = 2;

    /** Hard caps so remote config cannot turn the network into a weapon. */
    public static final int MAX_MESH_BROADCAST_FANOUT = 16;
    public static final int MAX_SEARCH_PEER_FANOUT = 32;
    public static final int MAX_MESH_HOP_TTL = 5;
    public static final int MAX_SEARCH_TTL = 4;

    private static final IceBridgeTopology INSTANCE = new IceBridgeTopology();

    private volatile int meshBroadcastFanout = DEFAULT_MESH_BROADCAST_FANOUT;
    private volatile int searchPeerFanout = DEFAULT_SEARCH_PEER_FANOUT;
    private volatile int meshHopTtl = DEFAULT_MESH_HOP_TTL;
    private volatile int searchTtl = DEFAULT_SEARCH_TTL;

    private IceBridgeTopology() {
        // optional env / system-property bootstrap
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
     * Apply live limits (e.g. from remote config). Values outside hard caps
     * are clamped. Pass {@code <= 0} to leave a field unchanged.
     */
    public void applyRemote(int meshFanout, int searchPeerFanout, int meshHopTtl, int searchTtl) {
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
        LOG.info("IceBridgeTopology applied: N(mesh)=" + this.meshBroadcastFanout
                + " M(searchPeers)=" + this.searchPeerFanout
                + " meshHopTtl=" + this.meshHopTtl
                + " searchTtl=" + this.searchTtl);
    }

    /**
     * Worst-case mesh RELAY packet upper bound for one unknown-target flood
     * (geometric series, no overlap, no loop skip).
     */
    public static long worstCaseMeshPackets(int n, int hopTtl) {
        if (n <= 0 || hopTtl <= 0) {
            return 0;
        }
        // sum_{k=0}^{ttl-1} n^k  — first hop fans to n, then each continues
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
     * Worst-case number of FrostWire peers that receive a search when each
     * hop forwards to M new peers (path-pruned: outdegree M, then M-1).
     */
    public static long worstCaseSearchPeers(int m, int searchTtl) {
        if (m <= 0 || searchTtl <= 0) {
            return 0;
        }
        // originator contacts M; each of those may forward to up to M-1 new
        // peers for remaining hops.
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

    /**
     * Reset to compiled defaults (tests only).
     */
    public void resetToDefaults() {
        meshBroadcastFanout = DEFAULT_MESH_BROADCAST_FANOUT;
        searchPeerFanout = DEFAULT_SEARCH_PEER_FANOUT;
        meshHopTtl = DEFAULT_MESH_HOP_TTL;
        searchTtl = DEFAULT_SEARCH_TTL;
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
