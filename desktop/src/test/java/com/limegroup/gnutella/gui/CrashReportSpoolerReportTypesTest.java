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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrashReportSpoolerReportTypesTest {
  @TempDir Path tempDir;

  @Test
  void strictmodeReportHasRequiredFieldsAndValidEnums() {
    File spoolDir = new File(tempDir.toFile(), "strictmode");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331);

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
                "violation_class",
                "violation_count",
                "report_nonce"));

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
  void watchdogReportHasRequiredFieldsAndValidEnums() {
    File spoolDir = new File(tempDir.toFile(), "watchdog");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331);

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
                "missed_heartbeats",
                "report_nonce"));

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
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331);

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
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331);

    spooler.spoolStrictMode(null, 1);

    assertEquals("unknown", spooledJson(spoolDir.toPath()).get("violation_class").getAsString());
  }

  @Test
  void countsAreClampedToValidatorRanges() {
    CrashReportSpooler lowStrict =
        new CrashReportSpooler(new File(tempDir.toFile(), "low-strict"), null, "7.0.4", 331);
    CrashReportSpooler lowWatch =
        new CrashReportSpooler(new File(tempDir.toFile(), "low-watch"), null, "7.0.4", 331);
    CrashReportSpooler highStrict =
        new CrashReportSpooler(new File(tempDir.toFile(), "high-strict"), null, "7.0.4", 331);
    CrashReportSpooler highWatch =
        new CrashReportSpooler(new File(tempDir.toFile(), "high-watch"), null, "7.0.4", 331);

    lowStrict.spoolStrictMode("com.example.BlockingCall", 0);
    lowWatch.spoolWatchdog(0);
    highStrict.spoolStrictMode("com.example.BlockingCall", 5000);
    highWatch.spoolWatchdog(500);

    assertEquals(
        1, spooledJson(new File(tempDir.toFile(), "low-strict").toPath()).get("violation_count").getAsInt());
    assertEquals(
        1, spooledJson(new File(tempDir.toFile(), "low-watch").toPath()).get("missed_heartbeats").getAsInt());
    assertEquals(
        1000,
        spooledJson(new File(tempDir.toFile(), "high-strict").toPath()).get("violation_count").getAsInt());
    assertEquals(
        100,
        spooledJson(new File(tempDir.toFile(), "high-watch").toPath()).get("missed_heartbeats").getAsInt());
  }

  @Test
  void strictmodeReportsCarryFreshNonces() {
    File spoolDir = new File(tempDir.toFile(), "nonces");
    CrashReportSpooler spooler = new CrashReportSpooler(spoolDir, null, "7.0.4", 331);

    spooler.spoolStrictMode("com.example.First", 1);
    spooler.spoolWatchdog(1);

    String first = spooledJson(spoolDir.toPath(), 0).get("report_nonce").getAsString();
    String second = spooledJson(spoolDir.toPath(), 1).get("report_nonce").getAsString();
    assertTrue(first.matches("[0-9a-f]{32}"));
    assertTrue(second.matches("[0-9a-f]{32}"));
    assertNotEquals(first, second);
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
