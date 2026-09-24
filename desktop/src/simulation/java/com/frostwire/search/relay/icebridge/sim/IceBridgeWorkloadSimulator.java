/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import com.frostwire.search.relay.IndexDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Deterministic workload simulation for the IceBridge search protocol. It models a fully connected
 * ultrapeer backbone, multi-homed leaves, digest-prioritized routing, nonce deduplication, and
 * per-requester token-bucket admission. Session setup, NAT, retransmissions, and Bloom-filter false
 * positives are outside this model.
 */
public final class IceBridgeWorkloadSimulator {

  public interface SimulationObserver {
    void onSnapshot(NetworkSnapshot snapshot);
  }

  public enum NodeType {
    ULTRAPEER,
    LEAF
  }

  public static final class WorkloadConfig {
    public int ultrapeerCount = 20;
    public int leafCount = 1_000;
    public int contentItems = 400;
    public int tokensPerItem = 3;
    public double searcherFraction = 0.20;
    public double flooderFraction = 0.01;
    public int searchesPerSearcher = 20;
    public int flooderBurst = 100;
    public int minUplinks = 3;
    public int maxUplinks = 6;
    public int flooderUplinks = 16;
    public int searchPeerFanout = 32;
    public int holderBudget = 8;
    public int maxHoldersPerItem = 8;
    public double digestCoverageFraction = 0.90;
    public int searchTtl = 7;
    public int softMax = 7;
    public int admissionBurst = 30;
    public double admissionRefillPerSecond = 0.5;
    public long seed = 20260922L;

    WorkloadConfig copy() {
      WorkloadConfig copy = new WorkloadConfig();
      copy.ultrapeerCount = ultrapeerCount;
      copy.leafCount = leafCount;
      copy.contentItems = contentItems;
      copy.tokensPerItem = tokensPerItem;
      copy.searcherFraction = searcherFraction;
      copy.flooderFraction = flooderFraction;
      copy.searchesPerSearcher = searchesPerSearcher;
      copy.flooderBurst = flooderBurst;
      copy.minUplinks = minUplinks;
      copy.maxUplinks = maxUplinks;
      copy.flooderUplinks = flooderUplinks;
      copy.searchPeerFanout = searchPeerFanout;
      copy.holderBudget = holderBudget;
      copy.maxHoldersPerItem = maxHoldersPerItem;
      copy.digestCoverageFraction = digestCoverageFraction;
      copy.searchTtl = searchTtl;
      copy.softMax = softMax;
      copy.admissionBurst = admissionBurst;
      copy.admissionRefillPerSecond = admissionRefillPerSecond;
      copy.seed = seed;
      return copy;
    }
  }

  public static final class HealthBudgets {
    public double minFindableHitRate = 0.85;
    public double maxNonFindableP95Messages = 900;
    public double maxFloodAdmissionRatio = 0.35;
    public double maxAmplificationRatio = 1.30;
    public double maxHottestHubShare = 0.35;
    public double maxDuplicateDeliveryRatio = 0.70;
  }

  public static final class NetworkNode {
    public final int id;
    public final String label;
    public final NodeType type;
    public final int connections;
    public final int contentItems;
    public final long messages;
    public final long rejected;
    public final long duplicates;
    public final boolean searcher;
    public final boolean flooder;

    NetworkNode(
        int id,
        String label,
        NodeType type,
        int connections,
        int contentItems,
        long messages,
        long rejected,
        long duplicates,
        boolean searcher,
        boolean flooder) {
      this.id = id;
      this.label = label;
      this.type = type;
      this.connections = connections;
      this.contentItems = contentItems;
      this.messages = messages;
      this.rejected = rejected;
      this.duplicates = duplicates;
      this.searcher = searcher;
      this.flooder = flooder;
    }
  }

  public static final class NetworkEdge {
    public final int from;
    public final int to;
    public final boolean backbone;

    NetworkEdge(int from, int to, boolean backbone) {
      this.from = from;
      this.to = to;
      this.backbone = backbone;
    }
  }

  public static final class ActivityHop {
    /** Direction of the sampled message on the wire. */
    public enum Kind {
      /** Origin or hub to hub/leaf search request or forward. */
      REQUEST,
      /** Holder leaf back to the requesting node. */
      RESPONSE,
      /** Attack traffic from a flooder. */
      FLOOD
    }

    public final int from;
    public final int to;
    public final Kind kind;
    public final int step;

    ActivityHop(int from, int to, Kind kind, int step) {
      this.from = from;
      this.to = to;
      this.kind = kind;
      this.step = step;
    }
  }

  public static final class NetworkSnapshot {
    public final String phase;
    public final int completedSearches;
    public final int totalSearches;
    public final int findableHits;
    public final int findableAttempts;
    public final int floodAdmitted;
    public final int floodAttempts;
    public final long messagesSoFar;
    public final List<NetworkNode> nodes;
    public final List<NetworkEdge> edges;
    public final List<ActivityHop> activity;

    NetworkSnapshot(
        String phase,
        int completedSearches,
        int totalSearches,
        int findableHits,
        int findableAttempts,
        int floodAdmitted,
        int floodAttempts,
        long messagesSoFar,
        List<NetworkNode> nodes,
        List<NetworkEdge> edges,
        List<ActivityHop> activity) {
      this.phase = phase;
      this.completedSearches = completedSearches;
      this.totalSearches = totalSearches;
      this.findableHits = findableHits;
      this.findableAttempts = findableAttempts;
      this.floodAdmitted = floodAdmitted;
      this.floodAttempts = floodAttempts;
      this.messagesSoFar = messagesSoFar;
      this.nodes = List.copyOf(nodes);
      this.edges = edges;
      this.activity = List.copyOf(activity);
    }
  }

  public static final class HubStats {
    public final int id;
    public final int connectedLeaves;
    public final int indexedLeaves;
    public final long admittedSearches;
    public final long rejectedSearches;
    public final long outgoingMessages;

    HubStats(Hub hub) {
      id = hub.id;
      connectedLeaves = hub.leaves.size();
      indexedLeaves = hub.leafDigests.size();
      admittedSearches = hub.admittedSearches;
      rejectedSearches = hub.rejectedSearches;
      outgoingMessages = hub.outgoingMessages;
    }
  }

  public static final class NetworkHealthReport {
    public final WorkloadConfig config;
    public final int ultrapeers;
    public final int leaves;
    public final int searchers;
    public final int flooders;
    public final int findableSearches;
    public final int nonFindableSearches;
    public final int floodAttempts;
    public final int floodAdmitted;
    public final long totalSearchMessages;
    public final long totalResponseMessages;
    public final long hubForwardMessages;
    public final long duplicateHubDeliveries;
    public final double findableHitRate;
    public final double findableMeanMessages;
    public final double findableP95Messages;
    public final double nonFindableMeanMessages;
    public final double nonFindableP95Messages;
    public final double floodAdmissionRatio;
    public final double floodMessagesPerSearch;
    public final double honestMessagesPerSearch;
    public final double amplificationRatio;
    public final double hottestHubShare;
    public final double duplicateDeliveryRatio;
    public final double healthScore;
    public final long durationMillis;
    public final List<HubStats> hubStats;

    NetworkHealthReport(
        WorkloadConfig config,
        int searchers,
        int flooders,
        List<SearchOutcome> findable,
        List<SearchOutcome> nonFindable,
        List<SearchOutcome> flood,
        List<Hub> hubs,
        long durationNanos) {
      this.config = config.copy();
      this.ultrapeers = config.ultrapeerCount;
      this.leaves = config.leafCount;
      this.searchers = searchers;
      this.flooders = flooders;
      this.findableSearches = findable.size();
      this.nonFindableSearches = nonFindable.size();
      this.floodAttempts = flood.size();
      this.floodAdmitted = countAdmitted(flood);
      List<SearchOutcome> honest = new ArrayList<>(findable);
      honest.addAll(nonFindable);
      List<SearchOutcome> all = new ArrayList<>(honest);
      all.addAll(flood);
      this.totalSearchMessages = sum(all, outcome -> outcome.requestMessages);
      this.totalResponseMessages = sum(all, outcome -> outcome.responseMessages);
      this.hubForwardMessages = sum(all, outcome -> outcome.hubForwardMessages);
      this.duplicateHubDeliveries = sum(all, outcome -> outcome.duplicateHubDeliveries);
      this.findableHitRate = ratio(findable, outcome -> outcome.hit);
      this.findableMeanMessages = mean(findable);
      this.findableP95Messages = percentile(findable, 0.95);
      this.nonFindableMeanMessages = mean(nonFindable);
      this.nonFindableP95Messages = percentile(nonFindable, 0.95);
      this.floodAdmissionRatio = divide(floodAdmitted, floodAttempts);
      this.floodMessagesPerSearch = mean(admitted(flood));
      this.honestMessagesPerSearch = mean(honest);
      this.amplificationRatio = divide(floodMessagesPerSearch, honestMessagesPerSearch);
      this.hottestHubShare = hottestHubShare(hubs);
      long leafMessages = sum(all, outcome -> outcome.leafMessages);
      long duplicates = sum(all, outcome -> outcome.duplicateDeliveries);
      this.duplicateDeliveryRatio = divide(duplicates, leafMessages);
      this.healthScore =
          findableHitRate
              - 0.5 * Math.max(0, floodAdmissionRatio - 0.15)
              - 0.25 * duplicateDeliveryRatio
              - 0.25 * hottestHubShare;
      this.durationMillis = Math.max(0, durationNanos / 1_000_000);
      List<HubStats> stats = new ArrayList<>(hubs.size());
      for (Hub hub : hubs) {
        stats.add(new HubStats(hub));
      }
      this.hubStats = List.copyOf(stats);
    }

    public boolean meets(HealthBudgets budgets) {
      return findableHitRate >= budgets.minFindableHitRate
          && nonFindableP95Messages <= budgets.maxNonFindableP95Messages
          && floodAdmissionRatio <= budgets.maxFloodAdmissionRatio
          && amplificationRatio <= budgets.maxAmplificationRatio
          && hottestHubShare <= budgets.maxHottestHubShare
          && duplicateDeliveryRatio <= budgets.maxDuplicateDeliveryRatio;
    }

    @Override
    public String toString() {
      return String.format(
          Locale.US,
          "Network health: %d ultrapeers, %d leaves, %d honest searches, %d flood attempts%n"
              + "  recall=%.3f, findable mean/p95=%.1f/%.0f messages%n"
              + "  miss mean/p95=%.1f/%.0f messages, search/response total=%d/%d%n"
              + "  flood admitted=%d/%d (%.3f), flood/honest amplification=%.3f%n"
              + "  hottest hub share=%.3f, duplicate delivery=%.3f, health score=%.3f%n"
              + "  simulation compute time=%d ms",
          ultrapeers,
          leaves,
          findableSearches + nonFindableSearches,
          floodAttempts,
          findableHitRate,
          findableMeanMessages,
          findableP95Messages,
          nonFindableMeanMessages,
          nonFindableP95Messages,
          totalSearchMessages,
          totalResponseMessages,
          floodAdmitted,
          floodAttempts,
          floodAdmissionRatio,
          amplificationRatio,
          hottestHubShare,
          duplicateDeliveryRatio,
          healthScore,
          durationMillis);
    }
  }

  private static final class SearchOutcome {
    final boolean hit;
    final boolean admitted;
    final int requestMessages;
    final int responseMessages;
    final int leafMessages;
    final int duplicateDeliveries;
    final int hubForwardMessages;
    final int duplicateHubDeliveries;

    SearchOutcome(
        boolean hit,
        boolean admitted,
        int requestMessages,
        int responseMessages,
        int leafMessages,
        int duplicateDeliveries,
        int hubForwardMessages,
        int duplicateHubDeliveries) {
      this.hit = hit;
      this.admitted = admitted;
      this.requestMessages = requestMessages;
      this.responseMessages = responseMessages;
      this.leafMessages = leafMessages;
      this.duplicateDeliveries = duplicateDeliveries;
      this.hubForwardMessages = hubForwardMessages;
      this.duplicateHubDeliveries = duplicateHubDeliveries;
    }

    int totalMessages() {
      return requestMessages + responseMessages;
    }
  }

  private static final class AdmissionBucket {
    double tokens;
    double lastRefillSecond;

    AdmissionBucket(int burst, double nowSecond) {
      tokens = burst;
      lastRefillSecond = nowSecond;
    }

    boolean acquire(WorkloadConfig config, double nowSecond) {
      tokens =
          Math.min(
              config.admissionBurst,
              tokens + (nowSecond - lastRefillSecond) * config.admissionRefillPerSecond);
      lastRefillSecond = nowSecond;
      if (tokens < 1.0) {
        return false;
      }
      tokens -= 1.0;
      return true;
    }
  }

  private static final class ContentItem {
    final int id;
    final Set<String> tokens = new HashSet<>();
    final Set<Integer> holders = new HashSet<>();

    ContentItem(int id) {
      this.id = id;
    }
  }

  private static final class Hub {
    final int id;
    final List<Integer> leaves = new ArrayList<>();
    final Map<Integer, IndexDigest> leafDigests = new HashMap<>();
    IndexDigest clusterDigest;
    final Map<Integer, AdmissionBucket> admission = new HashMap<>();
    final Set<Long> forwarded = new HashSet<>();
    long outgoingMessages;
    long admittedSearches;
    long rejectedSearches;

    Hub(int id) {
      this.id = id;
    }
  }

  private static final class Leaf {
    final int id;
    final List<Integer> uplinks = new ArrayList<>();
    final Set<Integer> heldItems = new HashSet<>();
    int lastRequester = -1;
    long lastNonce = -1;
    long messages;
    long duplicates;
    boolean searcher;
    boolean flooder;

    Leaf(int id) {
      this.id = id;
    }
  }

  private static final class Delivery {
    final int hubId;
    final int ttl;
    final boolean[] path;

    Delivery(int hubId, int ttl, boolean[] path) {
      this.hubId = hubId;
      this.ttl = ttl;
      this.path = path;
    }
  }

  private final WorkloadConfig config;
  private final Random random;
  private final List<Hub> hubs = new ArrayList<>();
  private final List<Leaf> leaves = new ArrayList<>();
  private final List<ContentItem> catalog = new ArrayList<>();
  private final List<NetworkEdge> edges = new ArrayList<>();
  private final List<ActivityHop> lastActivity = new ArrayList<>();
  private final List<Integer> flooderIds = new ArrayList<>();
  private boolean hasRun;

  public IceBridgeWorkloadSimulator(WorkloadConfig config) {
    this.config = config.copy();
    validate(this.config);
    this.random = new Random(this.config.seed);
    buildNetwork();
  }

  public NetworkHealthReport run() {
    return run(null);
  }

  public synchronized NetworkHealthReport run(SimulationObserver observer) {
    if (hasRun) {
      hubs.clear();
      leaves.clear();
      catalog.clear();
      edges.clear();
      lastActivity.clear();
      flooderIds.clear();
      random.setSeed(config.seed);
      buildNetwork();
    }
    hasRun = true;
    long startedNanos = System.nanoTime();
    long observerNanos = 0;
    List<Integer> searchers = sampleLeaves(config.searcherFraction);
    List<Integer> flooders = flooderIds;
    for (int searcher : searchers) {
      leaves.get(searcher).searcher = true;
    }
    List<SearchOutcome> findable = new ArrayList<>();
    List<SearchOutcome> nonFindable = new ArrayList<>();
    List<SearchOutcome> flood = new ArrayList<>();
    long nonce = 1;
    double nowSecond = 0;
    int completed = 0;
    int findableHits = 0;
    int floodAdmittedCount = 0;
    int total =
        flooders.size() * config.flooderBurst + searchers.size() * config.searchesPerSearcher;
    int cadence = observer == null ? 100 : 25;
    observerNanos += publish(observer, "Flood attack", completed, total, 0, 0, 0, 0);

    for (int flooder : flooders) {
      for (int i = 0; i < config.flooderBurst; i++) {
        nowSecond += 0.01;
        boolean trace = observer != null && (completed + 1) % cadence == 0;
        SearchOutcome outcome = search(flooder, null, nonce++, nowSecond, true, trace);
        flood.add(outcome);
        if (outcome.admitted) {
          floodAdmittedCount++;
        }
        completed++;
        if (completed % cadence == 0) {
          observerNanos +=
              publish(
                  observer,
                  "Flood attack",
                  completed,
                  total,
                  findableHits,
                  findable.size(),
                  floodAdmittedCount,
                  flood.size());
        }
      }
    }
    observerNanos +=
        publish(
            observer,
            "Honest search workload",
            completed,
            total,
            findableHits,
            findable.size(),
            floodAdmittedCount,
            flood.size());
    for (int round = 0; round < config.searchesPerSearcher; round++) {
      nowSecond += 30;
      for (int i = 0; i < searchers.size(); i++) {
        int searcher = searchers.get(i);
        ContentItem target =
            (round * searchers.size() + i) % 2 == 0
                ? catalog.get(random.nextInt(catalog.size()))
                : null;
        // Trace a miss too so viewers see that misses produce no result traffic.
        boolean trace = observer != null && (completed + 1) % cadence <= 1;
        SearchOutcome outcome = search(searcher, target, nonce++, nowSecond, false, trace);
        (target == null ? nonFindable : findable).add(outcome);
        if (target != null && outcome.hit) {
          findableHits++;
        }
        completed++;
        if (completed % cadence == 0) {
          observerNanos +=
              publish(
                  observer,
                  "Honest search workload",
                  completed,
                  total,
                  findableHits,
                  findable.size(),
                  floodAdmittedCount,
                  flood.size());
        }
      }
    }
    observerNanos +=
        publish(
            observer,
            "Complete",
            completed,
            total,
            findableHits,
            findable.size(),
            floodAdmittedCount,
            flood.size());
    long durationNanos = System.nanoTime() - startedNanos - observerNanos;
    return new NetworkHealthReport(
        config,
        searchers.size(),
        flooders.size(),
        findable,
        nonFindable,
        flood,
        hubs,
        durationNanos);
  }

  private void buildNetwork() {
    for (int i = 0; i < config.ultrapeerCount; i++) {
      hubs.add(new Hub(i));
    }
    for (int i = 0; i < config.leafCount; i++) {
      leaves.add(new Leaf(i));
    }
    flooderIds.addAll(sampleLeaves(config.flooderFraction));
    for (int flooder : flooderIds) {
      leaves.get(flooder).flooder = true;
    }
    for (int itemId = 0; itemId < config.contentItems; itemId++) {
      ContentItem item = new ContentItem(itemId);
      for (int token = 0; token < config.tokensPerItem; token++) {
        item.tokens.add("token" + itemId + "part" + token);
      }
      int holderCount = 1 + random.nextInt(config.maxHoldersPerItem);
      while (item.holders.size() < holderCount) {
        item.holders.add(random.nextInt(config.leafCount));
      }
      catalog.add(item);
      for (int holder : item.holders) {
        leaves.get(holder).heldItems.add(itemId);
      }
    }
    for (Leaf leaf : leaves) {
      int uplinkCount =
          leaf.flooder
              ? Math.min(config.flooderUplinks, config.ultrapeerCount)
              : config.minUplinks + random.nextInt(config.maxUplinks - config.minUplinks + 1);
      Set<Integer> selected = new HashSet<>();
      while (selected.size() < uplinkCount) {
        selected.add(random.nextInt(config.ultrapeerCount));
      }
      leaf.uplinks.addAll(selected);
      for (int hubId : selected) {
        hubs.get(hubId).leaves.add(leaf.id);
        edges.add(new NetworkEdge(config.ultrapeerCount + leaf.id, hubId, false));
      }
    }
    for (int from = 0; from < config.ultrapeerCount; from++) {
      for (int to = from + 1; to < config.ultrapeerCount; to++) {
        edges.add(new NetworkEdge(from, to, true));
      }
    }
    for (Hub hub : hubs) {
      for (int leafId : hub.leaves) {
        Leaf leaf = leaves.get(leafId);
        if (leaf.heldItems.isEmpty()) {
          continue;
        }
        if (random.nextDouble() >= config.digestCoverageFraction) {
          continue;
        }
        List<String> tokens = new ArrayList<>();
        for (int itemId : leaf.heldItems) {
          tokens.addAll(catalog.get(itemId).tokens);
        }
        hub.leafDigests.put(leafId, IndexDigest.fromTokens(tokens));
      }
      hub.clusterDigest = IndexDigest.aggregate(hub.leafDigests.values());
    }
  }

  private SearchOutcome search(
      int requester,
      ContentItem target,
      long nonce,
      double nowSecond,
      boolean flood,
      boolean trace) {
    Leaf origin = leaves.get(requester);
    List<String> queryTokens =
        target == null
            ? List.of("unfindable" + nonce)
            : IndexDigest.tokenize(String.join(" ", target.tokens));
    ActivityHop.Kind requestKind = flood ? ActivityHop.Kind.FLOOD : ActivityHop.Kind.REQUEST;
    List<Delivery> frontier = new ArrayList<>();
    int originNode = config.ultrapeerCount + requester;
    int traced = 0;
    if (trace) {
      lastActivity.clear();
    }
    for (int hubId : origin.uplinks) {
      boolean[] path = new boolean[config.ultrapeerCount];
      path[hubId] = true;
      frontier.add(new Delivery(hubId, config.searchTtl, path));
      if (trace) {
        lastActivity.add(new ActivityHop(originNode, hubId, requestKind, 0));
        traced++;
      }
    }
    int requestMessages = frontier.size();
    int hubForwardMessages = 0;
    int duplicateHubDeliveries = 0;
    int leafMessages = 0;
    int duplicates = 0;
    boolean admitted = false;
    Set<Integer> hits = new HashSet<>();
    long requestKey = (((long) requester) << 32) ^ nonce;

    while (!frontier.isEmpty()) {
      List<Delivery> next = new ArrayList<>();
      for (Delivery delivery : frontier) {
        int hubId = delivery.hubId;
        Hub hub = hubs.get(hubId);
        if (hub.forwarded.contains(requestKey)) {
          duplicateHubDeliveries++;
          continue;
        }
        AdmissionBucket bucket =
            hub.admission.computeIfAbsent(
                requester, ignored -> new AdmissionBucket(config.admissionBurst, nowSecond));
        if (!bucket.acquire(config, nowSecond)) {
          hub.rejectedSearches++;
          continue;
        }
        hub.forwarded.add(requestKey);
        admitted = true;
        hub.admittedSearches++;
        List<Integer> targets = matchingTargets(hub, queryTokens);
        int forwarded = 0;
        int hops = config.searchTtl - delivery.ttl;
        if (delivery.ttl > 1 && hops + 1 < config.softMax) {
          List<Integer> neighbors = new ArrayList<>();
          for (int neighbor = 0; neighbor < config.ultrapeerCount; neighbor++) {
            if (!delivery.path[neighbor]) {
              neighbors.add(neighbor);
            }
          }
          Collections.shuffle(neighbors, random);
          neighbors.sort(
              (a, b) ->
                  Boolean.compare(
                      hubs.get(b).clusterDigest.routes(queryTokens),
                      hubs.get(a).clusterDigest.routes(queryTokens)));
          for (int neighbor : neighbors) {
            if (targets.size() + forwarded >= config.searchPeerFanout) {
              break;
            }
            boolean[] path = delivery.path.clone();
            path[neighbor] = true;
            next.add(new Delivery(neighbor, delivery.ttl - 1, path));
            forwarded++;
            if (trace && traced < 36) {
              lastActivity.add(new ActivityHop(hubId, neighbor, requestKind, hops + 1));
              traced++;
            }
          }
          requestMessages += forwarded;
          hubForwardMessages += forwarded;
          hub.outgoingMessages += forwarded;
        }
        fillExplorationTargets(hub, targets, config.searchPeerFanout - forwarded);
        requestMessages += targets.size();
        leafMessages += targets.size();
        hub.outgoingMessages += targets.size();
        int shownLeaves = 0;
        for (int leafId : targets) {
          Leaf leaf = leaves.get(leafId);
          boolean replay = leaf.lastRequester == requester && leaf.lastNonce == nonce;
          boolean hit = !replay && target != null && leaf.heldItems.contains(target.id);
          if (trace && (hit || shownLeaves < 2) && traced < 48) {
            lastActivity.add(
                new ActivityHop(hubId, config.ultrapeerCount + leafId, requestKind, hops + 1));
            shownLeaves++;
            traced++;
          }
          if (trace && hit && traced < 48) {
            lastActivity.add(
                new ActivityHop(
                    config.ultrapeerCount + leafId,
                    originNode,
                    ActivityHop.Kind.RESPONSE,
                    hops + 2));
            traced++;
          }
          leaf.messages++;
          if (replay) {
            duplicates++;
            leaf.duplicates++;
            continue;
          }
          leaf.lastRequester = requester;
          leaf.lastNonce = nonce;
          if (hit) {
            hits.add(leafId);
          }
        }
      }
      frontier = next;
    }
    return new SearchOutcome(
        !hits.isEmpty(),
        admitted,
        requestMessages,
        hits.size(),
        leafMessages,
        duplicates,
        hubForwardMessages,
        duplicateHubDeliveries);
  }

  private List<Integer> matchingTargets(Hub hub, List<String> queryTokens) {
    List<Integer> targets = new ArrayList<>(config.holderBudget);
    for (int leafId : hub.leaves) {
      IndexDigest digest = hub.leafDigests.get(leafId);
      if (digest != null && digest.routes(queryTokens)) {
        targets.add(leafId);
        if (targets.size() == config.holderBudget) {
          break;
        }
      }
    }
    return targets;
  }

  private void fillExplorationTargets(Hub hub, List<Integer> targets, int targetCount) {
    List<Integer> exploration = new ArrayList<>(hub.leaves);
    exploration.removeAll(targets);
    Collections.shuffle(exploration, random);
    for (int leafId : exploration) {
      if (targets.size() == targetCount) {
        break;
      }
      targets.add(leafId);
    }
  }

  private List<Integer> sampleLeaves(double fraction) {
    int count = (int) Math.round(config.leafCount * fraction);
    List<Integer> ids = new ArrayList<>(config.leafCount);
    for (int i = 0; i < config.leafCount; i++) {
      ids.add(i);
    }
    Collections.shuffle(ids, random);
    return new ArrayList<>(ids.subList(0, count));
  }

  private long publish(
      SimulationObserver observer,
      String phase,
      int completedSearches,
      int totalSearches,
      int findableHits,
      int findableAttempts,
      int floodAdmitted,
      int floodAttempts) {
    if (observer == null) {
      return 0;
    }
    long started = System.nanoTime();
    List<NetworkNode> nodes = new ArrayList<>(hubs.size() + leaves.size());
    for (Hub hub : hubs) {
      nodes.add(
          new NetworkNode(
              hub.id,
              "UP-" + hub.id,
              NodeType.ULTRAPEER,
              config.ultrapeerCount - 1 + hub.leaves.size(),
              hub.leafDigests.size(),
              hub.outgoingMessages,
              hub.rejectedSearches,
              0,
              false,
              false));
    }
    for (Leaf leaf : leaves) {
      nodes.add(
          new NetworkNode(
              config.ultrapeerCount + leaf.id,
              "LEAF-" + leaf.id,
              NodeType.LEAF,
              leaf.uplinks.size(),
              leaf.heldItems.size(),
              leaf.messages,
              0,
              leaf.duplicates,
              leaf.searcher,
              leaf.flooder));
    }
    long messages = 0;
    for (Hub hub : hubs) {
      messages += hub.outgoingMessages;
    }
    observer.onSnapshot(
        new NetworkSnapshot(
            phase,
            completedSearches,
            totalSearches,
            findableHits,
            findableAttempts,
            floodAdmitted,
            floodAttempts,
            messages,
            nodes,
            List.copyOf(edges),
            lastActivity));
    lastActivity.clear();
    return System.nanoTime() - started;
  }

  private static void validate(WorkloadConfig config) {
    if (config.ultrapeerCount <= 0
        || config.leafCount <= 0
        || config.contentItems <= 0
        || config.tokensPerItem <= 0
        || !Double.isFinite(config.searcherFraction)
        || config.searcherFraction < 0
        || config.searcherFraction > 1
        || !Double.isFinite(config.flooderFraction)
        || config.flooderFraction < 0
        || config.flooderFraction > 1
        || config.searchesPerSearcher < 0
        || config.flooderBurst < 0
        || config.minUplinks <= 0
        || config.maxUplinks < config.minUplinks
        || config.maxUplinks > config.ultrapeerCount
        || config.flooderUplinks <= 0
        || config.searchPeerFanout <= 0
        || config.searchTtl <= 0
        || config.softMax <= 0
        || config.admissionBurst <= 0
        || !Double.isFinite(config.admissionRefillPerSecond)
        || config.admissionRefillPerSecond < 0
        || config.holderBudget <= 0
        || config.maxHoldersPerItem <= 0
        || config.maxHoldersPerItem > config.leafCount
        || !Double.isFinite(config.digestCoverageFraction)
        || config.digestCoverageFraction < 0
        || config.digestCoverageFraction > 1
        || config.holderBudget > config.searchPeerFanout) {
      throw new IllegalArgumentException("invalid workload configuration");
    }
  }

  private static List<SearchOutcome> admitted(List<SearchOutcome> outcomes) {
    List<SearchOutcome> admitted = new ArrayList<>();
    for (SearchOutcome outcome : outcomes) {
      if (outcome.admitted) {
        admitted.add(outcome);
      }
    }
    return admitted;
  }

  private static int countAdmitted(List<SearchOutcome> outcomes) {
    return admitted(outcomes).size();
  }

  private static double ratio(
      List<SearchOutcome> outcomes, java.util.function.Predicate<SearchOutcome> predicate) {
    if (outcomes.isEmpty()) {
      return 0;
    }
    int matches = 0;
    for (SearchOutcome outcome : outcomes) {
      if (predicate.test(outcome)) {
        matches++;
      }
    }
    return divide(matches, outcomes.size());
  }

  private static double mean(List<SearchOutcome> outcomes) {
    return outcomes.isEmpty()
        ? 0
        : divide(sum(outcomes, SearchOutcome::totalMessages), outcomes.size());
  }

  private static double percentile(List<SearchOutcome> outcomes, double percentile) {
    if (outcomes.isEmpty()) {
      return 0;
    }
    List<Integer> messages = new ArrayList<>(outcomes.size());
    for (SearchOutcome outcome : outcomes) {
      messages.add(outcome.totalMessages());
    }
    Collections.sort(messages);
    int index = (int) Math.ceil(percentile * messages.size()) - 1;
    return messages.get(Math.max(0, index));
  }

  private static long sum(List<SearchOutcome> outcomes, ToIntFunction<SearchOutcome> value) {
    long total = 0;
    for (SearchOutcome outcome : outcomes) {
      total += value.applyAsInt(outcome);
    }
    return total;
  }

  private static double hottestHubShare(List<Hub> hubs) {
    long total = 0;
    long hottest = 0;
    for (Hub hub : hubs) {
      total += hub.outgoingMessages;
      hottest = Math.max(hottest, hub.outgoingMessages);
    }
    return divide(hottest, total);
  }

  private static double divide(double numerator, double denominator) {
    return denominator == 0 ? 0 : numerator / denominator;
  }
}
