/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class IceBridgeCrawlerCliTest {

  private static IceBridgeCrawler.CliOptions parse(String... args) {
    return IceBridgeCrawler.CliOptions.parse(args);
  }

  @Test
  void defaultsAreSensible() {
    IceBridgeCrawler.CliOptions o = parse();
    assertEquals("icebridge-crawl.db", o.dbPath);
    assertEquals(1800, o.intervalSec);
    assertEquals(3, o.buckets);
    assertEquals(15, o.timeoutSec);
    assertEquals(8, o.catalogTimeoutSec);
    assertFalse(o.once);
    assertFalse(o.report);
    assertFalse(o.catalog);
    assertFalse(o.hasRelayUrl());
    assertEquals("", o.relayToken);
    assertEquals(parse().dbPath, IceBridgeCrawler.CliOptions.parse(null).dbPath);
  }

  @Test
  void parsesSeparateAndInlineValues() {
    IceBridgeCrawler.CliOptions o =
        parse(
            "--db",
            "x.db",
            "--once",
            "--interval-sec=60",
            "--buckets",
            "5",
            "--timeout-sec=7",
            "--relay-url=http://127.0.0.1:9",
            "--relay-token",
            "tok",
            "--catalog",
            "--catalog-timeout-sec",
            "4",
            "--report");
    assertEquals("x.db", o.dbPath);
    assertTrue(o.once);
    assertTrue(o.report);
    assertEquals(60, o.intervalSec);
    assertEquals(5, o.buckets);
    assertEquals(7, o.timeoutSec);
    assertEquals("http://127.0.0.1:9", o.relayUrl);
    assertEquals("tok", o.relayToken);
    assertTrue(o.catalog);
    assertTrue(o.hasRelayUrl());
    assertEquals(4, o.catalogTimeoutSec);
    assertEquals("y.db", parse("--db=y.db").dbPath);
    assertEquals("t2", parse("--relay-token=t2").relayToken);
  }

  @Test
  void rejectsBadArguments() {
    assertThrows(IllegalArgumentException.class, () -> parse("--nope"));
    assertThrows(IllegalArgumentException.class, () -> parse("--db"));
    assertThrows(IllegalArgumentException.class, () -> parse("--db="));
    assertThrows(IllegalArgumentException.class, () -> parse("--relay-url="));
    assertThrows(IllegalArgumentException.class, () -> parse("--buckets", "0"));
    assertThrows(IllegalArgumentException.class, () -> parse("--buckets", "x"));
    assertThrows(IllegalArgumentException.class, () -> parse("--interval-sec=-5"));
    assertThrows(IllegalArgumentException.class, () -> parse("--catalog"));
    assertThrows(IllegalArgumentException.class, () -> parse("--timeout-sec"));
  }

  @Test
  void reportModePrintsTheStoredPresence(@TempDir File tmp) {
    File db = new File(tmp, "crawl.db");
    long now = 1_700_000_000_000L;
    try (CrawlerStore store = CrawlerStore.open(db)) {
      store.recordSighting(
          com.frostwire.search.relay.RelayConstants.heartbeatBucketIndex(
              System.currentTimeMillis()),
          "10.0.0.1",
          4000,
          now);
      store.recordTorrent("0123456789abcdef0123456789abcdef01234567", "Ubuntu", 10, 1, "pub", now);
    }
    IceBridgeCrawler.CliOptions options = parse("--report", "--db", db.getPath());
    PrintStream original = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    int code;
    try {
      code = IceBridgeCrawler.run(options);
    } finally {
      System.setOut(original);
    }
    assertEquals(0, code);
    String out = captured.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("presence summary (last 3 buckets)"), out);
    assertTrue(out.contains("total distinct endpoints: 1"), out);
    assertTrue(out.contains("distinct infohashes: 1"), out);
    assertTrue(out.contains("1x 0123456789abcdef0123456789abcdef01234567 Ubuntu"), out);
  }

  @Test
  void runOnceRejectsMissingStoreOrSession() {
    assertThrows(
        IllegalArgumentException.class, () -> IceBridgeCrawler.runOnce(null, null, 1, 1, 0L));
  }
}
