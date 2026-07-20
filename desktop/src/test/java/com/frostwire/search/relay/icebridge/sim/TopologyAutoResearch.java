/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import com.frostwire.search.relay.icebridge.IceBridgeTopology;
import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator.AggregateReport;
import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator.Config;
import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator.Profile;
import com.frostwire.search.relay.icebridge.sim.IceBridgeNetworkSimulator.ResearchBudgets;
import com.frostwire.util.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Auto-research loop over IceBridge topology parameters for a fixed network profile (hybrid EC2
 * hubs by default).
 *
 * <p>Objective: maximize {@link IceBridgeNetworkSimulator#score} subject to hit-rate and traffic
 * SLOs — so users find content while the EC2 backbone and last-hop leaf queries stay within budget.
 *
 * <p>Produces a ranked list of candidates; the winner can be applied via {@link
 * IceBridgeTopology#applyRemote}.
 */
public final class TopologyAutoResearch {

  private static final Logger LOG = Logger.getLogger(TopologyAutoResearch.class);

  public static final class Candidate {
    public final int meshFanout;
    public final int searchPeerFanout;
    public final int meshHopTtl;
    public final int searchTtl;
    public final int softMax;
    public final int leafUpConnections;
    public final AggregateReport report;
    public final boolean meetsSlos;

    Candidate(
        int n,
        int m,
        int meshTtl,
        int searchTtl,
        int softMax,
        int leafUps,
        AggregateReport report,
        boolean meetsSlos) {
      this.meshFanout = n;
      this.searchPeerFanout = m;
      this.meshHopTtl = meshTtl;
      this.searchTtl = searchTtl;
      this.softMax = softMax;
      this.leafUpConnections = leafUps;
      this.report = report;
      this.meetsSlos = meetsSlos;
    }

    @Override
    public String toString() {
      return String.format(
          "candidate[N=%d M=%d meshTTL=%d searchTTL=%d softMax=%d leafUPs=%d "
              + "score=%.4f slo=%s hit=%.1f%% rare=%.1f%% meanMesh=%.0f meanSearch=%.0f]",
          meshFanout,
          searchPeerFanout,
          meshHopTtl,
          searchTtl,
          softMax,
          leafUpConnections,
          report.score,
          meetsSlos,
          report.hitRate * 100,
          report.rareHitRate * 100,
          report.meanMeshMessages,
          report.meanSearchPeerMessages);
    }
  }

  public static final class Result {
    public final Candidate best;
    public final Candidate bestMeetingSlos;
    public final List<Candidate> ranked;
    public final Config networkTemplate;
    public final ResearchBudgets budgets;

    Result(
        Candidate best,
        Candidate bestMeetingSlos,
        List<Candidate> ranked,
        Config networkTemplate,
        ResearchBudgets budgets) {
      this.best = best;
      this.bestMeetingSlos = bestMeetingSlos;
      this.ranked = ranked;
      this.networkTemplate = networkTemplate;
      this.budgets = budgets;
    }
  }

  /**
   * Hybrid EC2 benchmark network: fat hub mesh we operate + many leaves. Sized for unit-test
   * auto-research (seconds). Scale up for offline studies.
   */
  public static Config hybridEc2BenchmarkNetwork() {
    Config c = new Config();
    c.profile = Profile.HYBRID_EC2;
    c.useLiveTopology = false;
    c.iceBridgeCount = 28;
    c.ec2HubCount = 12;
    c.frostWireCount = 1_500;
    c.contentHolders = 60;
    c.rareContentFraction = 0.30;
    c.searchTrials = 36;
    c.seed = 20260716L;
    c.preferEc2Hubs = 0.90;
    return c;
  }

  /** Larger offline study network (~30k-class leaf mass). Not for CI. */
  public static Config hybridEc2StudyNetwork() {
    Config c = hybridEc2BenchmarkNetwork();
    c.iceBridgeCount = 64;
    c.ec2HubCount = 20;
    c.frostWireCount = 12_000;
    c.contentHolders = 200;
    c.searchTrials = 120;
    return c;
  }

  /**
   * Budgets for high-speed EC2 backbone: mesh can be heavier; last-hop leaf query volume is the
   * main limit.
   */
  public static ResearchBudgets hybridEc2Budgets() {
    ResearchBudgets b = new ResearchBudgets();
    b.minHitRate = 0.95;
    b.minRareHitRate = 0.85;
    b.maxMeanMeshMessages = 3_000;
    b.maxP95MeshMessages = 6_000;
    b.maxMeanSearchPeerMessages = 10_000;
    b.maxP95SearchPeerMessages = 18_000;
    b.hitWeight = 0.78;
    return b;
  }

  /** Coarse grid for CI (~40–60 candidates, unit-test friendly). */
  public static Result runHybridEc2Research() {
    return run(hybridEc2BenchmarkNetwork(), hybridEc2Budgets(), coarseGrid());
  }

  /** Wider grid for offline optimality studies. */
  public static Result runHybridEc2StudyResearch() {
    return run(hybridEc2StudyNetwork(), hybridEc2Budgets(), studyGrid());
  }

  public static final class Grid {
    public int[] meshFanouts = {8, 16, 32};
    public int[] searchPeerFanouts = {20, 30, 40};
    public int[] meshHopTtls = {2, 3};
    public int[] searchTtls = {2, 3};
    public int[] softMaxes = {3};
    public int[] leafUps = {2, 3};
  }

  public static Grid coarseGrid() {
    return new Grid();
  }

  public static Grid studyGrid() {
    Grid g = new Grid();
    g.meshFanouts = new int[] {8, 12, 16, 24, 32};
    g.searchPeerFanouts = new int[] {12, 20, 30, 40, 48};
    g.meshHopTtls = new int[] {2, 3};
    g.searchTtls = new int[] {2, 3};
    g.softMaxes = new int[] {2, 3};
    g.leafUps = new int[] {2, 3};
    return g;
  }

  public static Result run(Config networkTemplate, ResearchBudgets budgets, Grid grid) {
    List<Candidate> all = new ArrayList<>();
    int evaluated = 0;
    for (int n : grid.meshFanouts) {
      for (int m : grid.searchPeerFanouts) {
        for (int meshTtl : grid.meshHopTtls) {
          for (int searchTtl : grid.searchTtls) {
            for (int softMax : grid.softMaxes) {
              // softMax must cover the larger start TTL, otherwise the
              // clamp silently degrades the candidate to a smaller
              // effective TTL than its label claims
              if (softMax < meshTtl || softMax < searchTtl) {
                continue;
              }
              for (int leafUps : grid.leafUps) {
                Config cfg = networkTemplate.copy();
                cfg.useLiveTopology = false;
                cfg.meshFanout = n;
                cfg.searchPeerFanout = m;
                cfg.meshHopTtl = meshTtl;
                cfg.searchTtl = searchTtl;
                cfg.softMax = softMax;
                cfg.leafUpConnections = leafUps;
                // Common random numbers: every candidate runs on the
                // SAME network and content placement, so topology is
                // the only controlled variable. Per-candidate seeds
                // let network-instance noise (rare-hit swings of
                // ±30pp across seeds) dominate the ranking.
                cfg.seed = networkTemplate.seed;

                IceBridgeNetworkSimulator sim = new IceBridgeNetworkSimulator(cfg);
                AggregateReport report = sim.runAggregate(budgets);
                boolean slo = IceBridgeNetworkSimulator.meetsSlos(report, budgets);
                all.add(new Candidate(n, m, meshTtl, searchTtl, softMax, leafUps, report, slo));
                evaluated++;
              }
            }
          }
        }
      }
    }

    all.sort(Comparator.comparingDouble((Candidate c) -> c.report.score).reversed());
    Candidate best = all.isEmpty() ? null : all.get(0);
    Candidate bestSlo = all.stream().filter(c -> c.meetsSlos).findFirst().orElse(null);

    LOG.info(
        "TopologyAutoResearch evaluated "
            + evaluated
            + " candidates; "
            + "best="
            + best
            + "; bestSLO="
            + bestSlo);
    if (best != null) {
      LOG.info("  best report: " + best.report);
    }
    if (bestSlo != null && bestSlo != best) {
      LOG.info("  bestSLO report: " + bestSlo.report);
    }
    return new Result(best, bestSlo, all, networkTemplate, budgets);
  }

  /**
   * Apply the SLO-meeting research winner to the process-wide topology. When no candidate meets the
   * SLOs the topology is left untouched — applying a non-SLO winner silently would ship an
   * unverified profile.
   *
   * @return true if a SLO-meeting candidate was applied
   */
  public static boolean applyWinner(Result result) {
    Candidate w = result.bestMeetingSlos;
    if (w == null) {
      return false;
    }
    IceBridgeTopology.get()
        .applyRemote(
            w.meshFanout,
            w.searchPeerFanout,
            w.meshHopTtl,
            w.searchTtl,
            w.softMax,
            w.leafUpConnections);
    return true;
  }
}
