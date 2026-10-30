/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1397: the rUDP timers run on an injectable clock, so tests and the fuzz
 * harness can move time instead of waiting for it.
 */
class RudpTimersTest {

  private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

  private void advanceSeconds(long seconds) {
    nanos.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
  }

  @Test
  void anIdleAuthenticatedSessionIsEvictedAfterTwoMinutes() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {}, nanos::get)) {
      f.handshake();

      advanceSeconds(119);
      f.manager.tick();
      assertEquals(1, f.manager.sessionCount(), "evicted before 120 s of silence");

      advanceSeconds(2);
      f.manager.tick();
      assertEquals(0, f.manager.sessionCount(), "kept after 120 s of silence");
    }
  }

  @Test
  void aHalfOpenSessionIsEvictedAfterTheRetransmitTimeout() throws Exception {
    try (RudpFixture f = new RudpFixture((pub, payload) -> {}, nanos::get)) {
      byte[] hello = RudpAuth.createHelloPayload(f.peer, f.cid, f.local.ed25519PubRaw());
      f.receive(new RudpPacket(RudpPacket.Type.HELLO, f.cid, 0, 0, hello));
      assertEquals(1, f.manager.sessionCount());

      advanceSeconds(16);
      f.manager.tick();
      assertEquals(0, f.manager.sessionCount(), "a peer that never finishes keeps its session");
    }
  }

  @Test
  void aFragmentGroupExpiresAfterThirtySeconds() {
    FragmentReassembler reassembler = new FragmentReassembler(nanos::get);
    reassembler.accept("session:1", 0, 2, false, new byte[] {1});

    advanceSeconds(29);
    assertFalse(reassembler.hasExpired("session:"));
    advanceSeconds(2);
    assertTrue(reassembler.hasExpired("session:"));
  }

  @Test
  void aShortHelloIsNeverIntendedForAnyone() {
    byte[] pub = new byte[32];
    assertFalse(RudpAuth.intendedFor(new byte[0], pub));
    assertFalse(RudpAuth.intendedFor(new byte[31], pub));
    assertFalse(RudpAuth.intendedFor(new byte[40], pub), "zero-padded into 'anyone'");
  }
}
