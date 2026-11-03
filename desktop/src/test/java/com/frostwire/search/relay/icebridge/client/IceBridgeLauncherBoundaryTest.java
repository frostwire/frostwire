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

import com.frostwire.search.relay.icebridge.IceBridgeControlPortAnnouncement;
import java.io.File;
import java.io.IOException;
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
  @SuppressWarnings("try") // closes the launcher early to check its closed state
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
  @SuppressWarnings("try") // closes the launcher early to check its closed state
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
    int fixedPort;
    try (ServerSocket probe = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      fixedPort = probe.getLocalPort(); // the invalid child never binds it, so any port will do
    }
    // A caller-supplied port needs no announcement, so start() returns before the child fails.
    try (IceBridgeProcessLauncher launcher =
        new IceBridgeProcessLauncher(
            invalidJar.toFile(),
            temp.resolve("identity").toFile(),
            fixedPort,
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

  /**
   * frostwire/frostwire#1478: the launcher used to probe a free port, release it and pass the
   * number to the child, so any process could take it first. The child's own bind must pick it.
   */
  @Test
  @SuppressWarnings("try") // closes the launcher early to check its closed state
  void automaticControlPortIsBoundByTheChildAndLearnedFromItsAnnouncement() throws Exception {
    try (IceBridgeProcessLauncher launcher = launcher()) {
      assertEquals(0, launcher.controlPort(), "no port is reserved before the child binds one");
      launcher.start();
      List<String> args = Arrays.asList(child(launcher).info().arguments().orElseThrow());
      assertEquals("0", args.get(args.indexOf("--control-http-port") + 1));
      int announced =
          IceBridgeControlPortAnnouncement.parse(
              Files.readString(launcher.logDir().toPath().resolve("stdout.log")));
      assertTrue(announced > 0, "the child announces the port it bound");
      assertEquals(announced, launcher.controlPort());
      assertClientReachesChild(launcher);
      launcher.close();
      assertEquals(0, launcher.controlPort(), "a stopped child has no control port");
    }
  }

  /**
   * frostwire/frostwire#1478: a respawn reused the first child's port even after another process
   * took it, so every respawn failed to bind until FrostWire restarted.
   */
  @Test
  @SuppressWarnings("try") // the resource only holds the port for the block
  void respawnAfterAnotherProcessTookTheControlPortUsesTheReplacementsPort() throws Exception {
    try (IceBridgeProcessLauncher launcher = launcher()) {
      launcher.start();
      waitForReady(launcher);
      IceBridgeClient client = launcher.client();
      int firstPort = launcher.controlPort();
      ProcessHandle first = child(launcher);
      first.destroyForcibly();
      first.onExit().get(5, TimeUnit.SECONDS);
      try (ServerSocket taken =
          new ServerSocket(firstPort, 1, java.net.InetAddress.getLoopbackAddress())) {
        launcher.start();
        waitForReady(launcher);
        assertNotEquals(firstPort, launcher.controlPort());
        assertSame(client, launcher.client(), "transport and registry sync retain this client");
        assertClientReachesChild(launcher);
      }
    }
  }

  @Test
  void callerSuppliedControlPortIsUsedAsGiven() throws Exception {
    // The test holds the port, so the fixture child cannot bind it: only the launcher's client
    // can connect here, and start() must not wait for an announcement it does not need.
    try (ServerSocket fixed = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        IceBridgeProcessLauncher launcher =
            new IceBridgeProcessLauncher(
                childJar(),
                temp.resolve("identity").toFile(),
                fixed.getLocalPort(),
                0,
                0,
                "CLIENT",
                "127.0.0.1")) {
      assertEquals(fixed.getLocalPort(), launcher.controlPort());
      launcher.start();
      assertEquals(fixed.getLocalPort(), launcher.controlPort());
      fixed.setSoTimeout(5_000);
      Thread health = new Thread(() -> launcher.client().healthQuiet(2_000));
      health.start();
      try (Socket accepted = fixed.accept()) {
        assertTrue(accepted.isConnected(), "the client connects to the caller's port");
      } finally {
        health.join(5_000);
      }
    }
  }

  @Test
  void childThatExitsBeforeAnnouncingItsPortFailsStartupAtOnce() throws Exception {
    Path invalidJar = temp.resolve("invalid.jar");
    Files.writeString(invalidJar, "not a jar");
    try (IceBridgeProcessLauncher launcher =
        new IceBridgeProcessLauncher(
            invalidJar.toFile(),
            temp.resolve("identity").toFile(),
            0,
            0,
            0,
            "CLIENT",
            "127.0.0.1")) {
      long start = System.nanoTime();
      IOException error = assertThrows(IOException.class, launcher::start);
      assertTrue(
          System.nanoTime() - start < Duration.ofSeconds(5).toNanos(),
          "an exited child must not wait for the fifteen-second startup deadline");
      assertTrue(
          error.getMessage().contains("before announcing its control port"), error::toString);
      assertFalse(launcher.isAlive());
      assertNull(launcher.logDir(), "the failed launch's log dir and credential are deleted");
      assertEquals(0, launcher.controlPort());
      assertFalse(Files.exists(temp.resolve("icebridge-child.pid")));
    }
  }

  /** The launcher's client connects to the port the current child listens on. */
  private static void assertClientReachesChild(IceBridgeProcessLauncher launcher) throws Exception {
    Path output = launcher.logDir().toPath().resolve("stdout.log");
    // The fixture accepts the connection and never answers, so the health call itself fails.
    assertFalse(launcher.client().healthQuiet(200));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!Files.readString(output).contains("accepted")) {
      assertTrue(System.nanoTime() < deadline, "the client did not connect to the child's port");
      Thread.sleep(10);
    }
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
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
      // The fixture announces its port with the same code as the real child.
      for (Class<?> type : List.of(StalledChild.class, IceBridgeControlPortAnnouncement.class)) {
        String classFile = type.getName().replace('.', '/') + ".class";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classFile)) {
          out.putNextEntry(new JarEntry(classFile));
          java.util.Objects.requireNonNull(in).transferTo(out);
          out.closeEntry();
        }
      }
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

  /**
   * Minimal Java-only child that binds its control port like IceBridgeServer (0 picks a free one),
   * announces it, and accepts a health connection but never responds.
   */
  public static class StalledChild {
    @SuppressWarnings("try") // the resource only holds the port for the block
    public static void main(String[] args) throws Exception {
      List<String> arguments = Arrays.asList(args);
      int port = Integer.parseInt(arguments.get(arguments.indexOf("--control-http-port") + 1));
      try (ServerSocket server =
          new ServerSocket(port, 1, java.net.InetAddress.getLoopbackAddress())) {
        System.out.println(IceBridgeControlPortAnnouncement.line(server.getLocalPort()));
        System.out.println("ready");
        System.out.flush();
        try (Socket ignored = server.accept()) {
          System.out.println("accepted");
          System.out.flush();
          Thread.sleep(30_000);
        }
      }
    }
  }
}
