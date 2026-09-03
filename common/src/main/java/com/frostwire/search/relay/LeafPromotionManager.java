/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Promotes healthy CLIENT leaves to capped forwarders (Gnutella ultrapeer
 * promotion): a leaf that can accept inbound connections, holds several
 * live mesh sessions, and has been up a while starts forwarding a few
 * searches so it helps the network without fan-out risk. Sick leaves stay
 * leaves; promoted nodes demote as soon as sessions drain.
 *
 * <p>Promotion never grants full ultrapeer fanout: promoted targets forward
 * to at most {@link #PROMOTED_MAX_FORWARD_TARGETS} peers, matching the leaf
 * multi-homing width. Full fanout stays reserved for configured
 * FORWARDER/BOTH nodes and fat cloud hubs.
 *
 * <p>Phones behind carrier NAT essentially never qualify (no inbound HELLO
 * ever marks them connectable), so promotion is self-selecting and safe to
 * leave enabled. Set {@code ICEBRIDGE_LEAF_PROMOTION=false} to disable.
 */
public final class LeafPromotionManager {

    private static final Logger LOG = Logger.getLogger(LeafPromotionManager.class);

    /** Forwarding endpoints whose fanout can be capped on promotion. */
    public interface ForwardingTarget {
        void setForwardingEnabled(boolean enabled);

        void setMaxForwardTargets(int maxTargets);
    }

    /** Live sessions required to promote. */
    public static final int PROMOTE_MIN_SESSIONS = 3;
    /** Live sessions below which a promoted node demotes (hysteresis). */
    public static final int DEMOTE_BELOW_SESSIONS = 2;
    /** Uptime required before first promotion. */
    public static final long MIN_UPTIME_MS = 10 * 60_000L;
    /** Quiet period after a demotion before re-promotion is allowed. */
    public static final long DEMOTE_COOLDOWN_MS = 5 * 60_000L;
    /** Forward fanout granted to promoted leaves. */
    public static final int PROMOTED_MAX_FORWARD_TARGETS = 3;
    /** Re-evaluation interval. */
    public static final long CHECK_INTERVAL_MS = 60_000L;
    /** Env toggle; anything but {@code "false"} keeps promotion enabled. */
    public static final String ENV_TOGGLE = "ICEBRIDGE_LEAF_PROMOTION";

    private final BooleanSupplier connectable;
    private final IntSupplier sessionCount;
    private final LongSupplier uptimeMs;
    private final BooleanSupplier extraEligible;
    private final List<ForwardingTarget> targets = new ArrayList<>();
    private volatile boolean promoted;
    private volatile long lastDemoteMs;
    private ScheduledExecutorService scheduler;

    public LeafPromotionManager(BooleanSupplier connectable,
                                IntSupplier sessionCount,
                                LongSupplier uptimeMs,
                                BooleanSupplier extraEligible) {
        if (connectable == null) {
            throw new IllegalArgumentException("connectable is null");
        }
        if (sessionCount == null) {
            throw new IllegalArgumentException("sessionCount is null");
        }
        if (uptimeMs == null) {
            throw new IllegalArgumentException("uptimeMs is null");
        }
        if (extraEligible == null) {
            throw new IllegalArgumentException("extraEligible is null");
        }
        this.connectable = connectable;
        this.sessionCount = sessionCount;
        this.uptimeMs = uptimeMs;
        this.extraEligible = extraEligible;
    }

    /** Adds a forwarding endpoint; it immediately matches current state. Nulls are skipped. */
    public synchronized void addTarget(ForwardingTarget target) {
        if (target == null || targets.contains(target)) {
            return;
        }
        targets.add(target);
        applyTo(target);
    }

    public boolean isPromoted() {
        return promoted;
    }

    /**
     * Re-evaluates health and promotes/demotes on transition. Fail-closed:
     * supplier failures keep the current state.
     */
    public synchronized boolean evaluate() {
        boolean next;
        try {
            next = decide(System.currentTimeMillis());
        } catch (Throwable ignored) {
            return promoted;
        }
        if (next != promoted) {
            promoted = next;
            if (!promoted) {
                lastDemoteMs = System.currentTimeMillis();
            }
            apply();
            LOG.info("LeafPromotionManager: " + (promoted ? "promoted to capped forwarder"
                    : "demoted to leaf"));
        }
        return promoted;
    }

    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "leaf-promotion");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                evaluate();
            } catch (Throwable ignored) {
            }
        }, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public static boolean promotionEnabledByEnv() {
        String value = System.getenv(ENV_TOGGLE);
        if (value == null || value.isEmpty()) {
            try {
                value = System.getProperty(ENV_TOGGLE);
            } catch (Throwable ignored) {
            }
        }
        return value == null || !value.equalsIgnoreCase("false");
    }

    private boolean decide(long nowMs) {
        int sessions;
        long uptime;
        try {
            sessions = sessionCount.getAsInt();
            uptime = uptimeMs.getAsLong();
        } catch (Throwable ignored) {
            return promoted;
        }
        boolean eligible;
        try {
            eligible = connectable.getAsBoolean() && extraEligible.getAsBoolean()
                    && uptime >= MIN_UPTIME_MS;
        } catch (Throwable ignored) {
            return promoted;
        }
        if (!promoted) {
            return eligible && sessions >= PROMOTE_MIN_SESSIONS
                    && nowMs - lastDemoteMs >= DEMOTE_COOLDOWN_MS;
        }
        return eligible && sessions >= DEMOTE_BELOW_SESSIONS;
    }

    private void apply() {
        for (ForwardingTarget target : targets) {
            applyTo(target);
        }
    }

    private void applyTo(ForwardingTarget target) {
        try {
            target.setForwardingEnabled(promoted);
            target.setMaxForwardTargets(promoted ? PROMOTED_MAX_FORWARD_TARGETS : 0);
        } catch (Throwable ignored) {
        }
    }
}
