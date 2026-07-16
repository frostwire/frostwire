/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import com.frostwire.search.relay.icebridge.IceBridgeTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * In-memory simulation of a LimeWire-style hierarchical network:
 * <ul>
 *   <li><b>IceBridge forwarders</b> ≈ Gnutella ultrapeers (mesh degree N)</li>
 *   <li><b>FrostWire nodes</b> ≈ leaves (each attaches to {@code leafUPs} IceBridges)</li>
 * </ul>
 *
 * <p>Simulates distributed search: a leaf issues a query to its home IceBridges;
 * each IceBridge floods the mesh with fanout N / hop TTL (soft-max clamped),
 * and at each IceBridge that receives the search, up to M registered FrostWire
 * leaves are asked to answer. Tracks message counts and whether content holders
 * are reached.
 *
 * <p>Not a packet-level rUDP emulator — topology + fanout/TTL economics only.
 */
public final class IceBridgeNetworkSimulator {

    public static final class Config {
        public int iceBridgeCount = 100;
        public int frostWireCount = 2_000;
        public int contentHolders = 100;
        public int searchTrials = 200;
        public long seed = 42L;
        /** If true, use current IceBridgeTopology singleton values. */
        public boolean useLiveTopology = true;

        public int meshFanout = IceBridgeTopology.DEFAULT_MESH_BROADCAST_FANOUT;
        public int searchPeerFanout = IceBridgeTopology.DEFAULT_SEARCH_PEER_FANOUT;
        public int meshHopTtl = IceBridgeTopology.DEFAULT_MESH_HOP_TTL;
        public int searchTtl = IceBridgeTopology.DEFAULT_SEARCH_TTL;
        public int softMax = IceBridgeTopology.DEFAULT_SOFT_MAX;
        public int leafUpConnections = IceBridgeTopology.DEFAULT_LEAF_ULTRAPEER_CONNECTIONS;
    }

    public static final class SearchResult {
        public final boolean hit;
        public final int meshMessages;
        public final int searchPeerMessages;
        public final int iceBridgesVisited;
        public final int frostWiresQueried;
        public final int contentHoldersReached;

        SearchResult(boolean hit, int meshMessages, int searchPeerMessages,
                     int iceBridgesVisited, int frostWiresQueried, int contentHoldersReached) {
            this.hit = hit;
            this.meshMessages = meshMessages;
            this.searchPeerMessages = searchPeerMessages;
            this.iceBridgesVisited = iceBridgesVisited;
            this.frostWiresQueried = frostWiresQueried;
            this.contentHoldersReached = contentHoldersReached;
        }
    }

    public static final class AggregateReport {
        public final Config config;
        public final int iceBridgeEdges;
        public final double meanMeshMessages;
        public final double meanSearchPeerMessages;
        public final double meanIceBridgesVisited;
        public final double meanFrostWiresQueried;
        public final double hitRate;
        public final double p95MeshMessages;
        public final double p95SearchPeerMessages;
        public final long worstCaseInfiniteMesh;
        public final long worstCaseInfiniteSearch;

        AggregateReport(Config config, int iceBridgeEdges,
                        double meanMeshMessages, double meanSearchPeerMessages,
                        double meanIceBridgesVisited, double meanFrostWiresQueried,
                        double hitRate, double p95MeshMessages, double p95SearchPeerMessages,
                        long worstCaseInfiniteMesh, long worstCaseInfiniteSearch) {
            this.config = config;
            this.iceBridgeEdges = iceBridgeEdges;
            this.meanMeshMessages = meanMeshMessages;
            this.meanSearchPeerMessages = meanSearchPeerMessages;
            this.meanIceBridgesVisited = meanIceBridgesVisited;
            this.meanFrostWiresQueried = meanFrostWiresQueried;
            this.hitRate = hitRate;
            this.p95MeshMessages = p95MeshMessages;
            this.p95SearchPeerMessages = p95SearchPeerMessages;
            this.worstCaseInfiniteMesh = worstCaseInfiniteMesh;
            this.worstCaseInfiniteSearch = worstCaseInfiniteSearch;
        }

        @Override
        public String toString() {
            return String.format(
                    "IceBridgeNetworkSimulator[IBs=%d FW=%d edges=%d trials=%d | "
                            + "hitRate=%.1f%% meanMesh=%.1f p95Mesh=%.0f meanSearchMsgs=%.1f "
                            + "p95Search=%.0f meanIBs=%.1f meanFW=%.1f | "
                            + "infiniteTreeMesh=%d infiniteTreeSearch=%d | N=%d M=%d meshTTL=%d searchTTL=%d softMax=%d leafUPs=%d]",
                    config.iceBridgeCount, config.frostWireCount, iceBridgeEdges, config.searchTrials,
                    hitRate * 100.0, meanMeshMessages, p95MeshMessages, meanSearchPeerMessages,
                    p95SearchPeerMessages, meanIceBridgesVisited, meanFrostWiresQueried,
                    worstCaseInfiniteMesh, worstCaseInfiniteSearch,
                    config.meshFanout, config.searchPeerFanout, config.meshHopTtl, config.searchTtl,
                    config.softMax, config.leafUpConnections);
        }
    }

    private final Config config;
    private final Random random;

    /** Undirected IceBridge mesh: id -> neighbor ids. */
    private final List<List<Integer>> mesh = new ArrayList<>();
    /** FrostWire id -> attached IceBridge ids. */
    private final List<List<Integer>> leafAttachments = new ArrayList<>();
    /** IceBridge id -> registered FrostWire leaf ids. */
    private final List<List<Integer>> leavesOnBridge = new ArrayList<>();
    /** FrostWire ids that hold the search content. */
    private final Set<Integer> content = new HashSet<>();

    public IceBridgeNetworkSimulator(Config config) {
        this.config = Objects.requireNonNull(config, "config");
        this.random = new Random(config.seed);
        if (config.useLiveTopology) {
            IceBridgeTopology t = IceBridgeTopology.get();
            config.meshFanout = t.meshBroadcastFanout();
            config.searchPeerFanout = t.searchPeerFanout();
            config.meshHopTtl = t.meshHopTtl();
            config.searchTtl = t.searchTtl();
            config.softMax = t.softMax();
            config.leafUpConnections = t.leafUltrapeerConnections();
        }
        buildNetwork();
    }

    private void buildNetwork() {
        int ib = config.iceBridgeCount;
        int fw = config.frostWireCount;
        if (ib < 2) {
            throw new IllegalArgumentException("need at least 2 IceBridges");
        }
        if (fw < 1) {
            throw new IllegalArgumentException("need at least 1 FrostWire node");
        }

        for (int i = 0; i < ib; i++) {
            mesh.add(new ArrayList<>());
            leavesOnBridge.add(new ArrayList<>());
        }

        // Degree-bounded random regular-ish mesh (ultrapeer–ultrapeer).
        int degree = Math.min(config.meshFanout, ib - 1);
        for (int i = 0; i < ib; i++) {
            Set<Integer> neigh = new HashSet<>(mesh.get(i));
            int guard = 0;
            while (neigh.size() < degree && guard++ < degree * 20) {
                int j = random.nextInt(ib);
                if (j == i) {
                    continue;
                }
                if (mesh.get(j).size() >= degree && !neigh.contains(j)) {
                    // Prefer unsaturated peers, but allow overflow occasionally.
                    if (random.nextDouble() > 0.15) {
                        continue;
                    }
                }
                if (neigh.add(j)) {
                    mesh.get(i).add(j);
                    if (!mesh.get(j).contains(i)) {
                        mesh.get(j).add(i);
                    }
                }
            }
        }

        // Leaves attach to leafUpConnections distinct IceBridges.
        int attach = Math.min(config.leafUpConnections, ib);
        for (int f = 0; f < fw; f++) {
            List<Integer> ups = new ArrayList<>(attach);
            Set<Integer> chosen = new HashSet<>();
            while (chosen.size() < attach) {
                chosen.add(random.nextInt(ib));
            }
            ups.addAll(chosen);
            leafAttachments.add(ups);
            for (int u : ups) {
                leavesOnBridge.get(u).add(f);
            }
        }

        // Content holders.
        int holders = Math.min(config.contentHolders, fw);
        while (content.size() < holders) {
            content.add(random.nextInt(fw));
        }
    }

    public int iceBridgeEdgeCount() {
        int e = 0;
        for (List<Integer> n : mesh) {
            e += n.size();
        }
        return e / 2;
    }

    /**
     * Run one search from a random FrostWire leaf looking for content holders.
     */
    public SearchResult runSearch() {
        int originLeaf = random.nextInt(config.frostWireCount);
        List<Integer> homeBridges = leafAttachments.get(originLeaf);

        int meshMessages = 0;
        int searchPeerMessages = 0;
        Set<Integer> ibVisited = new HashSet<>();
        Set<Integer> fwQueried = new HashSet<>();
        Set<Integer> holdersHit = new HashSet<>();

        // BFS on IceBridge mesh with fanout N and hop TTL (soft-max).
        // State: (iceBridgeId, remainingTtl, hopsSoFar)
        Queue<int[]> q = new ArrayDeque<>();
        Set<String> meshSeen = new HashSet<>(); // ibId|fromId to reduce loops

        int startTtl = clampSoft(0, config.meshHopTtl);
        for (int home : homeBridges) {
            String key = home + "|-1";
            if (meshSeen.add(key)) {
                q.add(new int[]{home, startTtl, 0});
            }
        }

        while (!q.isEmpty()) {
            int[] state = q.poll();
            int ib = state[0];
            int remTtl = state[1];
            int hops = state[2];
            ibVisited.add(ib);

            // Deliver search to up to M leaves on this IceBridge (last-hop / local registry).
            List<Integer> leaves = leavesOnBridge.get(ib);
            if (!leaves.isEmpty()) {
                List<Integer> shuffled = new ArrayList<>(leaves);
                Collections.shuffle(shuffled, random);
                int m = Math.min(config.searchPeerFanout, shuffled.size());
                for (int i = 0; i < m; i++) {
                    int leaf = shuffled.get(i);
                    if (fwQueried.add(leaf)) {
                        searchPeerMessages++;
                        if (content.contains(leaf)) {
                            holdersHit.add(leaf);
                        }
                    }
                }
            }

            // Dual-envelope-style extra hop among leaves is not modeled as a
            // second mesh BFS; M already approximates UP→leaf fanout. Search
            // TTL soft-max limits how deep mesh flood can go (hops).
            if (remTtl <= 0) {
                continue;
            }

            List<Integer> neighbors = new ArrayList<>(mesh.get(ib));
            Collections.shuffle(neighbors, random);
            int n = Math.min(config.meshFanout, neighbors.size());
            int nextHops = hops + 1;
            int nextRem = clampSoft(nextHops, remTtl - 1);
            if (nextRem < 0) {
                continue;
            }
            int sent = 0;
            for (int nb : neighbors) {
                if (sent >= n) {
                    break;
                }
                String edgeKey = nb + "|" + ib;
                // Allow re-visit of a bridge only via new remaining budget not needed;
                // prevent full revisits.
                if (ibVisited.contains(nb) && meshSeen.contains(nb + "|*")) {
                    continue;
                }
                if (!meshSeen.add(edgeKey)) {
                    continue;
                }
                meshMessages++;
                sent++;
                meshSeen.add(nb + "|*");
                q.add(new int[]{nb, nextRem, nextHops});
            }
        }

        // Path-pruned dual-envelope search among FrostWire peers (originator
        // fans to M trusted peers via its home bridges' leaf sets).
        // Already counted via leavesOnBridge delivery; optional extra hop:
        if (config.searchTtl > 1) {
            List<Integer> frontier = new ArrayList<>(fwQueried);
            Set<Integer> seenFw = new HashSet<>(fwQueried);
            for (int depth = 1; depth < config.searchTtl; depth++) {
                List<Integer> next = new ArrayList<>();
                for (int leaf : frontier) {
                    // Each leaf may forward to M peers on its attached bridges.
                    List<Integer> candidates = new ArrayList<>();
                    for (int up : leafAttachments.get(leaf)) {
                        candidates.addAll(leavesOnBridge.get(up));
                    }
                    Collections.shuffle(candidates, random);
                    int forwarded = 0;
                    for (int c : candidates) {
                        if (forwarded >= config.searchPeerFanout) {
                            break;
                        }
                        if (c == leaf || seenFw.contains(c)) {
                            continue;
                        }
                        seenFw.add(c);
                        next.add(c);
                        searchPeerMessages++;
                        fwQueried.add(c);
                        if (content.contains(c)) {
                            holdersHit.add(c);
                        }
                        forwarded++;
                    }
                }
                frontier = next;
                if (frontier.isEmpty()) {
                    break;
                }
            }
        }

        boolean hit = !holdersHit.isEmpty();
        return new SearchResult(hit, meshMessages, searchPeerMessages,
                ibVisited.size(), fwQueried.size(), holdersHit.size());
    }

    private int clampSoft(int hopsSoFar, int requestedRemaining) {
        if (requestedRemaining <= 0) {
            return 0;
        }
        if (hopsSoFar + requestedRemaining > config.softMax) {
            return Math.max(0, config.softMax - hopsSoFar);
        }
        return requestedRemaining;
    }

    public AggregateReport runAggregate() {
        List<Integer> meshCounts = new ArrayList<>();
        List<Integer> searchCounts = new ArrayList<>();
        double sumMesh = 0, sumSearch = 0, sumIb = 0, sumFw = 0;
        int hits = 0;
        for (int t = 0; t < config.searchTrials; t++) {
            SearchResult r = runSearch();
            meshCounts.add(r.meshMessages);
            searchCounts.add(r.searchPeerMessages);
            sumMesh += r.meshMessages;
            sumSearch += r.searchPeerMessages;
            sumIb += r.iceBridgesVisited;
            sumFw += r.frostWiresQueried;
            if (r.hit) {
                hits++;
            }
        }
        int trials = config.searchTrials;
        Collections.sort(meshCounts);
        Collections.sort(searchCounts);
        return new AggregateReport(
                config,
                iceBridgeEdgeCount(),
                sumMesh / trials,
                sumSearch / trials,
                sumIb / trials,
                sumFw / trials,
                (double) hits / trials,
                percentile(meshCounts, 0.95),
                percentile(searchCounts, 0.95),
                IceBridgeTopology.worstCaseMeshPackets(config.meshFanout, config.meshHopTtl),
                IceBridgeTopology.worstCaseSearchPeers(config.searchPeerFanout, config.searchTtl));
    }

    private static double percentile(List<Integer> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.min(sorted.size() - 1, Math.floor(p * (sorted.size() - 1)));
        return sorted.get(idx);
    }
}
