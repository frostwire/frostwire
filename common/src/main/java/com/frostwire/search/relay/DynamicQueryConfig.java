/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

/**
 * Gnutella-style dynamic querying parameters for the originator
 * ({@link DistributedSearchPerformer}).
 *
 * <p>Instead of paying full depth x full fanout on every search, the
 * originator starts narrow/shallow and expands only while results are
 * thin. See {@code DESIGN_DYNAMIC_QUERYING.md} for phases, budgets,
 * and stop conditions.
 *
 * <p>All values are originator-local. Nothing here touches the wire:
 * each phase reuses the existing {@code ttl}/{@code path} hop fields,
 * so older peers need no upgrade.
 */
public final class DynamicQueryConfig {

    /** Hard cap on originator phases; the last phase is always full fanout. */
    public static final int MAX_PHASES = 3;
    /** Default result target before the originator stops expanding. */
    public static final int DEFAULT_DESIRED_RESULTS = 10;

    /** Phase 1 (probe): first-hop contacts (cumulative). */
    public static final int PHASE1_MAX_PEERS = 4;
    /** Phase 1 (probe): shallow hop budget. */
    public static final int PHASE1_TTL = 1;
    /** Phase 1 (probe): short wait, seconds. */
    public static final int PHASE1_TIMEOUT_SEC = 3;

    /** Phase 2 (expand): first-hop contacts (cumulative). */
    public static final int PHASE2_MAX_PEERS = 12;
    /** Phase 2 (expand): medium hop budget. */
    public static final int PHASE2_TTL = 2;
    /** Phase 2 (expand): medium wait, seconds. */
    public static final int PHASE2_TIMEOUT_SEC = 6;

    private final int desiredResults;
    private final int maxPhases;

    private DynamicQueryConfig(int desiredResults, int maxPhases) {
        if (desiredResults <= 0) {
            throw new IllegalArgumentException("desiredResults must be > 0");
        }
        if (maxPhases <= 0 || maxPhases > MAX_PHASES) {
            throw new IllegalArgumentException("maxPhases must be in (0, " + MAX_PHASES + "]");
        }
        this.desiredResults = desiredResults;
        this.maxPhases = maxPhases;
    }

    public static DynamicQueryConfig defaults() {
        return new DynamicQueryConfig(DEFAULT_DESIRED_RESULTS, MAX_PHASES);
    }

    public static DynamicQueryConfig withDesiredResults(int desiredResults) {
        return new DynamicQueryConfig(desiredResults, MAX_PHASES);
    }

    /** Deduped result target (local + peer) that stops expansion. */
    public int desiredResults() {
        return desiredResults;
    }

    public int maxPhases() {
        return maxPhases;
    }

    /**
     * Cumulative first-hop budget for {@code phase} (0-based), clamped
     * to the performer's {@code maxPeers}. Slices are disjoint, so the
     * sum of sends across all phases never exceeds {@code maxPeers}.
     */
    int cumulativePeerCap(int phase, int maxPeers) {
        if (phase <= 0) {
            return Math.min(PHASE1_MAX_PEERS, maxPeers);
        }
        if (phase == 1) {
            return Math.min(PHASE2_MAX_PEERS, maxPeers);
        }
        return maxPeers;
    }

    /**
     * Requested hop TTL for {@code phase}. The caller clamps it through
     * the topology soft-max; the final phase always requests the full
     * topology {@code searchTtl}.
     */
    int phaseTtl(int phase, int searchTtl) {
        int want;
        if (phase <= 0) {
            want = PHASE1_TTL;
        } else if (phase == 1) {
            want = PHASE2_TTL;
        } else {
            want = searchTtl;
        }
        return Math.max(0, Math.min(want, searchTtl));
    }

    /** Per-phase wait, never longer than the performer's timeout. */
    int phaseTimeoutSec(int phase, int peerTimeoutSec) {
        int want;
        if (phase <= 0) {
            want = PHASE1_TIMEOUT_SEC;
        } else if (phase == 1) {
            want = PHASE2_TIMEOUT_SEC;
        } else {
            want = peerTimeoutSec;
        }
        return Math.max(1, Math.min(want, peerTimeoutSec));
    }
}
