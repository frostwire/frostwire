/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Promotion/demotion policy: healthy leaves become capped forwarders. */
class LeafPromotionManagerTest {

  static final class Env {
    final AtomicBoolean connectable = new AtomicBoolean();
    final AtomicInteger sessions = new AtomicInteger();
    final AtomicLong uptime = new AtomicLong();
    final AtomicBoolean extra = new AtomicBoolean(true);
    final List<Boolean> enabledCalls = new ArrayList<>();
    final List<Integer> capCalls = new ArrayList<>();

    LeafPromotionManager.ForwardingTarget target() {
      return new LeafPromotionManager.ForwardingTarget() {
        @Override
        public void setForwardingEnabled(boolean enabled) {
          enabledCalls.add(enabled);
        }

        @Override
        public void setMaxForwardTargets(int maxTargets) {
          capCalls.add(maxTargets);
        }
      };
    }

    LeafPromotionManager manager() {
      return new LeafPromotionManager(
          connectable::get, sessions::get, uptime::get, extra::get);
    }

    void makeHealthy() {
      connectable.set(true);
      sessions.set(LeafPromotionManager.PROMOTE_MIN_SESSIONS + 2);
      uptime.set(LeafPromotionManager.MIN_UPTIME_MS + 1);
      extra.set(true);
    }
  }

  @Test
  void promotesHealthyLeafWithCappedFanout() {
    Env env = new Env();
    LeafPromotionManager mgr = env.manager();
    mgr.addTarget(env.target());
    env.enabledCalls.clear();
    env.capCalls.clear();
    env.makeHealthy();

    assertTrue(mgr.evaluate());
    assertTrue(mgr.isPromoted());
    assertEquals(List.of(true), env.enabledCalls);
    assertEquals(
        List.of(LeafPromotionManager.PROMOTED_MAX_FORWARD_TARGETS), env.capCalls);
  }

  @Test
  void staysLeafWithoutConnectivitySessionsUptimeOrExtra() {
    // Not connectable.
    assertStaysLeaf(env -> env.sessions.set(9));
    // Too few sessions.
    assertStaysLeaf(
        env -> {
          env.connectable.set(true);
          env.sessions.set(LeafPromotionManager.PROMOTE_MIN_SESSIONS - 1);
        });
    // Too young.
    assertStaysLeaf(
        env -> {
          env.connectable.set(true);
          env.sessions.set(9);
          env.uptime.set(LeafPromotionManager.MIN_UPTIME_MS - 1);
        });
    // Extra eligibility veto (e.g. platform policy).
    assertStaysLeaf(
        env -> {
          env.connectable.set(true);
          env.sessions.set(9);
          env.uptime.set(LeafPromotionManager.MIN_UPTIME_MS + 1);
          env.extra.set(false);
        });
  }

  @Test
  void demotesWhenSessionsDrainWithHysteresis() {
    Env env = new Env();
    LeafPromotionManager mgr = env.manager();
    mgr.addTarget(env.target());
    env.makeHealthy();
    assertTrue(mgr.evaluate());

    // At the demote floor it holds (hysteresis, not flapping).
    env.sessions.set(LeafPromotionManager.DEMOTE_BELOW_SESSIONS);
    assertTrue(mgr.evaluate());
    assertTrue(mgr.isPromoted());

    // Below the floor it demotes and restores leaf behavior.
    env.enabledCalls.clear();
    env.capCalls.clear();
    env.sessions.set(LeafPromotionManager.DEMOTE_BELOW_SESSIONS - 1);
    assertFalse(mgr.evaluate());
    assertFalse(mgr.isPromoted());
    assertEquals(List.of(false), env.enabledCalls);
    assertEquals(List.of(0), env.capCalls);
  }

  @Test
  void cooldownBlocksImmediateRepromotion() {
    Env env = new Env();
    LeafPromotionManager mgr = env.manager();
    mgr.addTarget(env.target());
    env.makeHealthy();
    assertTrue(mgr.evaluate());

    env.sessions.set(0);
    assertFalse(mgr.evaluate());

    // Healthy again right away: still leaf until the cooldown passes.
    env.sessions.set(LeafPromotionManager.PROMOTE_MIN_SESSIONS + 2);
    assertFalse(mgr.evaluate());
    assertFalse(mgr.isPromoted());
  }

  @Test
  void throwingSuppliersKeepCurrentState() {
    LeafPromotionManager mgr =
        new LeafPromotionManager(
            () -> true,
            () -> {
              throw new RuntimeException("sessions unavailable");
            },
            () -> Long.MAX_VALUE,
            () -> true);
    // Must not throw; stays a leaf.
    assertFalse(mgr.evaluate());
    assertFalse(mgr.isPromoted());
  }

  @Test
  void lateTargetImmediatelyMatchesState() {
    Env env = new Env();
    LeafPromotionManager mgr = env.manager();
    env.makeHealthy();
    assertTrue(mgr.evaluate());

    Env late = new Env();
    mgr.addTarget(late.target());
    assertEquals(List.of(true), late.enabledCalls);
    assertEquals(
        List.of(LeafPromotionManager.PROMOTED_MAX_FORWARD_TARGETS), late.capCalls);
  }

  @Test
  void constructorRejectsNullSuppliers() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new LeafPromotionManager(null, () -> 0, () -> 0L, () -> true));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LeafPromotionManager(() -> true, null, () -> 0L, () -> true));
  }

  private static void assertStaysLeaf(java.util.function.Consumer<Env> arrange) {
    Env env = new Env();
    env.uptime.set(Long.MAX_VALUE);
    LeafPromotionManager mgr = env.manager();
    mgr.addTarget(env.target());
    arrange.accept(env);
    assertFalse(mgr.evaluate());
    assertFalse(mgr.isPromoted());
  }
}
