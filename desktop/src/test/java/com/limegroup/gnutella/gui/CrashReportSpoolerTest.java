/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.limewire.util.CommonUtils;

class CrashReportSpoolerTest {
  @Test
  void writesOnlyAllowlistedAnonymousCrashFields() throws Exception {
    Path directory = Files.createTempDirectory("frostwire-crash-spool");
    CrashReportSpooler spooler = new CrashReportSpooler(directory.toFile(), null, "7.0.4", 331, "");

    spooler.spool(new IllegalArgumentException("must not be uploaded"));

    Path report = Files.list(directory).findFirst().orElseThrow();
    JsonObject json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
    Set<String> expected =
        new HashSet<>(
            Arrays.asList(
                "schema_version",
                "report_type",
                "platform",
                "app_version",
                "app_build",
                "os_name",
                "os_version",
                "os_arch",
                "runtime_version",
                "jre_version",
                "java_vendor",
                "jlibtorrent_version",
                "telluride_build",
                "cpu_count",
                "max_memory_mb",
                "memory_bucket",
                "heap_used_mb",
                "exception_class",
                "stack_frames",
                "report_nonce"));

    assertEquals(expected, json.keySet());
    assertEquals(1, json.get("schema_version").getAsInt());
    assertEquals("crash", json.get("report_type").getAsString());
    assertEquals("desktop", json.get("platform").getAsString());
    assertEquals("7.0.4", json.get("app_version").getAsString());
    assertTrue(json.getAsJsonPrimitive("app_build").isString());
    assertEquals(331, json.get("app_build").getAsInt());
    assertTrue(json.get("memory_bucket").getAsString().matches("lt_128m|128_512m|512m_2g|gte_2g"));
    assertEquals(
        IllegalArgumentException.class.getName(), json.get("exception_class").getAsString());
    assertFalse(json.toString().contains("must not be uploaded"));
    assertTrue(json.get("stack_frames").getAsJsonArray().size() > 0);
    assertTrue(json.get("stack_frames").getAsJsonArray().size() <= 64);
    assertEquals(
        new HashSet<>(Arrays.asList("class", "method", "line")),
        json.get("stack_frames").getAsJsonArray().get(0).getAsJsonObject().keySet());
    assertNotNull(json.get("report_nonce").getAsString());
    assertTrue(json.get("report_nonce").getAsString().matches("[0-9a-f]{32}"));
  }

  @Test
  void classLoadingErrorsNameTheMissingClassButNotTheMessage() throws Exception {
    Path directory = Files.createTempDirectory("frostwire-crash-spool");
    CrashReportSpooler spooler = new CrashReportSpooler(directory.toFile(), null, "7.0.4", 331, "");

    spooler.spool(new NoClassDefFoundError("com/frostwire/util/UrlUtils"));

    Path report =
        Files.list(directory)
            .filter(path -> path.toString().endsWith(".json"))
            .findFirst()
            .orElseThrow();
    JsonObject json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
    assertEquals(NoClassDefFoundError.class.getName(), json.get("exception_class").getAsString());
    assertEquals("com.frostwire.util.UrlUtils", json.get("missing_class").getAsString());
  }

  @Test
  void missingClassIsOnlyEverAClassName() {
    assertEquals(
        "com.foo.Bar", CrashReportSpooler.missingClass(new NoClassDefFoundError("com/foo/Bar")));
    assertEquals(
        "com.foo.Bar",
        CrashReportSpooler.missingClass(
            new NoClassDefFoundError("Could not initialize class com.foo.Bar")));
    assertEquals(
        "com.foo.Bar$Inner",
        CrashReportSpooler.missingClass(new ClassNotFoundException("com.foo.Bar$Inner")));
    assertEquals(
        null,
        CrashReportSpooler.missingClass(
            new NoClassDefFoundError("com/foo/Bar (wrong name: com/foo/Baz)")));
    assertEquals(
        null, CrashReportSpooler.missingClass(new NoClassDefFoundError("/Users/someone/x")));
    assertEquals(null, CrashReportSpooler.missingClass(new NoClassDefFoundError()));
    assertEquals(null, CrashReportSpooler.missingClass(new IllegalStateException("com.foo.Bar")));
  }

  @Test
  void devRunReportsCarryTheDevTag() throws Exception {
    Path directory = Files.createTempDirectory("frostwire-crash-spool");
    CrashReportSpooler spooler =
        new CrashReportSpooler(directory.toFile(), null, "7.0.4", 331, CrashReportSpooler.DEV_TAG);

    spooler.spool(new IllegalStateException("not uploaded"));
    spooler.spoolStrictMode("java.awt.event.InvocationEvent#1200", 1);

    Set<String> classes = new HashSet<>();
    for (Path report :
        Files.list(directory).filter(path -> path.toString().endsWith(".json")).toList()) {
      JsonObject json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
      classes.add(
          (json.has("exception_class") ? json.get("exception_class") : json.get("violation_class"))
              .getAsString());
    }
    assertEquals(
        Set.of("dev:java.lang.IllegalStateException", "dev:java.awt.event.InvocationEvent#1200"),
        classes);
  }

  @Test
  void devRunsAreTaggedAndReleasesAreNot() {
    assertEquals(CrashReportSpooler.DEV_TAG, CrashReportSpooler.runTag("false", false));
    assertEquals(CrashReportSpooler.DEV_TAG, CrashReportSpooler.runTag(null, true));
    assertEquals("", CrashReportSpooler.runTag("true", true));
    assertEquals("", CrashReportSpooler.runTag(null, false));
  }

  @Test
  void outOfMemoryReportsSayWhatRanOutButNotTheMessage() throws Exception {
    Path directory = Files.createTempDirectory("frostwire-crash-spool");
    CrashReportSpooler spooler = new CrashReportSpooler(directory.toFile(), null, "7.0.4", 331, "");

    spooler.spool(new OutOfMemoryError("Metaspace"));

    Path report = Files.list(directory).findFirst().orElseThrow();
    JsonObject json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
    assertEquals(OutOfMemoryError.class.getName(), json.get("exception_class").getAsString());
    assertEquals("metaspace", json.get("oom_kind").getAsString());
    assertFalse(json.toString().contains("Metaspace"));
    assertTrue(json.getAsJsonPrimitive("heap_used_mb").isNumber());
    assertTrue(json.get("heap_used_mb").getAsInt() >= 0);
  }

  @Test
  void oomKindNamesEachJvmMessage() {
    assertEquals("heap", CrashReportSpooler.oomKind(new OutOfMemoryError("Java heap space")));
    assertEquals(
        "gc_overhead",
        CrashReportSpooler.oomKind(new OutOfMemoryError("GC overhead limit exceeded")));
    assertEquals("metaspace", CrashReportSpooler.oomKind(new OutOfMemoryError("Metaspace")));
    assertEquals(
        "compressed_class",
        CrashReportSpooler.oomKind(new OutOfMemoryError("Compressed class space")));
    assertEquals(
        "native_thread",
        CrashReportSpooler.oomKind(
            new OutOfMemoryError(
                "unable to create native thread: possibly out of memory or process/resource limits"
                    + " reached")));
    assertEquals(
        "direct_buffer",
        CrashReportSpooler.oomKind(
            new OutOfMemoryError("Cannot reserve 1048576 bytes of direct buffer memory")));
    assertEquals(
        "array_size",
        CrashReportSpooler.oomKind(new OutOfMemoryError("Requested array size exceeds VM limit")));
    assertEquals("other", CrashReportSpooler.oomKind(new OutOfMemoryError()));
    assertEquals(null, CrashReportSpooler.oomKind(new IllegalStateException("Java heap space")));
  }

  @Test
  void capsSpoolAndCreatesFreshNoncePerReport() throws Exception {
    Path directory = Files.createTempDirectory("frostwire-crash-spool");
    CrashReportSpooler spooler = new CrashReportSpooler(directory.toFile(), null, "7.0.4", 331, "");

    for (int i = 0; i < 10; i++) {
      spooler.spool(new RuntimeException("report " + i));
    }

    assertEquals(5, Files.list(directory).count());
    String first = Files.readString(Files.list(directory).findFirst().orElseThrow());
    String second = Files.readString(Files.list(directory).skip(1).findFirst().orElseThrow());
    assertNotEquals(
        JsonParser.parseString(first).getAsJsonObject().get("report_nonce"),
        JsonParser.parseString(second).getAsJsonObject().get("report_nonce"));
  }

  @Test
  void throttleBudgetEnforcedPerKeyPerWindow() {
    String key = "throttle-budget-test";
    long now = System.currentTimeMillis();
    for (int i = 0; i < 5; i++) {
      assertTrue(CrashReportSpooler.tryAcquire(key, now));
    }
    assertFalse(CrashReportSpooler.tryAcquire(key, now));
  }

  @Test
  void throttleWindowResetsAfterExpiry() {
    String key = "throttle-window-reset-test";
    long now = System.currentTimeMillis();
    for (int i = 0; i < 5; i++) {
      assertTrue(CrashReportSpooler.tryAcquire(key, now));
    }
    assertFalse(CrashReportSpooler.tryAcquire(key, now));
    assertTrue(CrashReportSpooler.tryAcquire(key, now + 60L * 60L * 1000L + 1));
  }

  @Test
  void throttleKeysAreIndependent() {
    long now = System.currentTimeMillis();
    String fullKey = "throttle-independent-full-test";
    String freshKey = "throttle-independent-fresh-test";
    for (int i = 0; i < 5; i++) {
      assertTrue(CrashReportSpooler.tryAcquire(fullKey, now));
    }
    assertFalse(CrashReportSpooler.tryAcquire(fullKey, now));
    assertTrue(CrashReportSpooler.tryAcquire(freshKey, now));
  }

  @Test
  void throttleKeyForNullIsUnknown() {
    assertEquals("unknown", CrashReportSpooler.throttleKeyFor(null));
    assertEquals(
        IllegalStateException.class.getName(),
        CrashReportSpooler.throttleKeyFor(new IllegalStateException("boom")));
  }

  @Test
  void throttleFailsOpenOnNullKey() {
    assertTrue(CrashReportSpooler.tryAcquire(null));
    assertTrue(CrashReportSpooler.tryAcquire(null, System.currentTimeMillis()));
  }

  @Test
  void presenceHeartbeatUsesOnlyAnEphemeralSessionId() {
    String sessionId =
        JsonParser.parseString(LivePresenceHeartbeat.payload())
            .getAsJsonObject()
            .get("session_id")
            .getAsString();
    assertTrue(sessionId.matches("[0-9a-f]{32}"));
  }

  @Test
  void recordSyncNeverThrowsWithoutStartedInstance() {
    assertDoesNotThrow(() -> CrashReportSpooler.recordSync(null));
  }

  @Test
  void recordSyncSpoolsValidCrashJsonWithoutStartedInstance() {
    Set<String> before = listReportNames();
    assertDoesNotThrow(
        () -> CrashReportSpooler.recordSync(new IllegalStateException("sync probe")));
    Set<String> newcomers = listReportNames();
    newcomers.removeAll(before);
    try {
      assertFalse(newcomers.isEmpty());
      for (String name : newcomers) {
        JsonObject json =
            JsonParser.parseString(Files.readString(spoolDir().resolve(name))).getAsJsonObject();
        // The test JVM runs with fw.running.from.distribution=false, a dev run.
        String runTag =
            CrashReportSpooler.runTag(
                System.getProperty("fw.running.from.distribution"),
                CommonUtils.isRunningFromIntelliJ());
        assertEquals(
            runTag + IllegalStateException.class.getName(),
            json.get("exception_class").getAsString());
        assertFalse(json.toString().contains("sync probe"));
        assertTrue(json.get("report_nonce").getAsString().matches("[0-9a-f]{32}"));
      }
    } catch (AssertionError e) {
      throw e;
    } catch (Exception e) {
      fail("Unable to read spooled sync crash report");
    } finally {
      newcomers.forEach(CrashReportSpoolerTest::deleteReportQuietly);
    }
  }

  private static Path spoolDir() {
    return new File(CommonUtils.getUserSettingsDir(), "crash-reports").toPath();
  }

  private static Set<String> listReportNames() {
    try {
      Path spoolDir = spoolDir();
      if (!Files.isDirectory(spoolDir)) {
        return new HashSet<>();
      }
      try (var files = Files.list(spoolDir)) {
        return files
            .filter(path -> path.getFileName().toString().endsWith(".json"))
            .map(path -> path.getFileName().toString())
            .collect(Collectors.toSet());
      }
    } catch (Exception ignored) {
      return new HashSet<>();
    }
  }

  private static void deleteReportQuietly(String name) {
    try {
      Files.deleteIfExists(spoolDir().resolve(name));
    } catch (Exception ignored) {
    }
  }

  /**
   * frostwire/frostwire#1395: building a report can run the Telluride launcher. The About window's
   * test report built it on the EDT; every entry point now builds it on the uploader thread.
   */
  @Test
  void reportsAreBuiltOnTheUploaderThreadNotTheCaller() throws Exception {
    Path directory = Files.createTempDirectory("crash-report-thread");
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<Thread> lookupThread = new AtomicReference<>();
    CountDownLatch looked = new CountDownLatch(1);
    CrashReportSpooler spooler =
        new CrashReportSpooler(
            directory.toFile(),
            null,
            "7.0.4",
            331,
            "",
            () -> {
              lookupThread.set(Thread.currentThread());
              looked.countDown();
              try {
                release.await(10, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return "47";
            });
    Field instance = CrashReportSpooler.class.getDeclaredField("INSTANCE");
    instance.setAccessible(true);
    Object previous = instance.get(null);
    instance.set(null, spooler);
    try {
      long start = System.nanoTime();
      CrashReportSpooler.record(new ReportThreadProbe());
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;

      assertTrue(elapsedMs < 2_000, "record() waited for the report to be built: " + elapsedMs);
      assertTrue(looked.await(10, TimeUnit.SECONDS), "the report was never built");
      assertEquals("CrashReportUploader", lookupThread.get().getName());
    } finally {
      release.countDown();
      instance.set(null, previous);
    }
  }

  private static final class ReportThreadProbe extends RuntimeException {}
}
