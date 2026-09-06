/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

  @Test
  void constructorRejectsBadParameters() {
    assertThrows(IllegalArgumentException.class, () -> new RateLimiter(Double.NaN, 1));
    assertThrows(
        IllegalArgumentException.class, () -> new RateLimiter(1, Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new RateLimiter(-1, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1.0, 0));
    assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1.0, -1.0));
  }

  @Test
  void tryAcquireRejectsBadInputs() {
    RateLimiter rl = new RateLimiter(1.0, 1.0);
    assertFalse(rl.tryAcquire((byte[]) null));
    assertFalse(rl.tryAcquire(new byte[31]));
    assertFalse(rl.tryAcquire((String) null));
    assertFalse(rl.tryAcquire(""));
    assertEquals(0, rl.bucketCount());
  }

  @Test
  void keyChurnIsBoundedAndDoesNotEvictActiveQuota() {
    RateLimiter rl = new RateLimiter(1, 0.0001, 2, 60_000);
    assertTrue(rl.tryAcquire("a"));
    assertTrue(rl.tryAcquire("b"));
    for (int i = 0; i < 1000; i++) {
      assertFalse(rl.tryAcquire("new-" + i));
    }
    assertEquals(2, rl.bucketCount());
    assertFalse(rl.tryAcquire("a"));
  }

  @Test
  void idleCapacityIsRecoveredOnAdmission() throws Exception {
    RateLimiter rl = new RateLimiter(1, 100, 1, 20);
    assertTrue(rl.tryAcquire("a"));
    Thread.sleep(40);
    assertTrue(rl.tryAcquire("b"));
    assertEquals(1, rl.bucketCount());
  }

  @Test
  void weightedAcquisitionIsAtomicAndCannotRefundWithInvalidCosts() {
    RateLimiter limiter = new RateLimiter(10, 0.0001, 1, 60_000);
    assertFalse(limiter.tryAcquire("bytes", -1));
    assertFalse(limiter.tryAcquire("bytes", 0));
    assertFalse(limiter.tryAcquire("bytes", 11));
    assertEquals(0, limiter.bucketCount());
    assertTrue(limiter.tryAcquire("bytes", 7));
    assertFalse(limiter.tryAcquire("bytes", 4));
    assertTrue(limiter.tryAcquire("bytes", 3));
    assertFalse(limiter.tryAcquire("bytes"));
  }

  @Test
  void bucketStartsFullAndDrains() {
    RateLimiter rl = new RateLimiter(3.0, 0.0001); // 3 tokens, near-zero refill
    byte[] pub = new byte[32];
    assertTrue(rl.tryAcquire(pub));
    assertTrue(rl.tryAcquire(pub));
    assertTrue(rl.tryAcquire(pub));
    assertFalse(rl.tryAcquire(pub), "4th request must be rate-limited");
    assertEquals(1, rl.bucketCount());
    assertEquals(3, rl.totalAllowed());
    assertEquals(1, rl.totalRejected());
  }

  @Test
  void perPeerBucketsAreIndependent() {
    RateLimiter rl = new RateLimiter(1.0, 0.0001);
    byte[] peer1 = new byte[32];
    peer1[31] = 0x01;
    byte[] peer2 = new byte[32];
    peer2[31] = 0x02;
    assertTrue(rl.tryAcquire(peer1));
    assertFalse(rl.tryAcquire(peer1), "peer1 used its only token");
    assertTrue(rl.tryAcquire(peer2), "peer2 has its own bucket");
    assertEquals(2, rl.bucketCount());
  }

  @Test
  void refillRestoresTokensOverTime() throws Exception {
    RateLimiter rl = new RateLimiter(1.0, 10.0); // 1 token, 10/sec refill
    byte[] pub = new byte[32];
    assertTrue(rl.tryAcquire(pub));
    assertFalse(rl.tryAcquire(pub), "drained");
    Thread.sleep(200);
    // 200ms * 10/sec = 2 tokens refilled (capped at capacity 1)
    assertTrue(rl.tryAcquire(pub), "refilled after waiting");
  }

  @Test
  void evictIdleRemovesUnusedBuckets() throws Exception {
    RateLimiter rl = new RateLimiter(1.0, 1.0);
    byte[] pub = new byte[32];
    rl.tryAcquire(pub);
    assertEquals(1, rl.bucketCount());
    Thread.sleep(50);
    int removed = rl.evictIdle(20);
    assertEquals(1, removed);
    assertEquals(0, rl.bucketCount());
  }

  @Test
  void evictIdleRejectsNegative() {
    RateLimiter rl = new RateLimiter(1.0, 1.0);
    assertThrows(IllegalArgumentException.class, () -> rl.evictIdle(-1));
  }

  @Test
  void countersAccumulateAcrossBuckets() {
    RateLimiter rl = new RateLimiter(1.0, 0.0001);
    byte[] peer1 = new byte[32];
    peer1[31] = 0x01;
    byte[] peer2 = new byte[32];
    peer2[31] = 0x02;
    rl.tryAcquire(peer1);
    rl.tryAcquire(peer1); // rejected
    rl.tryAcquire(peer2);
    assertEquals(2, rl.totalAllowed());
    assertEquals(1, rl.totalRejected());
  }
}
