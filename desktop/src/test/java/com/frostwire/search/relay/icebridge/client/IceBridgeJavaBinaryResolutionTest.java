/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The IceBridge child must be spawned with a real {@code java} binary. Native launchers (macOS
 * .app, Windows .exe) boot the JVM in-process via JNI, so {@code
 * ProcessHandle.current().info().command()} reports the application binary itself — spawning it
 * with {@code -jar} boots a second full FrostWire, and each copy spawns its own child (fork bomb).
 * Resolution must prefer {@code <java.home>/bin/java} and fail closed otherwise.
 */
class IceBridgeJavaBinaryResolutionTest {

  @Test
  void resolveJavaBinaryUsesJavaHome() throws Exception {
    String resolved = IceBridgeProcessLauncher.resolveJavaBinary();
    File expected =
        new File(
            System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");

    assertEquals(expected.getAbsolutePath(), resolved);
    assertTrue(new File(resolved).canExecute());
  }

  @Test
  void resolveJavaBinaryFailsClosedWithoutJavaHomeBinary() throws Exception {
    String originalJavaHome = System.getProperty("java.home");
    Path emptyHome = Files.createTempDirectory("fake-java-home-");
    System.setProperty("java.home", emptyHome.toString());
    try {
      // The current test process image is the Gradle worker JVM, not a plain
      // `java` binary path we'd accept blindly; with no <java.home>/bin/java
      // this must throw instead of spawning the current image.
      // (If the worker happens to be a real java binary the fallback still
      // returns java — either way it must never return a non-java binary.)
      try {
        String resolved = IceBridgeProcessLauncher.resolveJavaBinary();
        assertTrue(
            IceBridgeProcessLauncher.isJavaBinary(new File(resolved)),
            "fallback must still be a java binary, got: " + resolved);
      } catch (java.io.IOException expected) {
        // fail-closed: acceptable when the current image is not java either
      }
    } finally {
      System.setProperty("java.home", originalJavaHome);
    }
  }

  @Test
  void isJavaBinaryRejectsNativeLaunchers() throws Exception {
    Path dir = Files.createTempDirectory("fake-launchers-");

    Path appLauncher = Files.createFile(dir.resolve("FrostWire"));
    Path exeLauncher = Files.createFile(dir.resolve("frostwire.exe"));
    Path javaBinary = Files.createFile(dir.resolve("java"));
    appLauncher.toFile().setExecutable(true);
    exeLauncher.toFile().setExecutable(true);
    javaBinary.toFile().setExecutable(true);

    assertFalse(
        IceBridgeProcessLauncher.isJavaBinary(appLauncher.toFile()),
        "macOS .app launcher image must not be used as the child JVM");
    assertFalse(
        IceBridgeProcessLauncher.isJavaBinary(exeLauncher.toFile()),
        "Windows .exe launcher image must not be used as the child JVM");
    assertTrue(IceBridgeProcessLauncher.isJavaBinary(javaBinary.toFile()));

    assertFalse(IceBridgeProcessLauncher.isJavaBinary(new File(dir.toFile(), "missing")));
    assertFalse(IceBridgeProcessLauncher.isJavaBinary(dir.toFile()));
    assertFalse(IceBridgeProcessLauncher.isJavaBinary(null));
  }
}
