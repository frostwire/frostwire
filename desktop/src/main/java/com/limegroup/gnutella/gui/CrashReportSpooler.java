package com.limegroup.gnutella.gui;

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.frostwire.jlibtorrent.LibTorrent;
import com.frostwire.search.telluride.TellurideBuild;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.limewire.util.CommonUtils;

/** Stores anonymous crash reports locally and uploads them outside the failing thread. */
public final class CrashReportSpooler {
  private static final Logger LOG = Logger.getLogger(CrashReportSpooler.class);
  private static final Gson GSON = new Gson();
  private static final String ENDPOINT = "https://icebase.frostwire.com/";
  private static final int MAX_REPORTS = 5;
  private static final int MAX_FRAMES = 64;
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
  private final int appBuild;

  public CrashReportSpooler(File directory, HttpClient client, String appVersion, int appBuild) {
    this.directory = directory;
    this.client = client;
    this.appVersion = appVersion;
    this.appBuild = appBuild;
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

  static void record(Throwable problem) {
    try {
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

  private CrashReport reportFor(Throwable problem) {
    StackTraceElement[] trace = problem.getStackTrace();
    Frame[] frames = new Frame[Math.min(trace.length, MAX_FRAMES)];
    for (int i = 0; i < frames.length; i++) {
      StackTraceElement element = trace[i];
      frames[i] =
          new Frame(element.getClassName(), element.getMethodName(), element.getLineNumber());
    }
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
        problem.getClass().getName(),
        frames,
        UUID.randomUUID().toString().replace("-", ""));
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
    private final String exception_class;
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
        String exceptionClass,
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
      this.exception_class = exceptionClass;
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
}
