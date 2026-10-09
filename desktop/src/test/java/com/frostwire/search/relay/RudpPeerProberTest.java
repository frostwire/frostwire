/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.icebridge.client.IceBridgeClient.ProbeOutcome;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient.ProbeState;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RudpPeerProberTest {

  private static final byte[] PUB = new byte[32];

  static {
    PUB[0] = 7;
  }

  private static long deadlineIn(long ms) {
    return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms);
  }

  @Test
  void waitsForTheHandshakeAndReturnsTheAuthenticatedKey() {
    AtomicInteger polls = new AtomicInteger();
    RudpPeerProber prober =
        new RudpPeerProber(
            (host, port, cancel) ->
                polls.incrementAndGet() < 3
                    ? new ProbeOutcome(ProbeState.PENDING, null)
                    : new ProbeOutcome(ProbeState.ESTABLISHED, PUB));

    Optional<ProbedPeer> peer = prober.probe("198.51.100.7", 6889, deadlineIn(2_000));

    assertTrue(peer.isPresent());
    assertArrayEquals(PUB, peer.get().pub());
    assertEquals(3, polls.get());
  }

  @Test
  void anEndpointThatNeverAnswersIsGivenUpOnAndItsHandshakeIsCancelled() {
    AtomicInteger cancels = new AtomicInteger();
    RudpPeerProber prober =
        new RudpPeerProber(
            (host, port, cancel) -> {
              if (cancel) cancels.incrementAndGet();
              return new ProbeOutcome(ProbeState.PENDING, null);
            });

    long started = System.nanoTime();
    Optional<ProbedPeer> peer = prober.probe("198.51.100.7", 6889, deadlineIn(300));

    assertTrue(peer.isEmpty());
    assertEquals(1, cancels.get(), "a dead endpoint must not keep a session in the daemon");
    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1_500);
  }

  @Test
  void aRefusedEndpointStopsImmediatelyWithoutCancelling() {
    AtomicInteger calls = new AtomicInteger();
    RudpPeerProber prober =
        new RudpPeerProber(
            (host, port, cancel) -> {
              calls.incrementAndGet();
              return new ProbeOutcome(ProbeState.NONE, null);
            });

    assertTrue(prober.probe("198.51.100.7", 6889, deadlineIn(2_000)).isEmpty());
    assertEquals(1, calls.get());
  }

  @Test
  void invalidEndpointsNeverReachTheDaemon() {
    AtomicInteger calls = new AtomicInteger();
    RudpPeerProber prober =
        new RudpPeerProber(
            (host, port, cancel) -> {
              calls.incrementAndGet();
              return new ProbeOutcome(ProbeState.ESTABLISHED, PUB);
            });

    assertTrue(prober.probe(null, 6889, deadlineIn(1_000)).isEmpty());
    assertTrue(prober.probe("", 6889, deadlineIn(1_000)).isEmpty());
    assertTrue(prober.probe("198.51.100.7", 0, deadlineIn(1_000)).isEmpty());
    assertTrue(prober.probe("198.51.100.7", 65536, deadlineIn(1_000)).isEmpty());
    assertEquals(0, calls.get());
    assertThrows(
        IllegalArgumentException.class, () -> new RudpPeerProber((RudpPeerProber.Daemon) null));
  }

  @Test
  void aFailingDaemonCallIsTreatedAsUnreachable() {
    RudpPeerProber prober =
        new RudpPeerProber(
            (host, port, cancel) -> {
              throw new IllegalStateException("daemon down");
            });

    assertTrue(prober.probe("198.51.100.7", 6889, deadlineIn(1_000)).isEmpty());
  }

  @Test
  void anInterruptedProbeStopsAndKeepsTheInterruptFlag() {
    RudpPeerProber prober =
        new RudpPeerProber((host, port, cancel) -> new ProbeOutcome(ProbeState.PENDING, null));
    Thread.currentThread().interrupt();
    try {
      assertTrue(prober.probe("198.51.100.7", 6889, deadlineIn(5_000)).isEmpty());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }
}
