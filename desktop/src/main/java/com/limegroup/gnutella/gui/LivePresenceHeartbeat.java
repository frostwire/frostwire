/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.google.gson.Gson;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Sends an anonymous, memory-only presence heartbeat while FrostWire is running.
 *
 * <p>The heartbeat carries a session id that is random per launch and never stored, this build's
 * version, and how many crash reports this session has recorded. Icebase uses it for its live
 * counters and for release metrics (active sessions and crash-free sessions per release). There is
 * no installation or user identifier, and crash reports carry no session id.
 */
public final class LivePresenceHeartbeat {
  private static final Logger LOG = Logger.getLogger(LivePresenceHeartbeat.class);
  private static final Gson GSON = new Gson();
  private static final String ENDPOINT = "https://icebase.frostwire.com/presence.php";
  private static final long INTERVAL_SECONDS = 60;
  private static final int TIMEOUT_MILLIS = 6000;

  /** Icebase's limit for crash_reports. */
  static final int MAX_CRASH_REPORTS = 1000;

  /** What Icebase accepts for app_version and app_build. */
  private static final Pattern RELEASE_TOKEN = Pattern.compile("[A-Za-z0-9._-]{1,32}");

  private static final ScheduledExecutorService SCHEDULER =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "LivePresenceHeartbeat");
            thread.setDaemon(true);
            return thread;
          });
  private static final String SESSION_ID = newSessionId();
  private static final AtomicInteger CRASH_REPORTS = new AtomicInteger();
  private static final AtomicBoolean SUCCESS_LOGGED = new AtomicBoolean();
  private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
  private static volatile HttpClient client;

  private LivePresenceHeartbeat() {}

  public static void start() {
    if (client != null) {
      return;
    }
    synchronized (LivePresenceHeartbeat.class) {
      if (client != null) {
        return;
      }
      try {
        HttpClient http = HttpClientFactory.newInstance(HttpClientFactory.HttpContext.MISC);
        SCHEDULER.scheduleAtFixedRate(() -> send(http), 0, INTERVAL_SECONDS, TimeUnit.SECONDS);
        client = http;
        LOG.info("Anonymous live presence heartbeat started");
      } catch (Throwable ignored) {
        LOG.info("Unable to initialize anonymous live presence heartbeat", ignored);
      }
    }
  }

  /**
   * Counts a crash report this session recorded and sends a heartbeat right away, off the calling
   * thread, so the session counts as crashed even if the app doesn't live until the next minute's
   * heartbeat.
   */
  static void crashRecorded() {
    countCrash();
    HttpClient http = client;
    if (http != null) {
      try {
        SCHEDULER.execute(() -> send(http));
      } catch (Throwable ignored) {
        // The scheduler is gone; the count goes out with the next heartbeat, if any.
      }
    }
  }

  static int crashReports() {
    return CRASH_REPORTS.get();
  }

  private static void countCrash() {
    CRASH_REPORTS.getAndUpdate(count -> Math.min(count + 1, MAX_CRASH_REPORTS));
  }

  static String payload() {
    return payload(
        FrostWireUtils.getFrostWireVersion(),
        Integer.toString(FrostWireUtils.getBuildNumber()),
        CRASH_REPORTS.get());
  }

  /**
   * The heartbeat body. A version or build Icebase would reject falls back to the session id alone,
   * so a malformed version string never costs the live counters.
   */
  static String payload(String appVersion, String appBuild, int crashReports) {
    if (appVersion == null
        || appBuild == null
        || !RELEASE_TOKEN.matcher(appVersion).matches()
        || !RELEASE_TOKEN.matcher(appBuild).matches()
        || "unknown".equals(appBuild)) {
      return GSON.toJson(new SessionPayload(SESSION_ID));
    }
    return GSON.toJson(
        new ReleasePayload(
            SESSION_ID,
            appVersion,
            appBuild,
            Math.max(0, Math.min(crashReports, MAX_CRASH_REPORTS))));
  }

  private static void send(HttpClient http) {
    try {
      http.post(ENDPOINT, TIMEOUT_MILLIS, payload(), "application/json; charset=utf-8");
      if (SUCCESS_LOGGED.compareAndSet(false, true)) {
        LOG.info("Anonymous live presence heartbeat sent to Icebase");
      }
    } catch (Throwable ignored) {
      if (FAILURE_LOGGED.compareAndSet(false, true)) {
        LOG.info("Unable to send anonymous live presence heartbeat", ignored);
      }
    }
  }

  private static String newSessionId() {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      result.append(String.format("%02x", value & 0xff));
    }
    return result.toString();
  }

  private static final class SessionPayload {
    private final String session_id;

    private SessionPayload(String sessionId) {
      this.session_id = sessionId;
    }
  }

  private static final class ReleasePayload {
    private final String session_id;
    private final String platform = "desktop";
    private final String app_version;
    private final String app_build;
    private final int crash_reports;

    private ReleasePayload(String sessionId, String appVersion, String appBuild, int crashReports) {
      this.session_id = sessionId;
      this.app_version = appVersion;
      this.app_build = appBuild;
      this.crash_reports = crashReports;
    }
  }
}
