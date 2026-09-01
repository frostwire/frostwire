package com.limegroup.gnutella.gui;

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.google.gson.Gson;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sends an anonymous, memory-only presence heartbeat while FrostWire is running. */
public final class LivePresenceHeartbeat {
  private static final Logger LOG = Logger.getLogger(LivePresenceHeartbeat.class);
  private static final Gson GSON = new Gson();
  private static final String ENDPOINT = "https://icebase.frostwire.com/presence.php";
  private static final long INTERVAL_SECONDS = 60;
  private static final ScheduledExecutorService SCHEDULER =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "LivePresenceHeartbeat");
            thread.setDaemon(true);
            return thread;
          });
  private static final String SESSION_ID = newSessionId();
  private static final AtomicBoolean SUCCESS_LOGGED = new AtomicBoolean();
  private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
  private static volatile boolean started;

  private LivePresenceHeartbeat() {}

  public static void start() {
    if (started) {
      return;
    }
    synchronized (LivePresenceHeartbeat.class) {
      if (started) {
        return;
      }
      try {
        HttpClient client = HttpClientFactory.newInstance(HttpClientFactory.HttpContext.MISC);
        SCHEDULER.scheduleAtFixedRate(
            () -> send(client), 0, INTERVAL_SECONDS, TimeUnit.SECONDS);
        started = true;
        LOG.info("Anonymous live presence heartbeat started");
      } catch (Throwable ignored) {
        LOG.info("Unable to initialize anonymous live presence heartbeat", ignored);
      }
    }
  }

  static String payload() {
    return GSON.toJson(new PresencePayload(SESSION_ID));
  }

  private static void send(HttpClient client) {
    try {
      client.post(ENDPOINT, 6000, payload(), "application/json; charset=utf-8");
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

  private static final class PresencePayload {
    private final String session_id;

    private PresencePayload(String sessionId) {
      this.session_id = sessionId;
    }
  }
}
