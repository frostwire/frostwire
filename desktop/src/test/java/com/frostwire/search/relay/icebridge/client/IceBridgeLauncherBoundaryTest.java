/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline launcher boundary tests: no IceBridge JAR, JNI, DHT or external peers. */
class IceBridgeLauncherBoundaryTest {
  @TempDir Path temp;

  @Test
  void credentialIsRestrictedAndAbsentFromArgumentsAndLogs() throws Exception {
    try (IceBridgeProcessLauncher launcher = launcher()) {
      launcher.start();
      waitForReady(launcher);
      Path tokenFile = tokenFile(launcher);
      assertEquals(launcher.authToken(), Files.readString(tokenFile).trim());
      if (Files.getFileAttributeView(tokenFile, PosixFileAttributeView.class) != null) {
        assertEquals(
            PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(tokenFile));
      }
      List<String> args = Arrays.asList(child(launcher).info().arguments().orElseThrow());
      assertFalse(args.contains("--auth-token"));
      assertTrue(args.contains("--auth-tokens-file"));
      assertFalse(args.stream().anyMatch(arg -> arg.contains(launcher.authToken())));
      assertFalse(
          Files.readString(launcher.logDir().toPath().resolve("stdout.log"))
              .contains(launcher.authToken()));
      assertFalse(
          Files.readString(launcher.logDir().toPath().resolve("stderr.log"))
              .contains(launcher.authToken()));
      launcher.close();
      assertFalse(Files.exists(tokenFile));
    }
  }

  @Test
  void stalledHealthRequestCannotOutliveReadinessBudget() throws Exception {
    try (IceBridgeProcessLauncher launcher = launcher()) {
      launcher.start();
      waitForReady(launcher);
      long start = System.nanoTime();
      assertFalse(launcher.awaitHealthy(150));
      assertTrue(
          System.nanoTime() - start < Duration.ofSeconds(2).toNanos(),
          "a stalled HTTP call must not use the client's default ten-second timeout");
    }
  }

  @Test
  void startupPortWaitUsesTheSameDeadline() throws Exception {
    try (java.net.DatagramSocket occupied = new java.net.DatagramSocket(0);
        IceBridgeProcessLauncher launcher =
            new IceBridgeProcessLauncher(
                childJar(),
                temp.resolve("identity").toFile(),
                0,
                occupied.getLocalPort(),
                0,
                "CLIENT",
                "127.0.0.1")) {
      long start = System.nanoTime();
      org.junit.jupiter.api.Assertions.assertThrows(
          java.io.InterruptedIOException.class, () -> launcher.startAndAwaitHealthy(100));
      assertTrue(System.nanoTime() - start < Duration.ofSeconds(2).toNanos());
      assertFalse(launcher.isAlive());
    }
  }

  @Test
  void childExitDeletesCredentialAndRestartUsesANewFile() throws Exception {
    try (IceBridgeProcessLauncher launcher = launcher()) {
      launcher.start();
      waitForReady(launcher);
      Path firstTokenFile = tokenFile(launcher);
      IceBridgeClient firstClient = launcher.client();
      ProcessHandle child = child(launcher);
      child.destroyForcibly();
      child.onExit().get(5, TimeUnit.SECONDS);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (Files.exists(firstTokenFile) && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      assertFalse(Files.exists(firstTokenFile), "exit callback must remove the launch credential");
      launcher.start();
      waitForReady(launcher);
      assertNotEquals(firstTokenFile, tokenFile(launcher));
      assertSame(firstClient, launcher.client(), "transport and registry sync retain this client");
    }
  }

  @Test
  void supervisorRetainsClientAndClosePreventsFurtherRespawns() throws Exception {
    try (IceBridgeProcessLauncher launcher = launcher()) {
      launcher.start();
      waitForReady(launcher);
      IceBridgeClient originalClient = launcher.client();
      Path originalTokenFile = tokenFile(launcher);
      ProcessHandle originalChild = child(launcher);
      originalChild.destroyForcibly();
      originalChild.onExit().get(5, TimeUnit.SECONDS);
      launcher.startSupervision(100);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!launcher.isAlive() && System.nanoTime() - deadline < 0) {
        Thread.sleep(10);
      }
      assertTrue(launcher.isAlive(), "supervisor must launch a replacement child");
      launcher.stopSupervision();
      waitForReady(launcher);
      ProcessHandle replacement = child(launcher);
      assertNotEquals(originalChild.pid(), replacement.pid());
      assertSame(originalClient, launcher.client());
      assertFalse(Files.exists(originalTokenFile));
      Path replacementTokenFile = tokenFile(launcher);
      launcher.startSupervision(100);
      launcher.close();
      assertFalse(replacement.isAlive());
      assertFalse(Files.exists(replacementTokenFile));
      assertFalse(Files.exists(temp.resolve("icebridge-child.pid")));
      assertNull(launcher.client());
      Thread.sleep(250);
      assertFalse(launcher.isAlive(), "a stopped supervisor must not resurrect the child");
    }
  }

  @Test
  void interruptedStartupDoesNotCreateAChildOrCredential() throws Exception {
    try (IceBridgeProcessLauncher launcher = launcher()) {
      Thread.currentThread().interrupt();
      try {
        assertThrows(
            java.io.InterruptedIOException.class, () -> launcher.startAndAwaitHealthy(100));
        assertThrows(InterruptedException.class, () -> launcher.awaitHealthy(100));
        assertFalse(launcher.isAlive());
        assertNull(launcher.logDir());
        assertFalse(Files.exists(temp.resolve("icebridge-child.pid")));
      } finally {
        Thread.interrupted();
      }
    }
  }

  @Test
  void nonpositiveBudgetsAreRejectedBeforeStarting() throws Exception {
    try (IceBridgeProcessLauncher launcher = launcher()) {
      assertThrows(IllegalArgumentException.class, () -> launcher.startAndAwaitHealthy(0));
      assertThrows(IllegalArgumentException.class, () -> launcher.awaitHealthy(-1));
      assertFalse(launcher.isAlive());
      assertNull(launcher.logDir());
    }
  }

  @Test
  void stalePidFileCannotKillAChildOwnedByALiveParent() throws Exception {
    File jar = childJar();
    File identity = temp.resolve("identity").toFile();
    try (IceBridgeProcessLauncher existing =
            new IceBridgeProcessLauncher(jar, identity, 0, 0, 0, "CLIENT", "127.0.0.1");
        IceBridgeProcessLauncher replacement =
            new IceBridgeProcessLauncher(jar, identity, 0, 0, 0, "CLIENT", "127.0.0.1")) {
      existing.start();
      waitForReady(existing);
      ProcessHandle liveChild = child(existing);
      replacement.start();
      waitForReady(replacement);
      assertTrue(liveChild.isAlive(), "matching JAR and identity do not imply the parent died");
    }
  }

  @Test
  void stalePidFileCannotKillAnotherIdentitysIceBridgeChild() throws Exception {
    File jar = childJar();
    File otherIdentity = Files.createDirectory(temp.resolve("other")).resolve("identity").toFile();
    try (IceBridgeProcessLauncher other =
            new IceBridgeProcessLauncher(jar, otherIdentity, 0, 0, 0, "CLIENT", "127.0.0.1");
        IceBridgeProcessLauncher launcher =
            new IceBridgeProcessLauncher(
                jar, temp.resolve("identity").toFile(), 0, 0, 0, "CLIENT", "127.0.0.1")) {
      other.start();
      waitForReady(other);
      String otherPid = Files.readString(temp.resolve("other/icebridge-child.pid"));
      Files.writeString(temp.resolve("icebridge-child.pid"), otherPid);
      launcher.start();
      waitForReady(launcher);
      assertTrue(other.isAlive(), "a pidfile is not proof of ownership");
    }
  }

  @Test
  void failedChildStartupDoesNotLeaveCredentialAfterClose() throws Exception {
    Path invalidJar = temp.resolve("invalid.jar");
    Files.writeString(invalidJar, "not a jar");
    Path logDir;
    try (IceBridgeProcessLauncher launcher =
        new IceBridgeProcessLauncher(
            invalidJar.toFile(),
            temp.resolve("identity").toFile(),
            0,
            0,
            0,
            "CLIENT",
            "127.0.0.1")) {
      launcher.start();
      logDir = launcher.logDir().toPath();
      assertFalse(launcher.awaitHealthy(2_000));
    }
    assertFalse(Files.exists(logDir));
  }

  private IceBridgeProcessLauncher launcher() throws Exception {
    return new IceBridgeProcessLauncher(
        childJar(), temp.resolve("identity").toFile(), 0, 0, 0, "CLIENT", "127.0.0.1");
  }

  private File childJar() throws Exception {
    Path jar = temp.resolve("icebridge.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, StalledChild.class.getName());
    String classFile = StalledChild.class.getName().replace('.', '/') + ".class";
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest);
        InputStream in = getClass().getClassLoader().getResourceAsStream(classFile)) {
      out.putNextEntry(new JarEntry(classFile));
      java.util.Objects.requireNonNull(in).transferTo(out);
      out.closeEntry();
    }
    return jar.toFile();
  }

  private Path tokenFile(IceBridgeProcessLauncher launcher) throws Exception {
    // The path is non-secret and belongs in argv; the credential must not.
    List<String> args = Arrays.asList(child(launcher).info().arguments().orElseThrow());
    return Path.of(args.get(args.indexOf("--auth-tokens-file") + 1));
  }

  private ProcessHandle child(IceBridgeProcessLauncher launcher) throws Exception {
    long pid = Long.parseLong(Files.readString(temp.resolve("icebridge-child.pid")).trim());
    return ProcessHandle.of(pid).orElseThrow();
  }

  private static void waitForReady(IceBridgeProcessLauncher launcher) throws Exception {
    Path output = launcher.logDir().toPath().resolve("stdout.log");
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline && launcher.isAlive()) {
      if (Files.exists(output) && Files.readString(output).contains("ready")) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("local child fixture did not start");
  }

  /** Minimal Java-only child that accepts a health connection but never responds. */
  public static class StalledChild {
    public static void main(String[] args) throws Exception {
      List<String> arguments = Arrays.asList(args);
      int port = Integer.parseInt(arguments.get(arguments.indexOf("--control-http-port") + 1));
      try (ServerSocket server =
          new ServerSocket(port, 1, java.net.InetAddress.getLoopbackAddress())) {
        System.out.println("ready");
        try (Socket ignored = server.accept()) {
          Thread.sleep(30_000);
        }
      }
    }
  }
}
