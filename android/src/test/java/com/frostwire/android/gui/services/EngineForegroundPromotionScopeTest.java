/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Guards the contract that prevents
 * {@code RemoteServiceException: Context.startForegroundService() did not then call
 * Service.startForeground()}. The service was started with startForegroundService, so it must
 * either successfully call startForeground() or fully stop itself to clear the pending requirement;
 * it must never abort the start request another way (throwing from onCreate, or swallow-and-return).
 */
public class EngineForegroundPromotionScopeTest {

  @Test
  public void promotionFailureFullyStopsService() throws Exception {
    String source = readProjectFile("src/main/java/com/frostwire/android/gui/services/EngineForegroundService.java");
    String tryShow = blockStartingAt(source, "private boolean tryShowPersistentNotification");
    assertTrue(
        "a failed foreground promotion must call stopSelf() to clear the pending startForeground requirement",
        tryShow.contains("stopSelf();"));
    assertFalse(
        "stopSelfResult(startId) does not always stop when multiple startIds are pending",
        tryShow.contains("stopSelfResult"));
  }

  @Test
  public void stickyRestartGuardFullyStopsService() throws Exception {
    String source = readProjectFile("src/main/java/com/frostwire/android/gui/services/EngineForegroundService.java");
    String onStart = blockStartingAt(source, "public int onStartCommand");
    String guard = onStart.substring(0, onStart.indexOf("tryShowPersistentNotification("));
    assertTrue(
        "the background sticky restart path must fully stop itself",
        guard.contains("stopSelf();"));
    assertFalse(
        "the background sticky restart path must not rely on stopSelfResult(startId)",
        guard.contains("stopSelfResult"));
  }

  @Test
  public void onCreateGuardsOnDemandWorkManagerInit() throws Exception {
    String source = readProjectFile("src/main/java/com/frostwire/android/gui/services/EngineForegroundService.java");
    String onCreate = blockStartingAt(source, "public void onCreate()");
    assertTrue(
        "onCreate must guard WorkManager scheduling: the initializer is removed in the manifest, so getInstance can throw",
        onCreate.contains("safeScheduleNotificationWork()"));
    assertFalse(
        "onCreate must not call the unguarded scheduleNotificationWork directly",
        onCreate.contains("scheduleNotificationWork();"));
  }

  private static String blockStartingAt(String source, String marker) {
    int start = source.indexOf(marker);
    if (start < 0) {
      return "";
    }
    int openingBrace = source.indexOf('{', start);
    if (openingBrace < 0) {
      return source.substring(start);
    }
    int depth = 0;
    for (int i = openingBrace; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(start, i);
      }
    }
    return source.substring(start);
  }

  private static String readProjectFile(String relativePath) throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relativePath);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relativePath);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
