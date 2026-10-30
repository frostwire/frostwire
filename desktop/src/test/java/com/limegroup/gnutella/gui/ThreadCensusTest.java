/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1393: an OutOfMemoryError "unable to create native thread" said nothing about
 * which threads filled the process. Every report now carries a thread census whose group names
 * never include what follows a thread name's prefix (search text, hosts, URLs).
 */
class ThreadCensusTest {

  /** What the Icebase server accepts as a group name. */
  private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z_.$-]{1,40}");

  @Test
  void groupNamesKeepOnlyTheNamePrefix() {
    assertEquals("pool-N-thread-N", ThreadCensus.groupName("pool-3-thread-12"));
    assertEquals(
        "SuggestionsThread", ThreadCensus.groupName("SuggestionsThread: my secret search"));
    assertEquals("OkHttp", ThreadCensus.groupName("OkHttp https://tracker.example.com/announce"));
    assertEquals(
        "EngineThreadPool", ThreadCensus.groupName("EngineThreadPool:com.frostwire.X.run"));
    assertEquals("Connection-N.N.N.N", ThreadCensus.groupName("Connection-10.0.0.1"));
    assertEquals(
        "BTDownload-StatusRefresh-N", ThreadCensus.groupName("BTDownload-StatusRefresh-2"));
    assertEquals("unnamed", ThreadCensus.groupName(""));
    assertEquals("unnamed", ThreadCensus.groupName("/Users/someone/file"));
    assertEquals("N", ThreadCensus.groupName("42"));
    assertEquals(40, ThreadCensus.groupName("a".repeat(100)).length());
    for (String name :
        new String[] {"Thread-0", "AWT-EventQueue-0", "Ω", "tracker 1.2.3.4:6881", "x\ty"}) {
      assertTrue(
          SERVER_NAME.matcher(ThreadCensus.groupName(name)).matches(),
          name + " -> " + ThreadCensus.groupName(name));
    }
  }

  @Test
  void groupsAreTheLargestFirstAndAtMostEight() {
    String[] names = new String[30];
    long[] allocated = new long[30];
    for (int i = 0; i < 12; i++) {
      names[i] = "OkHttp https://host-" + i;
      allocated[i] = 100L * 1024 * 1024;
    }
    for (int i = 12; i < 30; i++) {
      names[i] = "kind" + (char) ('a' + (i - 12) % 10) + "-" + i;
    }
    names[29] = null; // ended while the census was taken

    List<ThreadCensus.Group> groups = ThreadCensus.group(names, allocated);

    assertEquals(ThreadCensus.MAX_GROUPS, groups.size());
    assertEquals("OkHttp", groups.get(0).name());
    assertEquals(12, groups.get(0).count());
    assertEquals(1200L, groups.get(0).allocMb());
    for (int i = 1; i < groups.size(); i++) {
      assertTrue(groups.get(i - 1).count() >= groups.get(i).count());
    }
  }

  @Test
  void allocationIsLeftOutWhenNotMeasured() {
    List<ThreadCensus.Group> groups = ThreadCensus.group(new String[] {"a-1", "a-2"}, null);
    assertEquals(1, groups.size());
    assertEquals(2, groups.get(0).count());
    assertNull(groups.get(0).allocMb());
  }

  @Test
  void theCensusCountsTheThreadsThatAreRunning() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    Thread[] parked = new Thread[3];
    for (int i = 0; i < parked.length; i++) {
      parked[i] = new Thread(() -> awaitQuietly(release), "CensusProbe-" + i + " secret text");
      parked[i].start();
    }
    try {
      ThreadCensus census = ThreadCensus.take();
      assertNotNull(census);
      assertTrue(census.count >= 4, "count " + census.count);
      assertTrue(census.peak >= census.count);
      ThreadCensus.Group probes =
          census.groups.stream()
              .filter(g -> g.name().equals("CensusProbe-N"))
              .findFirst()
              .orElse(null);
      if (census.groups.size() < ThreadCensus.MAX_GROUPS || probes != null) {
        assertNotNull(probes, "the parked threads form one group");
        assertEquals(3, probes.count());
      }
    } finally {
      release.countDown();
      for (Thread thread : parked) {
        thread.join();
      }
    }
  }

  @Test
  void aCrashReportCarriesTheCensusWithoutThreadNameDetails() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    Thread leaky = new Thread(() -> awaitQuietly(release), "SuggestionsThread: my secret search");
    leaky.start();
    try {
      Path directory = Files.createTempDirectory("frostwire-thread-census");
      CrashReportSpooler spooler =
          new CrashReportSpooler(directory.toFile(), null, "7.1.3", 335, "");
      spooler.spool(new OutOfMemoryError("unable to create native thread: possibly out of memory"));

      Path report =
          Files.list(directory)
              .filter(p -> p.toString().endsWith(".json"))
              .findFirst()
              .orElseThrow();
      String text = Files.readString(report);
      JsonObject json = JsonParser.parseString(text).getAsJsonObject();
      assertEquals("native_thread", json.get("oom_kind").getAsString());
      assertTrue(json.get("thread_count").getAsInt() > 0);
      assertTrue(json.get("thread_peak").getAsInt() >= json.get("thread_count").getAsInt());
      JsonArray groups = json.getAsJsonArray("thread_groups");
      assertTrue(groups.size() > 0 && groups.size() <= ThreadCensus.MAX_GROUPS);
      for (JsonElement element : groups) {
        JsonObject group = element.getAsJsonObject();
        assertTrue(
            SERVER_NAME.matcher(group.get("name").getAsString()).matches(), group.toString());
        assertTrue(group.get("count").getAsInt() > 0);
      }
      assertFalse(text.contains("secret"), text);
    } finally {
      release.countDown();
      leaky.join();
    }
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
