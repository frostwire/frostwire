/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.gui.PerformanceMode;
import com.frostwire.util.JdkWarmUp;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrashReportSpoolerReportTypesTest {
  @TempDir Path tempDir;

  @Test
  void strictmodeReportCarriesWhereTheCpuWentWhenMeasured() {
    File spoolDir = new File(tempDir.toFile(), "strictmode-cpu");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.1.4", 337, "");

    spooler.spoolStrictMode(
        "java.awt.event.MouseEvent#502",
        1,
        new Throwable().getStackTrace(),
        new CrashReportSpooler.GcWindow(0, 0, 2500),
        new StallCpu.Window(
            12,
            4900,
            100,
            180,
            StallCpu.busiest(
                new String[] {"SearchManager-crawlExecutor-thread-1", "AWT-EventQueue-0"},
                new long[] {2_210_000_000L, 12_000_000L})));

    JsonObject json = spooledJson(spoolDir.toPath());
    assertEquals(12, json.get("edt_cpu_ms").getAsInt());
    assertEquals(4900, json.get("process_cpu_ms").getAsInt());
    assertEquals(100, json.get("system_cpu_pct").getAsInt());
    assertEquals(180, json.get("phys_free_mb").getAsInt());
    JsonObject busiest = json.getAsJsonArray("cpu_threads").get(0).getAsJsonObject();
    // The server accepts exactly these keys, in this order.
    assertEquals(List.of("name", "cpu_ms"), List.copyOf(busiest.keySet()));
    assertEquals("SearchManager-crawlExecutor-thread-N", busiest.get("name").getAsString());
    assertEquals(2210, busiest.get("cpu_ms").getAsInt());
  }

  /** Icebase compares reports by performance mode (frostwire/frostwire#1555). */
  @Test
  void everyReportCarriesThePerformanceMode() {
    try {
      PerformanceMode.resolve(
          PerformanceMode.Choice.AUTOMATIC, new PerformanceMode.MachineFacts(2, 2048, 300, 90_000));
      File spoolDir = new File(tempDir.toFile(), "performance-mode");
      CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.1.5", 338, "");
      spooler.spoolStrictMode("com.example.BlockingCall", 1);
      JsonObject json = spooledJson(spoolDir.toPath());
      assertEquals("lite", json.get("performance_mode").getAsString());
      assertTrue(json.get("performance_mode_auto").getAsBoolean());

      PerformanceMode.resolve(PerformanceMode.Choice.FULL, null);
      File fullDir = new File(tempDir.toFile(), "performance-mode-full");
      new CrashReportSpooler(fullDir, null, "7.1.5", 338, "").spoolWatchdog(3);
      JsonObject full = spooledJson(fullDir.toPath());
      assertEquals("full", full.get("performance_mode").getAsString());
      assertFalse(full.get("performance_mode_auto").getAsBoolean());
    } finally {
      PerformanceMode.resolve(PerformanceMode.Choice.FULL, null);
    }
  }

  /**
   * The first text warm-up failure explains a later NoClassDefFoundError for sun.font.HBShaper, so
   * every report type carries it when there was one (frostwire/frostwire#1587).
   */
  @Test
  @SuppressWarnings("unchecked")
  void everyReportCarriesTheFirstTextWarmUpFailure() throws Exception {
    java.lang.reflect.Field holder = JdkWarmUp.class.getDeclaredField("FIRST_TEXT_FAILURE");
    holder.setAccessible(true);
    java.util.concurrent.atomic.AtomicReference<String> failure =
        (java.util.concurrent.atomic.AtomicReference<String>) holder.get(null);
    String before = failure.get();
    try {
      failure.set(null);
      File none = new File(tempDir.toFile(), "no-warmup-failure");
      new CrashReportSpooler(none, null, "7.1.5", 338, "").spoolStrictMode("a.B", 1);
      assertFalse(spooledJson(none.toPath()).has("text_warmup_failure"));

      failure.set("java.lang.ExceptionInInitializerError <- java.lang.UnsatisfiedLinkError: x");
      File strict = new File(tempDir.toFile(), "warmup-failure-strict");
      new CrashReportSpooler(strict, null, "7.1.5", 338, "").spoolStrictMode("a.B", 1);
      File crash = new File(tempDir.toFile(), "warmup-failure-crash");
      new CrashReportSpooler(crash, null, "7.1.5", 338, "")
          .spool(new NoClassDefFoundError("sun/font/HBShaper"));
      File watchdog = new File(tempDir.toFile(), "warmup-failure-watchdog");
      new CrashReportSpooler(watchdog, null, "7.1.5", 338, "").spoolWatchdog(3);

      for (File dir : new File[] {strict, crash, watchdog}) {
        assertEquals(
            "java.lang.ExceptionInInitializerError <- java.lang.UnsatisfiedLinkError: x",
            spooledJson(dir.toPath()).get("text_warmup_failure").getAsString());
      }
    } finally {
      failure.set(before);
    }
  }

  @Test
  void everyReportCarriesTheAppUptime() {
    File spoolDir = new File(tempDir.toFile(), "uptime");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.1.4", 337, "");

    spooler.spoolStrictMode("com.example.BlockingCall", 1);

    JsonObject json = spooledJson(spoolDir.toPath());
    assertTrue(json.getAsJsonPrimitive("uptime_s").isNumber());
    assertTrue(json.get("uptime_s").getAsLong() >= 0);
    // No warm-up ran in this test JVM, so its state is left out rather than reported as running.
    assertFalse(json.has("warmup_done"));
    assertFalse(json.has("warmup_ms"));
    assertFalse(json.has("edt_cpu_ms"));
  }

  @Test
  void strictmodeReportCarriesItsGcWindowWhenMeasured() {
    File spoolDir = new File(tempDir.toFile(), "strictmode-gc");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331, "");

    spooler.spoolStrictMode(
        "java.awt.event.InvocationEvent#1200",
        1,
        new Throwable().getStackTrace(),
        new CrashReportSpooler.GcWindow(41, 2380, 2500));

    JsonObject json = spooledJson(spoolDir.toPath());
    assertEquals(41, json.get("gc_count").getAsInt());
    assertEquals(2380, json.get("gc_time_ms").getAsInt());
    assertEquals(2500, json.get("gc_window_ms").getAsInt());
    assertTrue(json.getAsJsonPrimitive("heap_used_mb").isNumber());
  }

  @Test
  void strictmodeReportHasRequiredFieldsAndValidEnums() {
    File spoolDir = new File(tempDir.toFile(), "strictmode");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331, "");

    spooler.spoolStrictMode("com.example.BlockingCall", 3);

    JsonObject json = spooledJson(spoolDir.toPath());
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
                "thread_count",
                "thread_peak",
                "thread_groups",
                "uptime_s",
                "performance_mode",
                "performance_mode_auto",
                "violation_class",
                "violation_count",
                "report_nonce"));

    // Left out when the JVM uses the OS default thread stack size (Windows).
    if (json.has("thread_stack_mb")) {
      expected.add("thread_stack_mb");
    }
    assertEquals(expected, json.keySet());
    assertEquals(1, json.get("schema_version").getAsInt());
    assertEquals("strictmode", json.get("report_type").getAsString());
    assertEquals("desktop", json.get("platform").getAsString());
    assertEquals("7.0.4", json.get("app_version").getAsString());
    assertTrue(json.getAsJsonPrimitive("app_build").isString());
    assertTrue(json.getAsJsonPrimitive("cpu_count").isString());
    assertTrue(json.getAsJsonPrimitive("max_memory_mb").isString());
    assertTrue(json.get("memory_bucket").getAsString().matches("lt_128m|128_512m|512m_2g|gte_2g"));
    assertEquals("com.example.BlockingCall", json.get("violation_class").getAsString());
    assertTrue(json.getAsJsonPrimitive("violation_count").isNumber());
    assertEquals(3, json.get("violation_count").getAsInt());
    assertTrue(json.get("report_nonce").getAsString().matches("[0-9a-f]{32}"));
    assertFalse(json.has("exception_class"));
    assertFalse(json.has("stack_frames"));
    assertFalse(json.has("missed_heartbeats"));
  }

  @Test
  void strictmodeReportPreservesCapturedEdtStack() {
    File spoolDir = new File(tempDir.toFile(), "edt-stack");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.1.1", 333, "");
    StackTraceElement[] captured = {
      new StackTraceElement("java.lang.Object", "wait0", "Object.java", -2),
      new StackTraceElement("com.frostwire.Example", "blockingCall", "Example.java", 42),
      new StackTraceElement("java.awt.EventDispatchThread", "run", "EventDispatchThread.java", -1)
    };

    spooler.spoolStrictMode("java.awt.event.FocusEvent#1004", 1, captured);

    var frames = spooledJson(spoolDir.toPath()).getAsJsonArray("stack_frames");
    assertEquals(3, frames.size());
    assertEquals("java.lang.Object", frames.get(0).getAsJsonObject().get("class").getAsString());
    assertEquals(-2, frames.get(0).getAsJsonObject().get("line").getAsInt());
    assertEquals("blockingCall", frames.get(1).getAsJsonObject().get("method").getAsString());
    assertEquals(42, frames.get(1).getAsJsonObject().get("line").getAsInt());
    assertEquals(-1, frames.get(2).getAsJsonObject().get("line").getAsInt());
    for (var frame : frames) {
      assertEquals(Set.of("class", "method", "line"), frame.getAsJsonObject().keySet());
    }
  }

  @Test
  void strictmodeStackUsesExistingFrameLimit() {
    File spoolDir = new File(tempDir.toFile(), "long-stack");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.1.1", 333, "");
    StackTraceElement[] captured = new StackTraceElement[100];
    Arrays.fill(
        captured, new StackTraceElement("com.frostwire.Example", "run", "Example.java", 42));

    spooler.spoolStrictMode("java.awt.event.FocusEvent#1004", 1, captured);

    assertEquals(64, spooledJson(spoolDir.toPath()).getAsJsonArray("stack_frames").size());
  }

  @Test
  void watchdogReportHasRequiredFieldsAndValidEnums() {
    File spoolDir = new File(tempDir.toFile(), "watchdog");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331, "");

    spooler.spoolWatchdog(2);

    JsonObject json = spooledJson(spoolDir.toPath());
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
                "thread_count",
                "thread_peak",
                "thread_groups",
                "uptime_s",
                "performance_mode",
                "performance_mode_auto",
                "missed_heartbeats",
                "report_nonce"));

    // Left out when the JVM uses the OS default thread stack size (Windows).
    if (json.has("thread_stack_mb")) {
      expected.add("thread_stack_mb");
    }
    assertEquals(expected, json.keySet());
    assertEquals(1, json.get("schema_version").getAsInt());
    assertEquals("watchdog", json.get("report_type").getAsString());
    assertEquals("desktop", json.get("platform").getAsString());
    assertTrue(json.get("memory_bucket").getAsString().matches("lt_128m|128_512m|512m_2g|gte_2g"));
    assertTrue(json.getAsJsonPrimitive("missed_heartbeats").isNumber());
    assertEquals(2, json.get("missed_heartbeats").getAsInt());
    assertTrue(json.get("report_nonce").getAsString().matches("[0-9a-f]{32}"));
    assertFalse(json.has("exception_class"));
    assertFalse(json.has("stack_frames"));
    assertFalse(json.has("violation_class"));
    assertFalse(json.has("violation_count"));
  }

  @Test
  void violationClassIsSanitizedToAsciiAndTruncated() {
    File spoolDir = new File(tempDir.toFile(), "sanitize");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331, "");

    StringBuilder raw = new StringBuilder();
    for (int i = 0; i < 300; i++) {
      raw.append('x');
    }
    raw.append("caf\u00e9\u0007");
    spooler.spoolStrictMode(raw.toString(), 1);

    String violationClass = spooledJson(spoolDir.toPath()).get("violation_class").getAsString();
    assertEquals(200, violationClass.length());
    assertTrue(violationClass.matches("[\\x20-\\x7e]*"));
    assertFalse(violationClass.contains("\u00e9"));
  }

  @Test
  void nullViolationClassFallsBackToUnknown() {
    File spoolDir = new File(tempDir.toFile(), "null-class");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331, "");

    spooler.spoolStrictMode(null, 1);

    assertEquals("unknown", spooledJson(spoolDir.toPath()).get("violation_class").getAsString());
  }

  @Test
  void countsAreClampedToValidatorRanges() {
    CrashReportSpooler lowStrict =
        new CrashReportSpooler(new File(tempDir.toFile(), "low-strict"), null, "7.0.4", 331, "");
    CrashReportSpooler lowWatch =
        new CrashReportSpooler(new File(tempDir.toFile(), "low-watch"), null, "7.0.4", 331, "");
    CrashReportSpooler highStrict =
        new CrashReportSpooler(new File(tempDir.toFile(), "high-strict"), null, "7.0.4", 331, "");
    CrashReportSpooler highWatch =
        new CrashReportSpooler(new File(tempDir.toFile(), "high-watch"), null, "7.0.4", 331, "");

    lowStrict.spoolStrictMode("com.example.BlockingCall", 0);
    lowWatch.spoolWatchdog(0);
    highStrict.spoolStrictMode("com.example.BlockingCall", 5000);
    highWatch.spoolWatchdog(500);

    assertEquals(
        1,
        spooledJson(new File(tempDir.toFile(), "low-strict").toPath())
            .get("violation_count")
            .getAsInt());
    assertEquals(
        1,
        spooledJson(new File(tempDir.toFile(), "low-watch").toPath())
            .get("missed_heartbeats")
            .getAsInt());
    assertEquals(
        1000,
        spooledJson(new File(tempDir.toFile(), "high-strict").toPath())
            .get("violation_count")
            .getAsInt());
    assertEquals(
        100,
        spooledJson(new File(tempDir.toFile(), "high-watch").toPath())
            .get("missed_heartbeats")
            .getAsInt());
  }

  @Test
  void strictmodeReportsCarryFreshNonces() {
    File spoolDir = new File(tempDir.toFile(), "nonces");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331, "");

    spooler.spoolStrictMode("com.example.First", 1);
    spooler.spoolWatchdog(1);

    String first = spooledJson(spoolDir.toPath(), 0).get("report_nonce").getAsString();
    String second = spooledJson(spoolDir.toPath(), 1).get("report_nonce").getAsString();
    assertTrue(first.matches("[0-9a-f]{32}"));
    assertTrue(second.matches("[0-9a-f]{32}"));
    assertNotEquals(first, second);
  }

  private JsonObject crashJson(String name, Throwable problem) {
    File dir = new File(tempDir.toFile(), name);
    new CrashReportSpooler(dir, null, "7.1.5", 338, "").spool(problem);
    return spooledJson(dir.toPath());
  }

  private static Throwable chain(int depth, Throwable leaf) {
    Throwable current = leaf;
    for (int i = 0; i < depth; i++) {
      current = new RuntimeException("wrapper " + i + " /Users/someone/secret", current);
    }
    return current;
  }

  /** The real failure behind a wrapper is lost without it (frostwire/frostwire#1576). */
  @Test
  void crashReportCarriesTheRootCauseClassAndFramesButNotItsMessage() {
    UnsatisfiedLinkError root = new UnsatisfiedLinkError("/Users/someone/lib.dylib not found");
    root.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("com.frostwire.Native", "load", "Native.java", 7),
          new StackTraceElement("com.frostwire.Boot", "main", "Boot.java", 9)
        });
    Throwable outer =
        new RuntimeException("Unable to load theme", new IllegalStateException("mid", root));

    JsonObject json = crashJson("cause-chain", outer);

    assertEquals(RuntimeException.class.getName(), json.get("exception_class").getAsString());
    assertEquals(UnsatisfiedLinkError.class.getName(), json.get("cause_class").getAsString());
    var frames = json.getAsJsonArray("cause_frames");
    assertEquals(2, frames.size());
    assertEquals(Set.of("class", "method", "line"), frames.get(0).getAsJsonObject().keySet());
    assertEquals(
        "com.frostwire.Native", frames.get(0).getAsJsonObject().get("class").getAsString());
    assertEquals("load", frames.get(0).getAsJsonObject().get("method").getAsString());
    assertEquals(7, frames.get(0).getAsJsonObject().get("line").getAsInt());
    assertFalse(json.toString().contains("someone"));
    assertFalse(json.toString().contains("Unable to load theme"));
  }

  @Test
  void crashReportWithoutACauseLeavesOutTheCauseFields() {
    JsonObject json = crashJson("no-cause", new IllegalStateException("alone"));
    assertFalse(json.has("cause_class"));
    assertFalse(json.has("cause_frames"));
  }

  @Test
  void aCauseCycleTerminatesAtTheLastNewCause() {
    RuntimeException a = new RuntimeException("a");
    IllegalStateException b = new IllegalStateException("b", a);
    a.initCause(b);

    JsonObject json = crashJson("cycle", a);

    assertEquals(IllegalStateException.class.getName(), json.get("cause_class").getAsString());
  }

  @Test
  void aSelfCausedExceptionHasNoCauseFields() {
    // initCause(this) is illegal; a cycle through itself is only reachable via a subclass.
    Throwable looping =
        new RuntimeException("loop") {
          @Override
          public synchronized Throwable getCause() {
            return this;
          }
        };
    assertFalse(crashJson("self-cause", looping).has("cause_class"));
  }

  @Test
  void aDeepChainStopsAtTenHops() {
    Throwable leaf = new Error("leaf");
    // 15 wrappers above the leaf: the 10th cause below the outer one is a wrapper, not the leaf.
    Throwable outer = chain(15, leaf);
    Throwable expected = outer;
    for (int i = 0; i < 10; i++) {
      expected = expected.getCause();
    }
    assertNotEquals(leaf, expected);
    expected.setStackTrace(
        new StackTraceElement[] {new StackTraceElement("com.frostwire.Tenth", "run", "T.java", 1)});

    JsonObject json = crashJson("deep", outer);

    assertEquals(RuntimeException.class.getName(), json.get("cause_class").getAsString());
    assertEquals(
        "com.frostwire.Tenth",
        json.getAsJsonArray("cause_frames").get(0).getAsJsonObject().get("class").getAsString());
  }

  @Test
  void causeFramesUseTheStackFramesCap() {
    Error root = new Error("deep stack");
    StackTraceElement[] many = new StackTraceElement[100];
    Arrays.fill(many, new StackTraceElement("com.frostwire.Example", "run", "Example.java", 42));
    root.setStackTrace(many);

    JsonObject json = crashJson("cap", new RuntimeException("outer", root));

    assertEquals(64, json.getAsJsonArray("cause_frames").size());
  }

  @Test
  void causeClassIsPrintableAsciiAtMostTwoHundredChars() {
    Throwable root = new Exception() {};
    JsonObject json = crashJson("ascii", new RuntimeException(root));
    String cause = json.get("cause_class").getAsString();
    assertTrue(cause.length() <= 200);
    assertTrue(cause.chars().allMatch(c -> c >= 0x20 && c <= 0x7E));
    assertEquals(root.getClass().getName(), cause);
  }

  @Test
  void strictmodeAndWatchdogReportsNeverCarryTheCauseFields() {
    File strict = new File(tempDir.toFile(), "cause-strict");
    new CrashReportSpooler(strict, null, "7.1.5", 338, "")
        .spoolStrictMode("a.B", 1, new StackTraceElement[0]);
    File watchdog = new File(tempDir.toFile(), "cause-watchdog");
    new CrashReportSpooler(watchdog, null, "7.1.5", 338, "").spoolWatchdog(3);
    for (File dir : new File[] {strict, watchdog}) {
      JsonObject json = spooledJson(dir.toPath());
      assertFalse(json.has("cause_class"));
      assertFalse(json.has("cause_frames"));
    }
  }

  private static JsonObject spooledJson(Path directory) {
    return spooledJson(directory, 0);
  }

  private static JsonObject spooledJson(Path directory, int index) {
    try (Stream<Path> files = Files.list(directory)) {
      Path report =
          files
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .sorted()
              .skip(index)
              .findFirst()
              .orElseThrow(() -> new AssertionError("no spooled report at index " + index));
      return JsonParser.parseString(Files.readString(report)).getAsJsonObject();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
