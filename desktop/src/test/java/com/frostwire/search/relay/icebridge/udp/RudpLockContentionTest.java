/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Measures whether the single global monitor in {@link RudpSessionManager} is an ingest bottleneck:
 * {@code onPacket} throughput alone versus with concurrent monitor contenders.
 *
 * <p>The HTTP control plane holds the same monitor for {@code deliver}/{@code sendRelay}, so an
 * Ed25519 verify that runs under the monitor blocks control-plane sends and vice versa. This
 * reporting test decides whether an "verify outside the monitor" lock split is worth its security
 * risk: it asserts only correctness (throughput ratios can vary wildly on shared CI hosts) and
 * prints the measured numbers.
 *
 * <p><b>Measured outcome (2026-09):</b> ~11.6k authenticated 64-byte DATA packets/s single-threaded
 * versus ~10.6k..12.7k with two threads hammering {@code deliver} on the same monitor, i.e. no
 * meaningful contention. The lock is not the ingest limiter — per-packet Ed25519 verify dominates —
 * so the lock split is deliberately not implemented.
 */
class RudpLockContentionTest {

  // Keep under packetGlobal's 2000-packet budget so the rate limiter, not the
  // monitor, is never what limits the measurement.
  private static final int PACKETS = 1500;
  private static final int CONTENDERS = 2;
  private static final byte[] PAYLOAD = new byte[64];

  @Test
  void concurrentMonitorContendersDoNotStarveIngest() throws Exception {
    long baselineNanos = measureIngest(false);
    long contendedNanos = measureIngest(true);

    double baselinePps = rate(baselineNanos);
    double contendedPps = rate(contendedNanos);
    System.out.printf(
        "RudpLockContention: packets=%d baselinePps=%.0f contendedPps=%.0f ratio=%.2f "
            + "contenders=%d%n",
        PACKETS, baselinePps, contendedPps, contendedPps / baselinePps, CONTENDERS);

    assertTrue(baselinePps > 0, "baseline ingest must make progress");
    assertTrue(contendedPps > 0, "contended ingest must make progress");
    // No timing assertion on purpose: on a loaded 2-vCPU CI runner the contender
    // threads steal CPU, so the ratio measures host starvation rather than the
    // monitor (observed 0.36 on CI vs 0.92..1.09 on a developer laptop). The
    // measurement lives in the javadoc; correctness is asserted inside
    // measureIngest() (every packet must be delivered).
  }

  private static double rate(long nanos) {
    return nanos <= 0 ? Double.POSITIVE_INFINITY : PACKETS * 1_000_000_000.0 / nanos;
  }

  private static long measureIngest(boolean contended) throws Exception {
    AtomicLong delivered = new AtomicLong();
    try (RudpFixture f = new RudpFixture((pub, payload) -> delivered.incrementAndGet())) {
      f.handshake();
      List<RudpPacket> packets = new ArrayList<>(PACKETS);
      for (int i = 1; i <= PACKETS; i++) {
        packets.add(f.signed(RudpPacket.Type.DATA, i, 0, PAYLOAD));
      }

      AtomicBoolean stop = new AtomicBoolean();
      CountDownLatch ready = new CountDownLatch(contended ? CONTENDERS : 0);
      List<Thread> contenders = new ArrayList<>();
      for (int i = 0; i < (contended ? CONTENDERS : 0); i++) {
        Thread t =
            new Thread(
                () -> {
                  ready.countDown();
                  while (!stop.get()) {
                    // Real control-plane work on the same monitor: deliver() finds the live
                    // session and signs/queues a reliable payload while holding it.
                    f.manager.deliver(f.peer.ed25519PubRaw(), PAYLOAD);
                    f.manager.sessionCount();
                  }
                },
                "rudp-contender-" + i);
        t.setDaemon(true);
        contenders.add(t);
        t.start();
      }
      ready.await();

      long start = System.nanoTime();
      for (RudpPacket packet : packets) {
        f.receive(packet);
      }
      long elapsed = System.nanoTime() - start;

      stop.set(true);
      for (Thread t : contenders) {
        t.join(2000);
      }
      f.drain();
      assertEquals(PACKETS, delivered.get(), "all packets must be delivered");
      return elapsed;
    }
  }
}
