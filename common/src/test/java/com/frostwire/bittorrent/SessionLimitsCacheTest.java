/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The Options window read the session's limits with synchronous calls into libtorrent's session
 * thread, which froze it for seconds while that thread was busy (Icebase #22a7224ac571 and
 * #49bebb7d5c74, frostwire/frostwire#1529). It reads a cache instead, which must not call the
 * session once it has been filled.
 */
class SessionLimitsCacheTest {
  private static final SessionLimits FIRST = new SessionLimits(200, 60, 4, 5, 0, 0, true);
  private static final SessionLimits SECOND = new SessionLimits(300, 80, 6, 7, 1024, 2048, false);

  private final AtomicInteger sessionReads = new AtomicInteger();
  private final AtomicReference<SessionLimits> inSession = new AtomicReference<>(FIRST);
  private final SessionLimitsCache cache =
      new SessionLimitsCache(
          () -> {
            sessionReads.incrementAndGet();
            return inSession.get();
          });

  @Test
  void onceFilledReadingTheLimitsNeverCallsTheSession() throws Exception {
    cache.refresh();
    assertEquals(1, sessionReads.get());

    // What the Options window does on the EDT, many times: the session is not asked again.
    AtomicReference<SessionLimits> seen = new AtomicReference<>();
    Thread edt =
        new Thread(
            () -> {
              for (int i = 0; i < 100; i++) {
                seen.set(cache.get());
              }
            });
    edt.start();
    edt.join();

    assertEquals(FIRST, seen.get());
    assertEquals(1, sessionReads.get(), "the cache called into the session");
  }

  @Test
  void aReadBeforeTheFirstRefreshGivesTheSessionsValuesAndKeepsThem() {
    assertEquals(FIRST, cache.get());
    assertEquals(FIRST, cache.get());
    assertEquals(1, sessionReads.get());
  }

  @Test
  void changesMadeThroughTheEngineAreSeenWithoutAskingTheSession() {
    cache.refresh();

    cache.update(l -> l.withMaxConnections(500));
    cache.update(l -> l.withDownloadRateLimit(4096));
    cache.update(l -> l.withDhtRunning(false));

    SessionLimits limits = cache.get();
    assertEquals(500, limits.maxConnections());
    assertEquals(4096, limits.downloadRateLimit());
    assertFalse(limits.dhtRunning());
    assertEquals(60, limits.maxPeers(), "the other values are untouched");
    assertEquals(1, sessionReads.get());
  }

  @Test
  void aChangeBeforeTheFirstReadIsLeftToThatRead() {
    cache.update(l -> l.withMaxConnections(500));
    assertEquals(0, sessionReads.get());

    inSession.set(new SessionLimits(500, 60, 4, 5, 0, 0, true));
    assertEquals(500, cache.get().maxConnections());
    assertEquals(1, sessionReads.get());
  }

  @Test
  void refreshReplacesWhatWasCached() {
    cache.refresh();
    inSession.set(SECOND);

    assertEquals(FIRST, cache.get());
    cache.refresh();

    assertEquals(SECOND, cache.get());
    assertEquals(2, sessionReads.get());
  }

  @Test
  void everyFieldHasItsOwnUpdater() {
    SessionLimits l = FIRST;
    l = l.withMaxConnections(1).withMaxPeers(2).withMaxActiveDownloads(3).withMaxActiveSeeds(4);
    l = l.withDownloadRateLimit(5).withUploadRateLimit(6).withDhtRunning(false);
    assertEquals(new SessionLimits(1, 2, 3, 4, 5, 6, false), l);
    assertTrue(FIRST.dhtRunning(), "records are immutable");
  }
}
