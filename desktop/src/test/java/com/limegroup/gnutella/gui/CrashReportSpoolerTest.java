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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.limewire.util.CommonUtils;

class CrashReportSpoolerTest {
  @Test
  void writesOnlyAllowlistedAnonymousCrashFields() throws Exception {
    Path directory = Files.createTempDirectory("frostwire-crash-spool");
    CrashReportSpooler spooler = new CrashReportSpooler(directory.toFile(), null, "7.0.4", 331);

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
  void capsSpoolAndCreatesFreshNoncePerReport() throws Exception {
    Path directory = Files.createTempDirectory("frostwire-crash-spool");
    CrashReportSpooler spooler = new CrashReportSpooler(directory.toFile(), null, "7.0.4", 331);

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
    assertDoesNotThrow(() -> CrashReportSpooler.recordSync(new IllegalStateException("sync probe")));
    Set<String> newcomers = listReportNames();
    newcomers.removeAll(before);
    try {
      assertFalse(newcomers.isEmpty());
      for (String name : newcomers) {
        JsonObject json =
            JsonParser.parseString(Files.readString(spoolDir().resolve(name))).getAsJsonObject();
        assertEquals(
            IllegalStateException.class.getName(), json.get("exception_class").getAsString());
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
}
