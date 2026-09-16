/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IceBridgeEventLogTest {

  @BeforeEach
  void resetSingleton() {
    IceBridgeEventLog log = IceBridgeEventLog.instance();
    log.clear();
    log.setEnabled(true);
  }

  @Test
  void tailReturnsNewestFirst() {
    IceBridgeEventLog log = new IceBridgeEventLog(10);
    log.record(IceBridgeEvent.of(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.SEARCH, "a", "first"));
    log.record(IceBridgeEvent.of(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.SEARCH, "b", "second"));

    List<IceBridgeEvent> tail = log.tail(2);
    assertEquals(2, tail.size());
    assertEquals("second", tail.get(0).message());
    assertEquals("first", tail.get(1).message());
  }

  @Test
  void ringBufferEvictsOldestAtCapacity() {
    IceBridgeEventLog log = new IceBridgeEventLog(3);
    for (int i = 0; i < 5; i++) {
      log.record(IceBridgeEvent.of(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.GENERAL, "", "e" + i));
    }
    assertEquals(3, log.size());
    List<IceBridgeEvent> tail = log.tail(3);
    assertEquals("e4", tail.get(0).message());
    assertEquals("e2", tail.get(2).message());
  }

  @Test
  void queryFiltersByLevelCategoryTextPeerAndLimit() {
    IceBridgeEventLog log = new IceBridgeEventLog(50);
    log.record(IceBridgeEvent.of(IceBridgeEvent.Level.DEBUG, IceBridgeEvent.Category.FORWARD, "aa", "forward a"));
    log.record(IceBridgeEvent.of(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.FORWARD, "bb", "forward b"));
    log.record(IceBridgeEvent.of(IceBridgeEvent.Level.ERROR, IceBridgeEvent.Category.ERROR, "", "boom"));

    assertEquals(2, log.query(null, Set.of(IceBridgeEvent.Category.FORWARD), null, null, 0, 50).size());
    assertEquals(
        2,
        log.query(IceBridgeEvent.Level.INFO, null, null, null, 0, 50).size(),
        "minLevel INFO keeps INFO+ERROR and excludes DEBUG");
    assertEquals(1, log.query(null, null, "boom", null, 0, 50).size());
    assertEquals(1, log.query(null, null, null, "bb", 0, 50).size());
    assertEquals(1, log.query(null, null, null, null, 0, 1).size(), "limit wins");
    assertEquals(
        0,
        log.query(null, null, null, null, System.currentTimeMillis() + 60_000, 50).size(),
        "since in the future matches nothing");
  }

  @Test
  void disabledLogIsANoOp() {
    IceBridgeEventLog log = IceBridgeEventLog.instance();
    log.setEnabled(false);
    log.record(IceBridgeEvent.of(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.SEARCH, "", "x"));
    assertEquals(0, log.size());
    assertFalse(IceBridgeEvents.enabled());
  }

  @Test
  void listenersAreNotifiedAndExceptionsAreIsolated() {
    IceBridgeEventLog log = new IceBridgeEventLog(10);
    AtomicInteger ok = new AtomicInteger();
    java.util.function.Consumer<IceBridgeEvent> counting = e -> ok.incrementAndGet();
    log.addListener(
        e -> {
          throw new RuntimeException("bad listener");
        });
    log.addListener(counting);

    log.record(IceBridgeEvent.of(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.GENERAL, "", "x"));
    assertEquals(1, ok.get(), "a throwing listener must not stop the others");

    log.removeListener(counting);
    log.record(IceBridgeEvent.of(IceBridgeEvent.Level.INFO, IceBridgeEvent.Category.GENERAL, "", "y"));
    assertEquals(1, ok.get());
  }

  @Test
  void facadeEmitsWhenEnabled() {
    IceBridgeEvents.search("ff", "searched miami");
    IceBridgeEvents.error("failed", new IllegalStateException("nope"));
    List<IceBridgeEvent> all = IceBridgeEventLog.instance().tail(10);
    assertEquals(2, all.size());
    assertEquals(IceBridgeEvent.Category.ERROR, all.get(0).category());
    assertTrue(all.get(0).message().contains("IllegalStateException"));
  }

  @Test
  void parseHelpersAreLenient() {
    Set<IceBridgeEvent.Category> cats = IceBridgeEventLog.parseCategories("search, FORWARD, bogus");
    assertTrue(cats.contains(IceBridgeEvent.Category.SEARCH));
    assertTrue(cats.contains(IceBridgeEvent.Category.FORWARD));
    assertEquals(2, cats.size());
    assertEquals(IceBridgeEvent.Level.DEBUG, IceBridgeEventLog.parseLevel(null));
    assertEquals(IceBridgeEvent.Level.WARN, IceBridgeEventLog.parseLevel("warn"));
    assertEquals(IceBridgeEvent.Level.DEBUG, IceBridgeEventLog.parseLevel("nonsense"));
  }
}
