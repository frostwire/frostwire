/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.jlibtorrent.LibTorrent;
import com.frostwire.search.telluride.TellurideBuild;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.limewire.util.CommonUtils;

/** Stores anonymous crash reports locally and uploads them outside the failing thread. */
public final class CrashReportSpooler {
  private static final Logger LOG = Logger.getLogger(CrashReportSpooler.class);
  private static final Gson GSON = new Gson();
  private static final String ENDPOINT = "https://icebase.frostwire.com/";
  private static final int MAX_REPORTS = 5;
  private static final int MAX_FRAMES = 64;
  // Per-key flood control on the Icebase path: at most THROTTLE_MAX_PER_KEY reports
  // per key per rolling THROTTLE_WINDOW_MILLIS window. Fail open on any internal error.
  private static final int THROTTLE_MAX_PER_KEY = 5;
  private static final long THROTTLE_WINDOW_MILLIS = 60L * 60L * 1000L;
  private static final int THROTTLE_MAX_KEYS = 1000;
  private static final ConcurrentHashMap<String, Slot> THROTTLE_SLOTS = new ConcurrentHashMap<>();
  private static final ExecutorService UPLOAD_QUEUE =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "CrashReportUploader");
            thread.setDaemon(true);
            return thread;
          });
  private static volatile CrashReportSpooler INSTANCE;

  private final File directory;
  private final HttpClient client;
  private final String appVersion;

  /**
   * {@link #DEV_TAG} in front of every report's class for runs outside a distribution, so their
   * reports get their own Icebase issues instead of mixing with users' (frostwire/frostwire#1368).
   */
  private final String runTag;

  private final int appBuild;

  public CrashReportSpooler(File directory, HttpClient client, String appVersion, int appBuild) {
    this(
        directory,
        client,
        appVersion,
        appBuild,
        runTag(
            System.getProperty("fw.running.from.distribution"),
            CommonUtils.isRunningFromIntelliJ()));
  }

  CrashReportSpooler(
      File directory, HttpClient client, String appVersion, int appBuild, String runTag) {
    this.directory = directory;
    this.client = client;
    this.appVersion = appVersion;
    this.appBuild = appBuild;
    this.runTag = runTag;
  }

  public static void start() {
    try {
      if (INSTANCE == null) {
        INSTANCE =
            new CrashReportSpooler(
                new File(CommonUtils.getUserSettingsDir(), "crash-reports"),
                HttpClientFactory.newInstance(HttpClientFactory.HttpContext.MISC),
                FrostWireUtils.getFrostWireVersion(),
                FrostWireUtils.getBuildNumber());
      }
      CrashReportSpooler spooler = INSTANCE;
      UPLOAD_QUEUE.execute(spooler::uploadPending);
    } catch (Throwable ignored) {
      LOG.info("Unable to initialize anonymous crash reporting", ignored);
    }
  }

  /** Queues a synthetic anonymous report for verifying the Icebase pipeline. */
  public static void submitTestReport() {
    try {
      start();
      CrashReportSpooler spooler = INSTANCE;
      if (spooler != null) {
        spooler.spool(new Throwable());
        UPLOAD_QUEUE.execute(spooler::uploadPending);
      }
    } catch (Throwable ignored) {
      LOG.info("Unable to submit anonymous test crash report", ignored);
    }
  }

  public static void record(Throwable problem) {
    try {
      String throttleKey = throttleKeyFor(problem);
      if (!tryAcquire(throttleKey)) {
        LOG.debug("Throttled anonymous crash report for " + throttleKey);
        return;
      }
      CrashReportSpooler spooler = INSTANCE;
      if (spooler != null) {
        spooler.spool(problem);
        UPLOAD_QUEUE.execute(spooler::uploadPending);
        LOG.info("Automatically queued anonymous crash report for Icebase");
      }
    } catch (Throwable ignored) {
      LOG.info("Unable to queue anonymous crash report", ignored);
    }
  }

  /**
   * Synchronously spools a crash report without requiring {@link #start()} to have run and without
   * attempting any upload. Intended for fatal paths that exit immediately after. Deliberately
   * unthrottled: a fatal report must never be dropped.
   */
  public static void recordSync(Throwable problem) {
    try {
      CrashReportSpooler spooler =
          new CrashReportSpooler(
              new File(CommonUtils.getUserSettingsDir(), "crash-reports"),
              null,
              FrostWireUtils.getFrostWireVersion(),
              FrostWireUtils.getBuildNumber());
      spooler.spool(problem);
    } catch (Throwable ignored) {
      LOG.info("Unable to synchronously spool anonymous crash report", ignored);
    }
  }

  static String throttleKeyFor(Throwable problem) {
    try {
      return problem == null ? "unknown" : problem.getClass().getName();
    } catch (Throwable ignored) {
      return "unknown";
    }
  }

  static boolean tryAcquire(String key) {
    return tryAcquire(key, System.currentTimeMillis());
  }

  static boolean tryAcquire(String key, long nowMillis) {
    try {
      String slotKey = key == null ? "unknown" : key;
      if (THROTTLE_SLOTS.size() > THROTTLE_MAX_KEYS) {
        THROTTLE_SLOTS.clear();
      }
      Slot slot =
          THROTTLE_SLOTS.computeIfAbsent(
              slotKey,
              k -> {
                Slot created = new Slot();
                created.windowStartMillis = nowMillis;
                return created;
              });
      synchronized (slot) {
        if (nowMillis < slot.windowStartMillis
            || nowMillis - slot.windowStartMillis >= THROTTLE_WINDOW_MILLIS) {
          slot.windowStartMillis = nowMillis;
          slot.count = 0;
        }
        if (slot.count < THROTTLE_MAX_PER_KEY) {
          slot.count++;
          return true;
        }
        return false;
      }
    } catch (Throwable ignored) {
      return true;
    }
  }

  private static final class Slot {
    long windowStartMillis;
    int count;
  }

  /** Queues an anonymous StrictMode violation report for Icebase. No-op until {@link #start()}. */
  public static void recordStrictMode(String violationClass, int violationCount) {
    recordStrictMode(violationClass, violationCount, null);
  }

  /** Queues the captured violating thread's stack, not the reporting thread's stack. */
  public static void recordStrictMode(
      String violationClass, int violationCount, StackTraceElement[] capturedStack) {
    recordStrictMode(violationClass, violationCount, capturedStack, null);
  }

  /** Adds the GC activity during the end of the stalled event, when it was measured. */
  public static void recordStrictMode(
      String violationClass,
      int violationCount,
      StackTraceElement[] capturedStack,
      GcWindow gcWindow) {
    try {
      if (!tryAcquire("strictmode:" + sanitizeViolationClass(violationClass))) {
        LOG.debug("Throttled anonymous strictmode report");
        return;
      }
      CrashReportSpooler spooler = INSTANCE;
      if (spooler != null) {
        spooler.spoolStrictMode(violationClass, violationCount, capturedStack, gcWindow);
        UPLOAD_QUEUE.execute(spooler::uploadPending);
        LOG.info("Automatically queued anonymous strictmode report for Icebase");
      }
    } catch (Throwable ignored) {
      LOG.info("Unable to queue anonymous strictmode report", ignored);
    }
  }

  /** Queues an anonymous EDT watchdog report for Icebase. No-op until {@link #start()}. */
  public static void recordWatchdog(int missedHeartbeats) {
    try {
      if (!tryAcquire("watchdog")) {
        LOG.debug("Throttled anonymous watchdog report");
        return;
      }
      CrashReportSpooler spooler = INSTANCE;
      if (spooler != null) {
        spooler.spoolWatchdog(missedHeartbeats);
        UPLOAD_QUEUE.execute(spooler::uploadPending);
        LOG.info("Automatically queued anonymous watchdog report for Icebase");
      }
    } catch (Throwable ignored) {
      LOG.info("Unable to queue anonymous watchdog report", ignored);
    }
  }

  void spool(Throwable problem) {
    if (problem == null) {
      return;
    }
    try {
      Files.createDirectories(directory.toPath());
      Path temporary = Files.createTempFile(directory.toPath(), "crash-", ".tmp");
      Files.writeString(temporary, GSON.toJson(reportFor(problem)), StandardCharsets.UTF_8);
      Path report =
          directory.toPath().resolve(temporary.getFileName().toString().replace(".tmp", ".json"));
      try {
        Files.move(temporary, report, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, report);
      }
      prune();
    } catch (Throwable ignored) {
      LOG.debug("Unable to spool crash report");
    }
  }

  void spoolStrictMode(String violationClass, int violationCount) {
    spoolStrictMode(violationClass, violationCount, null);
  }

  void spoolStrictMode(
      String violationClass, int violationCount, StackTraceElement[] capturedStack) {
    spoolStrictMode(violationClass, violationCount, capturedStack, null);
  }

  void spoolStrictMode(
      String violationClass,
      int violationCount,
      StackTraceElement[] capturedStack,
      GcWindow gcWindow) {
    spoolPayload(strictModeReportFor(violationClass, violationCount, capturedStack, gcWindow));
  }

  /**
   * Garbage collections, and the time spent in them, during the last {@code windowMs} of a stalled
   * event. A stall whose window is mostly GC time was a heap problem, not slow code.
   */
  public record GcWindow(int count, int timeMs, int windowMs) {}

  void spoolWatchdog(int missedHeartbeats) {
    spoolPayload(watchdogReportFor(missedHeartbeats));
  }

  private void spoolPayload(Object payload) {
    if (payload == null) {
      return;
    }
    try {
      Files.createDirectories(directory.toPath());
      Path temporary = Files.createTempFile(directory.toPath(), "crash-", ".tmp");
      Files.writeString(temporary, GSON.toJson(payload), StandardCharsets.UTF_8);
      Path report =
          directory.toPath().resolve(temporary.getFileName().toString().replace(".tmp", ".json"));
      try {
        Files.move(temporary, report, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temporary, report);
      }
      prune();
    } catch (Throwable ignored) {
      LOG.debug("Unable to spool crash report");
    }
  }

  private CrashReport reportFor(Throwable problem) {
    Frame[] frames = framesFor(problem.getStackTrace());
    return new CrashReport(
        appVersion,
        appBuild,
        System.getProperty("os.name", "unknown"),
        System.getProperty("os.version", "unknown"),
        System.getProperty("os.arch", "unknown"),
        System.getProperty("java.version", "unknown"),
        System.getProperty("java.runtime.version", "unknown"),
        System.getProperty("java.vendor", "unknown"),
        jlibtorrentVersion(),
        tellurideBuild(),
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().maxMemory() / (1024 * 1024),
        memoryBucket(),
        heapUsedMb(),
        runTag + problem.getClass().getName(),
        oomKind(problem),
        missingClass(problem),
        frames,
        UUID.randomUUID().toString().replace("-", ""));
  }

  private static Frame[] framesFor(StackTraceElement[] trace) {
    Frame[] frames = new Frame[Math.min(trace.length, MAX_FRAMES)];
    for (int i = 0; i < frames.length; i++) {
      StackTraceElement element = trace[i];
      frames[i] =
          new Frame(element.getClassName(), element.getMethodName(), element.getLineNumber());
    }
    return frames;
  }

  private StrictModeReport strictModeReportFor(
      String violationClass,
      int violationCount,
      StackTraceElement[] capturedStack,
      GcWindow gcWindow) {
    return new StrictModeReport(
        appVersion,
        appBuild,
        System.getProperty("os.name", "unknown"),
        System.getProperty("os.version", "unknown"),
        System.getProperty("os.arch", "unknown"),
        System.getProperty("java.version", "unknown"),
        System.getProperty("java.runtime.version", "unknown"),
        System.getProperty("java.vendor", "unknown"),
        jlibtorrentVersion(),
        tellurideBuild(),
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().maxMemory() / (1024 * 1024),
        memoryBucket(),
        heapUsedMb(),
        runTag + sanitizeViolationClass(violationClass),
        clamp(violationCount, 1, 1000),
        capturedStack == null ? null : framesFor(capturedStack),
        gcWindow,
        UUID.randomUUID().toString().replace("-", ""));
  }

  private WatchdogReport watchdogReportFor(int missedHeartbeats) {
    return new WatchdogReport(
        appVersion,
        appBuild,
        System.getProperty("os.name", "unknown"),
        System.getProperty("os.version", "unknown"),
        System.getProperty("os.arch", "unknown"),
        System.getProperty("java.version", "unknown"),
        System.getProperty("java.runtime.version", "unknown"),
        System.getProperty("java.vendor", "unknown"),
        jlibtorrentVersion(),
        tellurideBuild(),
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().maxMemory() / (1024 * 1024),
        memoryBucket(),
        heapUsedMb(),
        clamp(missedHeartbeats, 1, 100),
        UUID.randomUUID().toString().replace("-", ""));
  }

  private static String sanitizeViolationClass(String violationClass) {
    if (violationClass == null) {
      return "unknown";
    }
    StringBuilder ascii = new StringBuilder(Math.min(violationClass.length(), 200));
    for (int i = 0; i < violationClass.length() && ascii.length() < 200; i++) {
      char c = violationClass.charAt(i);
      ascii.append(c >= 0x20 && c <= 0x7E ? c : '?');
    }
    return ascii.length() == 0 ? "unknown" : ascii.toString();
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private String jlibtorrentVersion() {
    try {
      return LibTorrent.jlibtorrentVersion();
    } catch (Throwable ignored) {
      return "unavailable";
    }
  }

  private String tellurideBuild() {
    try {
      Integer build = TellurideBuild.detect(FrostWireUtils.getTellurideLauncherFile());
      return build == null ? "unavailable" : Integer.toString(build);
    } catch (Throwable ignored) {
      return "unavailable";
    }
  }

  private void prune() throws Exception {
    try (var files = Files.list(directory.toPath())) {
      var reports =
          files
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .sorted(Comparator.comparingLong(this::lastModified))
              .toList();
      reports.stream()
          .limit(Math.max(0, reports.size() - MAX_REPORTS))
          .forEach(path -> path.toFile().delete());
    }
  }

  private long lastModified(Path path) {
    return path.toFile().lastModified();
  }

  private String memoryBucket() {
    long megabytes = Runtime.getRuntime().maxMemory() / (1024 * 1024);
    if (megabytes < 128) return "lt_128m";
    if (megabytes < 512) return "128_512m";
    if (megabytes < 2048) return "512m_2g";
    return "gte_2g";
  }

  /** Java heap in use now, in MB. */
  static int heapUsedMb() {
    Runtime runtime = Runtime.getRuntime();
    return (int) ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
  }

  static final String DEV_TAG = "dev:";

  /**
   * The tag for this run: {@link #DEV_TAG} when the launcher says it isn't a distribution ({@code
   * gradle run} sets {@code fw.running.from.distribution=false}) or it runs from an IDE. Release
   * launchers set the flag to true; one that sets nothing counts as a release.
   */
  static String runTag(String distributionFlag, boolean fromIde) {
    if ("true".equals(distributionFlag)) {
      return "";
    }
    return "false".equals(distributionFlag) || fromIde ? DEV_TAG : "";
  }

  private static final Pattern CLASS_NAME =
      Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

  /**
   * The class a NoClassDefFoundError or ClassNotFoundException names, as a dotted class name, or
   * null for any other problem. Only a value that is a Java class name is reported, never the rest
   * of the message.
   */
  static String missingClass(Throwable problem) {
    if (!(problem instanceof NoClassDefFoundError)
        && !(problem instanceof ClassNotFoundException)) {
      return null;
    }
    String message = problem.getMessage();
    if (message == null) {
      return null;
    }
    message = message.trim();
    String initializationFailed = "Could not initialize class ";
    if (message.startsWith(initializationFailed)) {
      message = message.substring(initializationFailed.length());
    }
    String name = message.replace('/', '.');
    return name.length() <= 200 && CLASS_NAME.matcher(name).matches() ? name : null;
  }

  /**
   * What an OutOfMemoryError ran out of, from the JVM's message, or null for any other problem.
   * Only this fixed name is reported, never the message.
   */
  static String oomKind(Throwable problem) {
    if (!(problem instanceof OutOfMemoryError)) {
      return null;
    }
    String message = problem.getMessage() == null ? "" : problem.getMessage();
    if (message.contains("Java heap space")) return "heap";
    if (message.contains("GC overhead limit exceeded")) return "gc_overhead";
    if (message.contains("Compressed class space")) return "compressed_class";
    if (message.contains("Metaspace")) return "metaspace";
    if (message.contains("unable to create native thread")) return "native_thread";
    if (message.contains("irect buffer memory")) return "direct_buffer";
    if (message.contains("Requested array size exceeds VM limit")) return "array_size";
    return "other";
  }

  private void uploadPending() {
    if (client == null || !directory.isDirectory()) return;
    try (var files = Files.list(directory.toPath())) {
      files
          .filter(path -> path.getFileName().toString().endsWith(".json"))
          .sorted(Comparator.comparingLong(this::lastModified))
          .forEach(this::upload);
    } catch (Throwable ignored) {
      LOG.debug("Unable to upload crash reports");
    }
  }

  private void upload(Path report) {
    try {
      String content = Files.readString(report, StandardCharsets.UTF_8);
      client.post(ENDPOINT, 6000, content, "application/json; charset=utf-8");
      Files.deleteIfExists(report);
    } catch (Throwable ignored) {
      LOG.debug("Unable to upload crash report");
    }
  }

  private static final class CrashReport {
    private final int schema_version = 1;
    private final String report_type = "crash";
    private final String platform = "desktop";
    private final String app_version;
    private final String app_build;
    private final String os_name;
    private final String os_version;
    private final String os_arch;
    private final String runtime_version;
    private final String jre_version;
    private final String java_vendor;
    private final String jlibtorrent_version;
    private final String telluride_build;
    private final String cpu_count;
    private final String max_memory_mb;
    private final String memory_bucket;
    private final int heap_used_mb;
    private final String exception_class;

    /** Null, and then left out of the JSON, for anything but an OutOfMemoryError. */
    private final String oom_kind;

    /** Null, and then left out of the JSON, for anything but a class loading error. */
    private final String missing_class;

    private final Frame[] stack_frames;
    private final String report_nonce;

    private CrashReport(
        String appVersion,
        int appBuild,
        String osName,
        String osVersion,
        String osArch,
        String runtimeVersion,
        String jreVersion,
        String javaVendor,
        String jlibtorrentVersion,
        String tellurideBuild,
        int cpuCount,
        long maxMemoryMb,
        String memoryBucket,
        int heapUsedMb,
        String exceptionClass,
        String oomKind,
        String missingClass,
        Frame[] stackFrames,
        String nonce) {
      this.app_version = appVersion;
      this.app_build = Integer.toString(appBuild);
      this.os_name = osName;
      this.os_version = osVersion;
      this.os_arch = osArch;
      this.runtime_version = runtimeVersion;
      this.jre_version = jreVersion;
      this.java_vendor = javaVendor;
      this.jlibtorrent_version = jlibtorrentVersion;
      this.telluride_build = tellurideBuild;
      this.cpu_count = Integer.toString(cpuCount);
      this.max_memory_mb = Long.toString(maxMemoryMb);
      this.memory_bucket = memoryBucket;
      this.heap_used_mb = heapUsedMb;
      this.exception_class = exceptionClass;
      this.oom_kind = oomKind;
      this.missing_class = missingClass;
      this.stack_frames = stackFrames;
      this.report_nonce = nonce;
    }
  }

  private static final class Frame {
    @SerializedName("class")
    private final String className;

    private final String method;
    private final int line;

    private Frame(String className, String method, int line) {
      this.className = className;
      this.method = method;
      this.line = line;
    }
  }

  private static final class StrictModeReport {
    private final int schema_version = 1;
    private final String report_type = "strictmode";
    private final String platform = "desktop";
    private final String app_version;
    private final String app_build;
    private final String os_name;
    private final String os_version;
    private final String os_arch;
    private final String runtime_version;
    private final String jre_version;
    private final String java_vendor;
    private final String jlibtorrent_version;
    private final String telluride_build;
    private final String cpu_count;
    private final String max_memory_mb;
    private final String memory_bucket;
    private final int heap_used_mb;
    private final String violation_class;
    private final int violation_count;
    private final Frame[] stack_frames;

    /** Null, and then left out of the JSON, when the stall's GC activity wasn't measured. */
    private final Integer gc_count;

    private final Integer gc_time_ms;
    private final Integer gc_window_ms;

    private final String report_nonce;

    private StrictModeReport(
        String appVersion,
        int appBuild,
        String osName,
        String osVersion,
        String osArch,
        String runtimeVersion,
        String jreVersion,
        String javaVendor,
        String jlibtorrentVersion,
        String tellurideBuild,
        int cpuCount,
        long maxMemoryMb,
        String memoryBucket,
        int heapUsedMb,
        String violationClass,
        int violationCount,
        Frame[] stackFrames,
        GcWindow gcWindow,
        String nonce) {
      this.app_version = appVersion;
      this.app_build = Integer.toString(appBuild);
      this.os_name = osName;
      this.os_version = osVersion;
      this.os_arch = osArch;
      this.runtime_version = runtimeVersion;
      this.jre_version = jreVersion;
      this.java_vendor = javaVendor;
      this.jlibtorrent_version = jlibtorrentVersion;
      this.telluride_build = tellurideBuild;
      this.cpu_count = Integer.toString(cpuCount);
      this.max_memory_mb = Long.toString(maxMemoryMb);
      this.memory_bucket = memoryBucket;
      this.heap_used_mb = heapUsedMb;
      this.violation_class = violationClass;
      this.violation_count = violationCount;
      this.stack_frames = stackFrames;
      this.gc_count = gcWindow == null ? null : gcWindow.count();
      this.gc_time_ms = gcWindow == null ? null : gcWindow.timeMs();
      this.gc_window_ms = gcWindow == null ? null : gcWindow.windowMs();
      this.report_nonce = nonce;
    }
  }

  private static final class WatchdogReport {
    private final int schema_version = 1;
    private final String report_type = "watchdog";
    private final String platform = "desktop";
    private final String app_version;
    private final String app_build;
    private final String os_name;
    private final String os_version;
    private final String os_arch;
    private final String runtime_version;
    private final String jre_version;
    private final String java_vendor;
    private final String jlibtorrent_version;
    private final String telluride_build;
    private final String cpu_count;
    private final String max_memory_mb;
    private final String memory_bucket;
    private final int heap_used_mb;
    private final int missed_heartbeats;
    private final String report_nonce;

    private WatchdogReport(
        String appVersion,
        int appBuild,
        String osName,
        String osVersion,
        String osArch,
        String runtimeVersion,
        String jreVersion,
        String javaVendor,
        String jlibtorrentVersion,
        String tellurideBuild,
        int cpuCount,
        long maxMemoryMb,
        String memoryBucket,
        int heapUsedMb,
        int missedHeartbeats,
        String nonce) {
      this.app_version = appVersion;
      this.app_build = Integer.toString(appBuild);
      this.os_name = osName;
      this.os_version = osVersion;
      this.os_arch = osArch;
      this.runtime_version = runtimeVersion;
      this.jre_version = jreVersion;
      this.java_vendor = javaVendor;
      this.jlibtorrent_version = jlibtorrentVersion;
      this.telluride_build = tellurideBuild;
      this.cpu_count = Integer.toString(cpuCount);
      this.max_memory_mb = Long.toString(maxMemoryMb);
      this.memory_bucket = memoryBucket;
      this.heap_used_mb = heapUsedMb;
      this.missed_heartbeats = missedHeartbeats;
      this.report_nonce = nonce;
    }
  }
}
