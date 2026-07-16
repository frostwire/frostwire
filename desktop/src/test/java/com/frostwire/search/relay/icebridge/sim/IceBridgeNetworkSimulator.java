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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Hierarchical network simulator for IceBridge topology benchmarking.
 *
 * <p><b>Hybrid model (target production shape):</b>
 * <ul>
 *   <li><b>EC2 IceBridge hubs</b> — high-bandwidth forwarders we operate
 *       (fat backbone; high fanout is affordable)</li>
 *   <li><b>Optional edge IceBridges</b> — lower-capacity community/home relays</li>
 *   <li><b>FrostWire clients</b> — leaves multi-homed to hubs (prefer EC2)</li>
 * </ul>
 *
 * <p>Unlike residential LimeWire ultrapeers, EC2 hubs share a high-speed
 * mesh, so N (mesh fanout) is constrained more by amplification policy than
 * by uplink. M (search peer fanout) is the main last-hop cost.
 *
 * <p>Not packet-level rUDP — topology + fanout/TTL economics for auto-research.
 */
public final class IceBridgeNetworkSimulator {

    public enum Profile {
        /** Pure LimeWire-style uniform ultrapeers (all IBs equal). */
        LIMEWIRE_UNIFORM,
        /** EC2 high-speed hub mesh + optional edge bridges + leaves. */
        HYBRID_EC2
    }

    public static final class Config {
        public Profile profile = Profile.HYBRID_EC2;
        public int iceBridgeCount = 100;
        /** EC2 hubs within iceBridgeCount (rest are edge). Hybrid only. */
        public int ec2HubCount = 16;
        public int frostWireCount = 2_000;
        public int contentHolders = 100;
        /** Fraction of content that is "rare" (only 1–2 holders). */
        public double rareContentFraction = 0.25;
        public int searchTrials = 200;
        public long seed = 42L;
        public boolean useLiveTopology = true;

        public int meshFanout = IceBridgeTopology.DEFAULT_MESH_BROADCAST_FANOUT;
        public int searchPeerFanout = IceBridgeTopology.DEFAULT_SEARCH_PEER_FANOUT;
        public int meshHopTtl = IceBridgeTopology.DEFAULT_MESH_HOP_TTL;
        public int searchTtl = IceBridgeTopology.DEFAULT_SEARCH_TTL;
        public int softMax = IceBridgeTopology.DEFAULT_SOFT_MAX;
        public int leafUpConnections = IceBridgeTopology.DEFAULT_LEAF_ULTRAPEER_CONNECTIONS;

        /**
         * Prefer attaching leaves to EC2 hubs this fraction of the time
         * (hybrid). 1.0 = always prefer hubs when available.
         */
        public double preferEc2Hubs = 0.85;

        public Config copy() {
            Config c = new Config();
            c.profile = profile;
            c.iceBridgeCount = iceBridgeCount;
            c.ec2HubCount = ec2HubCount;
            c.frostWireCount = frostWireCount;
            c.contentHolders = contentHolders;
            c.rareContentFraction = rareContentFraction;
            c.searchTrials = searchTrials;
            c.seed = seed;
            c.useLiveTopology = useLiveTopology;
            c.meshFanout = meshFanout;
            c.searchPeerFanout = searchPeerFanout;
            c.meshHopTtl = meshHopTtl;
            c.searchTtl = searchTtl;
            c.softMax = softMax;
            c.leafUpConnections = leafUpConnections;
            c.preferEc2Hubs = preferEc2Hubs;
            return c;
        }
    }

    public static final class SearchResult {
        public final boolean hit;
        public final boolean rareQuery;
        public final int meshMessages;
        public final int searchPeerMessages;
        public final int iceBridgesVisited;
        public final int frostWiresQueried;
        public final int contentHoldersReached;
        /** Approximate hop distance until first hit (0 if home, -1 if miss). */
        public final int hopsToFirstHit;

        SearchResult(boolean hit, boolean rareQuery, int meshMessages, int searchPeerMessages,
                     int iceBridgesVisited, int frostWiresQueried, int contentHoldersReached,
                     int hopsToFirstHit) {
            this.hit = hit;
            this.rareQuery = rareQuery;
            this.meshMessages = meshMessages;
            this.searchPeerMessages = searchPeerMessages;
            this.iceBridgesVisited = iceBridgesVisited;
            this.frostWiresQueried = frostWiresQueried;
            this.contentHoldersReached = contentHoldersReached;
            this.hopsToFirstHit = hopsToFirstHit;
        }
    }

    public static final class AggregateReport {
        public final Config config;
        public final int iceBridgeEdges;
        public final int ec2Hubs;
        public final double meanMeshMessages;
        public final double meanSearchPeerMessages;
        public final double meanIceBridgesVisited;
        public final double meanFrostWiresQueried;
        public final double hitRate;
        public final double rareHitRate;
        public final double commonHitRate;
        public final double p95MeshMessages;
        public final double p95SearchPeerMessages;
        public final double meanHopsToHit;
        public final long worstCaseInfiniteMesh;
        public final long worstCaseInfiniteSearch;
        /** Scalar score for auto-research (higher is better). */
        public final double score;

        AggregateReport(Config config, int iceBridgeEdges, int ec2Hubs,
                        double meanMeshMessages, double meanSearchPeerMessages,
                        double meanIceBridgesVisited, double meanFrostWiresQueried,
                        double hitRate, double rareHitRate, double commonHitRate,
                        double p95MeshMessages, double p95SearchPeerMessages,
                        double meanHopsToHit,
                        long worstCaseInfiniteMesh, long worstCaseInfiniteSearch,
                        double score) {
            this.config = config;
            this.iceBridgeEdges = iceBridgeEdges;
            this.ec2Hubs = ec2Hubs;
            this.meanMeshMessages = meanMeshMessages;
            this.meanSearchPeerMessages = meanSearchPeerMessages;
            this.meanIceBridgesVisited = meanIceBridgesVisited;
            this.meanFrostWiresQueried = meanFrostWiresQueried;
            this.hitRate = hitRate;
            this.rareHitRate = rareHitRate;
            this.commonHitRate = commonHitRate;
            this.p95MeshMessages = p95MeshMessages;
            this.p95SearchPeerMessages = p95SearchPeerMessages;
            this.meanHopsToHit = meanHopsToHit;
            this.worstCaseInfiniteMesh = worstCaseInfiniteMesh;
            this.worstCaseInfiniteSearch = worstCaseInfiniteSearch;
            this.score = score;
        }

        @Override
        public String toString() {
            return String.format(
                    "sim[%s IBs=%d ec2=%d FW=%d edges=%d trials=%d | "
                            + "hit=%.1f%% rareHit=%.1f%% commonHit=%.1f%% score=%.3f | "
                            + "meanMesh=%.1f p95Mesh=%.0f meanSearch=%.1f p95Search=%.0f "
                            + "meanIBs=%.1f meanFW=%.1f meanHops=%.2f | "
                            + "N=%d M=%d meshTTL=%d searchTTL=%d softMax=%d leafUPs=%d]",
                    config.profile, config.iceBridgeCount, ec2Hubs, config.frostWireCount,
                    iceBridgeEdges, config.searchTrials,
                    hitRate * 100, rareHitRate * 100, commonHitRate * 100, score,
                    meanMeshMessages, p95MeshMessages, meanSearchPeerMessages, p95SearchPeerMessages,
                    meanIceBridgesVisited, meanFrostWiresQueried, meanHopsToHit,
                    config.meshFanout, config.searchPeerFanout, config.meshHopTtl,
                    config.searchTtl, config.softMax, config.leafUpConnections);
        }
    }

    /**
     * SLO-style budgets for auto-research. EC2 hubs tolerate higher mesh
     * traffic; last-hop leaf queries are the scarcer resource.
     */
    public static final class ResearchBudgets {
        public double minHitRate = 0.95;
        public double minRareHitRate = 0.85;
        public double maxMeanMeshMessages = 2_500;
        public double maxP95MeshMessages = 5_000;
        public double maxMeanSearchPeerMessages = 8_000;
        public double maxP95SearchPeerMessages = 15_000;
        /** Weight of hit rate vs traffic in score (0–1 favors hits). */
        public double hitWeight = 0.75;
    }

    private final Config config;
    private final Random random;
    private final List<List<Integer>> mesh = new ArrayList<>();
    private final boolean[] isEc2Hub;
    private final List<List<Integer>> leafAttachments = new ArrayList<>();
    private final List<List<Integer>> leavesOnBridge = new ArrayList<>();
    /** Content item id -> holder leaf ids. */
    private final List<Set<Integer>> contentCatalog = new ArrayList<>();
    private final List<Boolean> contentIsRare = new ArrayList<>();

    public IceBridgeNetworkSimulator(Config config) {
        this.config = Objects.requireNonNull(config, "config").copy();
        this.random = new Random(this.config.seed);
        if (this.config.useLiveTopology) {
            IceBridgeTopology t = IceBridgeTopology.get();
            this.config.meshFanout = t.meshBroadcastFanout();
            this.config.searchPeerFanout = t.searchPeerFanout();
            this.config.meshHopTtl = t.meshHopTtl();
            this.config.searchTtl = t.searchTtl();
            this.config.softMax = t.softMax();
            this.config.leafUpConnections = t.leafUltrapeerConnections();
        }
        int ib = this.config.iceBridgeCount;
        this.isEc2Hub = new boolean[ib];
        buildNetwork();
        buildContentCatalog();
    }

    private void buildNetwork() {
        int ib = config.iceBridgeCount;
        int fw = config.frostWireCount;
        if (ib < 2 || fw < 1) {
            throw new IllegalArgumentException("need >=2 IceBridges and >=1 FrostWire");
        }

        int hubs = config.profile == Profile.HYBRID_EC2
                ? Math.min(Math.max(2, config.ec2HubCount), ib)
                : ib;
        for (int i = 0; i < ib; i++) {
            mesh.add(new ArrayList<>());
            leavesOnBridge.add(new ArrayList<>());
            isEc2Hub[i] = i < hubs;
        }

        if (config.profile == Profile.HYBRID_EC2) {
            buildHybridMesh(hubs);
        } else {
            buildUniformMesh(Math.min(config.meshFanout, ib - 1));
        }

        int attach = Math.min(config.leafUpConnections, ib);
        for (int f = 0; f < fw; f++) {
            List<Integer> ups = pickLeafAttachments(attach, hubs);
            leafAttachments.add(ups);
            for (int u : ups) {
                leavesOnBridge.get(u).add(f);
            }
        }
    }

    /** Fully connect EC2 hubs; edge bridges attach to 2–4 hubs + sparse edge mesh. */
    private void buildHybridMesh(int hubs) {
        int ib = config.iceBridgeCount;
        // Fat EC2 backbone: full mesh among hubs (high-speed links).
        for (int i = 0; i < hubs; i++) {
            for (int j = i + 1; j < hubs; j++) {
                mesh.get(i).add(j);
                mesh.get(j).add(i);
            }
        }
        // Edge bridges: connect to 2–4 hubs + a few peer edges.
        for (int e = hubs; e < ib; e++) {
            int hubLinks = 2 + random.nextInt(3);
            hubLinks = Math.min(hubLinks, hubs);
            Set<Integer> chosen = new HashSet<>();
            while (chosen.size() < hubLinks) {
                chosen.add(random.nextInt(hubs));
            }
            for (int h : chosen) {
                mesh.get(e).add(h);
                mesh.get(h).add(e);
            }
            // sparse edge-to-edge
            if (ib - hubs > 1 && random.nextBoolean()) {
                int other = hubs + random.nextInt(ib - hubs);
                if (other != e) {
                    mesh.get(e).add(other);
                    mesh.get(other).add(e);
                }
            }
        }
    }

    private void buildUniformMesh(int degree) {
        int ib = config.iceBridgeCount;
        for (int i = 0; i < ib; i++) {
            Set<Integer> neigh = new HashSet<>(mesh.get(i));
            int guard = 0;
            while (neigh.size() < degree && guard++ < degree * 25) {
                int j = random.nextInt(ib);
                if (j == i) {
                    continue;
                }
                if (mesh.get(j).size() >= degree && !neigh.contains(j) && random.nextDouble() > 0.2) {
                    continue;
                }
                if (neigh.add(j)) {
                    mesh.get(i).add(j);
                    if (!mesh.get(j).contains(i)) {
                        mesh.get(j).add(i);
                    }
                }
            }
        }
    }

    private List<Integer> pickLeafAttachments(int attach, int hubs) {
        Set<Integer> chosen = new HashSet<>();
        while (chosen.size() < attach) {
            if (config.profile == Profile.HYBRID_EC2 && hubs > 0
                    && random.nextDouble() < config.preferEc2Hubs) {
                chosen.add(random.nextInt(hubs));
            } else {
                chosen.add(random.nextInt(config.iceBridgeCount));
            }
        }
        return new ArrayList<>(chosen);
    }

    private void buildContentCatalog() {
        int items = Math.max(1, config.contentHolders);
        int rareItems = (int) Math.round(items * config.rareContentFraction);
        for (int c = 0; c < items; c++) {
            boolean rare = c < rareItems;
            contentIsRare.add(rare);
            int holders = rare ? (1 + random.nextInt(2)) : (3 + random.nextInt(12));
            holders = Math.min(holders, config.frostWireCount);
            Set<Integer> set = new HashSet<>();
            while (set.size() < holders) {
                set.add(random.nextInt(config.frostWireCount));
            }
            contentCatalog.add(set);
        }
    }

    public int iceBridgeEdgeCount() {
        int e = 0;
        for (List<Integer> n : mesh) {
            e += n.size();
        }
        return e / 2;
    }

    public int ec2HubCount() {
        int n = 0;
        for (boolean h : isEc2Hub) {
            if (h) {
                n++;
            }
        }
        return n;
    }

    public SearchResult runSearch() {
        int contentId = random.nextInt(contentCatalog.size());
        Set<Integer> holders = contentCatalog.get(contentId);
        boolean rare = contentIsRare.get(contentId);

        int originLeaf = random.nextInt(config.frostWireCount);
        List<Integer> homeBridges = leafAttachments.get(originLeaf);

        int meshMessages = 0;
        int searchPeerMessages = 0;
        Set<Integer> ibVisited = new HashSet<>();
        Set<Integer> fwQueried = new HashSet<>();
        Set<Integer> holdersHit = new HashSet<>();
        int hopsToFirstHit = -1;

        Queue<int[]> q = new ArrayDeque<>();
        Set<String> meshSeen = new HashSet<>();
        int startTtl = clampSoft(0, config.meshHopTtl);
        for (int home : homeBridges) {
            if (meshSeen.add(home + "|-1")) {
                q.add(new int[]{home, startTtl, 0});
            }
        }

        while (!q.isEmpty()) {
            int[] state = q.poll();
            int ib = state[0];
            int remTtl = state[1];
            int hops = state[2];
            ibVisited.add(ib);

            List<Integer> leaves = leavesOnBridge.get(ib);
            if (!leaves.isEmpty()) {
                List<Integer> shuffled = new ArrayList<>(leaves);
                Collections.shuffle(shuffled, random);
                // EC2 hubs can afford full M; edge bridges use half (capacity model).
                int mCap = isEc2Hub[ib] ? config.searchPeerFanout
                        : Math.max(3, config.searchPeerFanout / 2);
                int m = Math.min(mCap, shuffled.size());
                for (int i = 0; i < m; i++) {
                    int leaf = shuffled.get(i);
                    if (fwQueried.add(leaf)) {
                        searchPeerMessages++;
                        if (holders.contains(leaf)) {
                            holdersHit.add(leaf);
                            if (hopsToFirstHit < 0) {
                                hopsToFirstHit = hops;
                            }
                        }
                    }
                }
            }

            if (remTtl <= 0) {
                continue;
            }
            List<Integer> neighbors = new ArrayList<>(mesh.get(ib));
            Collections.shuffle(neighbors, random);
            // Prefer flooding other EC2 hubs first (fat backbone).
            neighbors.sort((a, b) -> Boolean.compare(isEc2Hub[b], isEc2Hub[a]));
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
                if (meshSeen.contains(nb + "|*")) {
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

        // Dual-envelope search hops (app layer): each queried leaf may forward
        // to at most M new peers on its attached IceBridges — path-pruned, not
        // a full population flood. Depth limited by searchTtl-1.
        if (config.searchTtl > 1 && !fwQueried.isEmpty()) {
            List<Integer> frontier = new ArrayList<>(fwQueried);
            Set<Integer> seenFw = new HashSet<>(fwQueried);
            for (int depth = 1; depth < config.searchTtl; depth++) {
                List<Integer> next = new ArrayList<>();
                // Global remaining budget this hop: O(M * |frontier|) but
                // capped so low-M profiles stay sparse.
                int hopBudget = Math.max(config.searchPeerFanout,
                        config.searchPeerFanout * Math.min(8, frontier.size()));
                int hopUsed = 0;
                Collections.shuffle(frontier, random);
                for (int leaf : frontier) {
                    if (hopUsed >= hopBudget) {
                        break;
                    }
                    List<Integer> candidates = new ArrayList<>();
                    for (int up : leafAttachments.get(leaf)) {
                        candidates.addAll(leavesOnBridge.get(up));
                    }
                    Collections.shuffle(candidates, random);
                    int forwarded = 0;
                    for (int c : candidates) {
                        if (forwarded >= config.searchPeerFanout || hopUsed >= hopBudget) {
                            break;
                        }
                        if (c == leaf || seenFw.contains(c)) {
                            continue;
                        }
                        seenFw.add(c);
                        next.add(c);
                        searchPeerMessages++;
                        hopUsed++;
                        fwQueried.add(c);
                        forwarded++;
                        if (holders.contains(c)) {
                            holdersHit.add(c);
                            if (hopsToFirstHit < 0) {
                                hopsToFirstHit = depth + 1;
                            }
                        }
                    }
                }
                frontier = next;
                if (frontier.isEmpty()) {
                    break;
                }
            }
        }

        return new SearchResult(!holdersHit.isEmpty(), rare, meshMessages, searchPeerMessages,
                ibVisited.size(), fwQueried.size(), holdersHit.size(), hopsToFirstHit);
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
        return runAggregate(new ResearchBudgets());
    }

    public AggregateReport runAggregate(ResearchBudgets budgets) {
        List<Integer> meshCounts = new ArrayList<>();
        List<Integer> searchCounts = new ArrayList<>();
        double sumMesh = 0, sumSearch = 0, sumIb = 0, sumFw = 0, sumHops = 0;
        int hits = 0, rareHits = 0, rareTrials = 0, commonHits = 0, commonTrials = 0;
        int hopSamples = 0;
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
                if (r.hopsToFirstHit >= 0) {
                    sumHops += r.hopsToFirstHit;
                    hopSamples++;
                }
            }
            if (r.rareQuery) {
                rareTrials++;
                if (r.hit) {
                    rareHits++;
                }
            } else {
                commonTrials++;
                if (r.hit) {
                    commonHits++;
                }
            }
        }
        int trials = config.searchTrials;
        Collections.sort(meshCounts);
        Collections.sort(searchCounts);
        double hitRate = (double) hits / trials;
        double rareHitRate = rareTrials == 0 ? 1.0 : (double) rareHits / rareTrials;
        double commonHitRate = commonTrials == 0 ? 1.0 : (double) commonHits / commonTrials;
        double meanMesh = sumMesh / trials;
        double meanSearch = sumSearch / trials;
        double p95Mesh = percentile(meshCounts, 0.95);
        double p95Search = percentile(searchCounts, 0.95);
        double meanHops = hopSamples == 0 ? -1 : sumHops / hopSamples;
        double score = score(hitRate, rareHitRate, meanMesh, meanSearch, p95Mesh, p95Search, budgets);
        return new AggregateReport(
                config, iceBridgeEdgeCount(), ec2HubCount(),
                meanMesh, meanSearch, sumIb / trials, sumFw / trials,
                hitRate, rareHitRate, commonHitRate, p95Mesh, p95Search, meanHops,
                IceBridgeTopology.worstCaseMeshPackets(config.meshFanout, config.meshHopTtl),
                IceBridgeTopology.worstCaseSearchPeers(config.searchPeerFanout, config.searchTtl),
                score);
    }

    /**
     * Multi-objective score in [roughly -1, 1]: high hit rates, low traffic.
     * Candidates that miss SLOs are heavily penalized.
     */
    public static double score(double hitRate, double rareHitRate,
                               double meanMesh, double meanSearch,
                               double p95Mesh, double p95Search,
                               ResearchBudgets b) {
        double hitScore = b.hitWeight * hitRate + (1.0 - b.hitWeight) * rareHitRate;
        double meshNorm = Math.min(1.0, meanMesh / Math.max(1.0, b.maxMeanMeshMessages));
        double searchNorm = Math.min(1.0, meanSearch / Math.max(1.0, b.maxMeanSearchPeerMessages));
        double p95Norm = 0.5 * Math.min(1.0, p95Mesh / Math.max(1.0, b.maxP95MeshMessages))
                + 0.5 * Math.min(1.0, p95Search / Math.max(1.0, b.maxP95SearchPeerMessages));
        double cost = 0.45 * meshNorm + 0.40 * searchNorm + 0.15 * p95Norm;
        double s = hitScore - 0.55 * cost;
        if (hitRate < b.minHitRate) {
            s -= 2.0 * (b.minHitRate - hitRate);
        }
        if (rareHitRate < b.minRareHitRate) {
            s -= 1.5 * (b.minRareHitRate - rareHitRate);
        }
        if (meanMesh > b.maxMeanMeshMessages) {
            s -= 0.5;
        }
        if (meanSearch > b.maxMeanSearchPeerMessages) {
            s -= 0.5;
        }
        return s;
    }

    public static boolean meetsSlos(AggregateReport r, ResearchBudgets b) {
        return r.hitRate >= b.minHitRate
                && r.rareHitRate >= b.minRareHitRate
                && r.meanMeshMessages <= b.maxMeanMeshMessages
                && r.p95MeshMessages <= b.maxP95MeshMessages
                && r.meanSearchPeerMessages <= b.maxMeanSearchPeerMessages
                && r.p95SearchPeerMessages <= b.maxP95SearchPeerMessages;
    }

    private static double percentile(List<Integer> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.min(sorted.size() - 1, Math.floor(p * (sorted.size() - 1)));
        return sorted.get(idx);
    }
}
