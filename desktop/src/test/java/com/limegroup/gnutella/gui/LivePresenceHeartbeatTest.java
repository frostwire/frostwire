/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The presence heartbeat feeds Icebase's release metrics. Icebase rejects unknown fields and
 * partial releases, and a rejected heartbeat also drops the session from the live counters, so the
 * body must be exactly the session id alone or the session id with the full release.
 */
class LivePresenceHeartbeatTest {

  private static JsonObject parse(String json) {
    return JsonParser.parseString(json).getAsJsonObject();
  }

  @Test
  void heartbeatCarriesThisReleaseAndNothingIdentifying() {
    JsonObject body = parse(LivePresenceHeartbeat.payload());
    assertEquals(
        Set.of("session_id", "platform", "app_version", "app_build", "crash_reports"),
        body.keySet());
    assertTrue(body.get("session_id").getAsString().matches("[0-9a-f]{32}"));
    assertEquals("desktop", body.get("platform").getAsString());
    assertEquals(FrostWireUtils.getFrostWireVersion(), body.get("app_version").getAsString());
    assertEquals(
        Integer.toString(FrostWireUtils.getBuildNumber()), body.get("app_build").getAsString());
  }

  @Test
  void recordedCrashesAreCountedEvenWhenTheReportIsThrottled() {
    int before = LivePresenceHeartbeat.crashReports();
    IllegalStateException crash = new IllegalStateException("presence crash count probe");
    // The second identical crash within the throttle window is not spooled, but still counts.
    CrashReportSpooler.record(crash);
    CrashReportSpooler.record(crash);
    assertEquals(before + 2, LivePresenceHeartbeat.crashReports());
    assertEquals(
        before + 2, parse(LivePresenceHeartbeat.payload()).get("crash_reports").getAsInt());
  }

  @Test
  void fatalCrashesAreCounted() {
    int before = LivePresenceHeartbeat.crashReports();
    CrashReportSpooler.recordSync(new IllegalStateException("presence fatal crash probe"));
    assertEquals(before + 1, LivePresenceHeartbeat.crashReports());
  }

  @Test
  void crashCountIsCappedAtIcebasesLimit() {
    JsonObject body =
        parse(
            LivePresenceHeartbeat.payload(
                "7.1.3", "336", LivePresenceHeartbeat.MAX_CRASH_REPORTS + 5));
    assertEquals(LivePresenceHeartbeat.MAX_CRASH_REPORTS, body.get("crash_reports").getAsInt());
  }

  @Test
  void aVersionIcebaseWouldRejectFallsBackToTheSessionIdAlone() {
    for (String[] release :
        new String[][] {
          {"7.1.3 beta", "336"},
          {"7.1.3", "unknown"},
          {null, "336"},
          {"7.1.3", ""},
          {"7.1.3", "x".repeat(33)}
        }) {
      assertEquals(
          Set.of("session_id"),
          parse(LivePresenceHeartbeat.payload(release[0], release[1], 0)).keySet(),
          String.join("/", String.valueOf(release[0]), String.valueOf(release[1])));
    }
  }
}
