package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
  void presenceHeartbeatUsesOnlyAnEphemeralSessionId() {
    String sessionId =
        JsonParser.parseString(LivePresenceHeartbeat.payload())
            .getAsJsonObject()
            .get("session_id")
            .getAsString();
    assertTrue(sessionId.matches("[0-9a-f]{32}"));
  }
}
