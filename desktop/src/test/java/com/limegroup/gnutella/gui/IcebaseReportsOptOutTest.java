/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.settings.BugSettings;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.limewire.util.CommonUtils;

/**
 * frostwire/frostwire#1565: a user who opts out of Icebase reports sends nothing to Icebase. Every
 * path that queues, spools or uploads a report is checked with the setting off, against a fake HTTP
 * client that records each post.
 */
class IcebaseReportsOptOutTest {

  private final List<String> posts = new CopyOnWriteArrayList<>();
  private boolean previousSetting;
  private Object previousInstance;
  private Field instanceField;
  private Path directory;
  private CrashReportSpooler spooler;

  @BeforeEach
  void installRecordingSpooler() throws Exception {
    previousSetting = BugSettings.SEND_ICEBASE_REPORTS.getValue();
    directory = Files.createTempDirectory("icebase-opt-out");
    spooler = new CrashReportSpooler(directory.toFile(), recordingClient(), "7.1.5", 338, "");
    instanceField = CrashReportSpooler.class.getDeclaredField("INSTANCE");
    instanceField.setAccessible(true);
    previousInstance = instanceField.get(null);
    instanceField.set(null, spooler);
  }

  @AfterEach
  void restore() throws Exception {
    drainUploader();
    instanceField.set(null, previousInstance);
    BugSettings.SEND_ICEBASE_REPORTS.setValue(previousSetting);
  }

  private HttpClient recordingClient() {
    return (HttpClient)
        Proxy.newProxyInstance(
            HttpClient.class.getClassLoader(),
            new Class<?>[] {HttpClient.class},
            (proxy, method, args) -> {
              if (method.getName().equals("post")) {
                posts.add(String.valueOf(args[0]));
                return "{\"accepted\":true}";
              }
              if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> proxy == args[0];
                  default -> "RecordingHttpClient";
                };
              }
              return null;
            });
  }

  /** Waits until everything queued on the uploader thread so far has run. */
  private static void drainUploader() throws Exception {
    Field queue = CrashReportSpooler.class.getDeclaredField("UPLOAD_QUEUE");
    queue.setAccessible(true);
    ((ExecutorService) queue.get(null)).submit(() -> {}).get(10, TimeUnit.SECONDS);
  }

  private List<String> spooled() throws Exception {
    try (Stream<Path> files = Files.list(directory)) {
      return files.map(path -> path.getFileName().toString()).collect(Collectors.toList());
    }
  }

  private static final class OptOutProbe extends RuntimeException {}

  private static final class OptInProbe extends RuntimeException {}

  @Test
  void crashReportsAreNotQueuedOrSentWhileOptedOut() throws Exception {
    BugSettings.SEND_ICEBASE_REPORTS.setValue(false);

    CrashReportSpooler.record(new OptOutProbe());
    drainUploader();

    assertEquals(List.of(), posts);
    assertEquals(List.of(), spooled());
  }

  @Test
  void freezeReportsAreNotQueuedOrSentWhileOptedOut() throws Exception {
    BugSettings.SEND_ICEBASE_REPORTS.setValue(false);

    CrashReportSpooler.recordStrictMode(
        "java.awt.event.InvocationEvent#1200", 1, new Throwable().getStackTrace());
    drainUploader();

    assertEquals(List.of(), posts);
    assertEquals(List.of(), spooled());
  }

  @Test
  void watchdogReportsAreNotQueuedOrSentWhileOptedOut() throws Exception {
    BugSettings.SEND_ICEBASE_REPORTS.setValue(false);

    CrashReportSpooler.recordWatchdog(3);
    drainUploader();

    assertEquals(List.of(), posts);
    assertEquals(List.of(), spooled());
  }

  @Test
  void theAboutWindowsTestReportIsNotSentWhileOptedOut() throws Exception {
    BugSettings.SEND_ICEBASE_REPORTS.setValue(false);

    CrashReportSpooler.submitTestReport();
    drainUploader();

    assertEquals(List.of(), posts);
    assertEquals(List.of(), spooled());
  }

  @Test
  void fatalCrashesAreNotSpooledWhileOptedOut() throws Exception {
    BugSettings.SEND_ICEBASE_REPORTS.setValue(false);
    Path fatalSpool = new File(CommonUtils.getUserSettingsDir(), "crash-reports").toPath();
    List<String> before = list(fatalSpool);

    CrashReportSpooler.recordSync(new OptOutProbe());

    assertEquals(before, list(fatalSpool), "a fatal crash was spooled for a later upload");
  }

  private static List<String> list(Path dir) throws Exception {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.map(path -> path.getFileName().toString()).sorted().collect(Collectors.toList());
    }
  }

  @Test
  void reportsSpooledBeforeOptingOutAreDeletedUnsent() throws Exception {
    BugSettings.SEND_ICEBASE_REPORTS.setValue(true);
    spooler.spool(new OptOutProbe());
    spooler.spoolWatchdog(2);
    assertEquals(2, spooled().size());

    BugSettings.SEND_ICEBASE_REPORTS.setValue(false);
    // What runs at every launch (start()) and after every new report.
    spooler.uploadPending();

    assertEquals(List.of(), posts);
    assertEquals(List.of(), spooled());
  }

  @Test
  void optingOutDeletesTheReportsWaitingOnDisk() throws Exception {
    BugSettings.SEND_ICEBASE_REPORTS.setValue(true);
    spooler.spool(new OptOutProbe());
    assertEquals(1, spooled().size());

    IcebaseReports.setEnabled(false);
    drainUploader();

    assertFalse(BugSettings.SEND_ICEBASE_REPORTS.getValue());
    assertEquals(List.of(), spooled());
    assertEquals(List.of(), posts);
  }

  @Test
  void optingBackInSendsNewReports() throws Exception {
    IcebaseReports.setEnabled(false);
    IcebaseReports.setEnabled(true);

    // Its own exception class: the throttle is per class, shared by every test in this JVM.
    CrashReportSpooler.record(new OptInProbe());
    drainUploader();

    assertTrue(BugSettings.SEND_ICEBASE_REPORTS.getValue());
    assertEquals(List.of("https://icebase.frostwire.com/"), posts);
  }

  @Test
  void existingInstallsKeepSendingByDefault() {
    BugSettings.SEND_ICEBASE_REPORTS.revertToDefault();
    assertTrue(BugSettings.SEND_ICEBASE_REPORTS.getValue());
  }
}
