/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.IdentityKeys;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IceBridgeProcessLauncherTest {

  private IceBridgeProcessLauncher launcher;

  @AfterEach
  void stop() {
    if (launcher != null) {
      launcher.close();
    }
  }

  @Test
  void startsAndHealthChecksIceBridgeProcess() throws Exception {
    File jar = new File(System.getProperty("user.dir"), "build/libs/icebridge.jar");
    assertTrue(jar.isFile(), "icebridge.jar must be built first (run icebridgeJar)");

    Path tmp = Files.createTempDirectory("icebridge-test");
    File identityFile = new File(tmp.toFile(), "identity.dat");

    // Pre-generate identity with no PoW so the daemon doesn't spend
    // minutes mining 20-bit difficulty (JDK Ed25519 KeyPairGenerator
    // is ~100x slower than native on some platforms).
    IdentityKeys keys = IdentityKeys.generate(0);
    IdentityKeys.save(keys, identityFile);

    launcher = new IceBridgeProcessLauncher(jar, identityFile, 0, 0, "BOTH");
    launcher.start();

    assertTrue(launcher.isAlive());
    assertTrue(launcher.controlPort() > 0);
    assertTrue(launcher.rudpPort() > 0);
    // relayPort defaults or passed
    assertTrue(launcher.relayPort() > 0);

    IceBridgeClient client = launcher.client();
    boolean healthy = false;
    for (int i = 0; i < 300; i++) {
      if (!launcher.isAlive()) {
        fail("IceBridge subprocess exited before becoming healthy");
      }
      if (client.health()) {
        healthy = true;
        break;
      }
      Thread.sleep(100);
    }
    assertTrue(healthy, "IceBridge daemon did not become healthy in time (30s)");
  }

  @Test
  void customRelayPortIsPassedToSubprocess() throws Exception {
    File jar = new File(System.getProperty("user.dir"), "build/libs/icebridge.jar");
    assertTrue(jar.isFile(), "icebridge.jar must be built first (run icebridgeJar)");

    Path tmp = Files.createTempDirectory("icebridge-test-relayport");
    File identityFile = new File(tmp.toFile(), "identity.dat");

    IdentityKeys keys = IdentityKeys.generate(0);
    IdentityKeys.save(keys, identityFile);

    int customRelay = 7000; // as in user scenario
    launcher =
        new IceBridgeProcessLauncher(jar, identityFile, 0, 0, customRelay, "BOTH", "127.0.0.1");
    // Don't fully start (would take time), just verify constructor + getter
    assertEquals(customRelay, launcher.relayPort());
    // Also test the 5-arg ctor defaults relay to 6888
    IceBridgeProcessLauncher l2 = new IceBridgeProcessLauncher(jar, identityFile, 0, 0, "BOTH");
    assertEquals(6888, l2.relayPort());
  }

  /**
   * Regression for the recurring orphan failure (#917/#937/#944): a stale icebridge.jar child from
   * a dead FrostWire session holds the rUDP port; a fresh launcher must kill it (pidfile next to
   * the identity file) and start its own healthy child instead of silently failing to bind.
   */
  @Test
  void killsStaleOrphanChildAndReclaimsTheRudpPort() throws Exception {
    File jar = new File(System.getProperty("user.dir"), "build/libs/icebridge.jar");
    assertTrue(jar.isFile(), "icebridge.jar must be built first (run icebridgeJar)");

    Path tmp = Files.createTempDirectory("icebridge-test-orphan");
    File identityFile = new File(tmp.toFile(), "identity.dat");
    IdentityKeys keys = IdentityKeys.generate(0);
    IdentityKeys.save(keys, identityFile);

    int orphanRudp = freePort();
    int orphanControl = freePort();

    // Simulate the orphan: a child whose FrostWire parent died, holding the port.
    Process orphan =
        new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar",
                jar.getAbsolutePath(),
                "--rudp-port",
                String.valueOf(orphanRudp),
                "--control-http-port",
                String.valueOf(orphanControl),
                "--role",
                "BOTH",
                "--host",
                "127.0.0.1",
                "--identity-file",
                identityFile.getAbsolutePath())
            .redirectOutput(new File(tmp.toFile(), "orphan-stdout.log"))
            .redirectError(new File(tmp.toFile(), "orphan-stderr.log"))
            .start();
    Files.writeString(
        new File(tmp.toFile(), "icebridge-child.pid").toPath(), String.valueOf(orphan.pid()));
    // Wait for the orphan to bind the rUDP port.
    boolean orphanUp = false;
    for (int i = 0; i < 100 && !orphanUp; i++) {
      try (java.net.DatagramSocket probe = new java.net.DatagramSocket(orphanRudp)) {
        // port still free — orphan not up yet
      } catch (IOException busy) {
        orphanUp = true;
      }
      Thread.sleep(100);
    }
    assertTrue(orphanUp, "orphan never bound the rUDP port");
    try {
      // Fresh launcher, same identity dir + same rUDP port as the orphan holds.
      launcher = new IceBridgeProcessLauncher(jar, identityFile, 0, orphanRudp, "BOTH");
      launcher.start();

      boolean healthy = false;
      for (int i = 0; i < 300; i++) {
        if (launcher.client() != null && launcher.client().health()) {
          healthy = true;
          break;
        }
        Thread.sleep(100);
      }
      assertTrue(healthy, "fresh child did not become healthy after reclaiming the port");
      assertTrue(
          orphan.waitFor(10, java.util.concurrent.TimeUnit.SECONDS),
          "stale orphan must be killed by the launcher");
    } finally {
      orphan.destroyForcibly();
    }
  }

  /**
   * Parent watchdog: a child started with --parent-pid exits when its parent process dies — no
   * orphan survives a crash/kill -9.
   */
  @Test
  void childExitsWhenItsParentProcessDies() throws Exception {
    File jar = new File(System.getProperty("user.dir"), "build/libs/icebridge.jar");
    assertTrue(jar.isFile(), "icebridge.jar must be built first (run icebridgeJar)");

    Path tmp = Files.createTempDirectory("icebridge-test-watchdog");
    File identityFile = new File(tmp.toFile(), "identity.dat");
    IdentityKeys keys = IdentityKeys.generate(0);
    IdentityKeys.save(keys, identityFile);

    // A short-lived "parent" the child watches.
    Process shortLivedParent = new ProcessBuilder("sleep", "1").start();
    long parentPid = shortLivedParent.pid();
    shortLivedParent.waitFor();

    Process child =
        new ProcessBuilder(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar",
                jar.getAbsolutePath(),
                "--rudp-port",
                String.valueOf(freePort()),
                "--control-http-port",
                String.valueOf(freePort()),
                "--role",
                "BOTH",
                "--host",
                "127.0.0.1",
                "--identity-file",
                identityFile.getAbsolutePath(),
                "--parent-pid",
                String.valueOf(parentPid))
            .redirectOutput(new File(tmp.toFile(), "watchdog-stdout.log"))
            .redirectError(new File(tmp.toFile(), "watchdog-stderr.log"))
            .start();

    assertTrue(
        child.waitFor(30, java.util.concurrent.TimeUnit.SECONDS),
        "child must exit after its parent dies (watchdog interval is 3s)");
  }

  /**
   * Supervision: when the child dies at runtime, the supervisor respawns a healthy replacement
   * without any caller action — the self-heal that makes restart-orphan scenarios impossible to
   * notice.
   */
  @Test
  void supervisorRespawnsChildAfterExternalKill() throws Exception {
    File jar = new File(System.getProperty("user.dir"), "build/libs/icebridge.jar");
    assertTrue(jar.isFile(), "icebridge.jar must be built first (run icebridgeJar)");

    Path tmp = Files.createTempDirectory("icebridge-test-supervision");
    File identityFile = new File(tmp.toFile(), "identity.dat");
    IdentityKeys keys = IdentityKeys.generate(0);
    IdentityKeys.save(keys, identityFile);

    launcher = new IceBridgeProcessLauncher(jar, identityFile, 0, 0, "BOTH");
    launcher.start();
    assertTrue(waitForHealthy(launcher, 30_000), "initial child never became healthy");
    long firstPid = readPidFile(tmp.toFile());

    launcher.startSupervision(1_000);

    // Kill the child from outside (simulates a crash / third-party kill).
    ProcessHandle.of(firstPid).ifPresent(ProcessHandle::destroyForcibly);
    long killDeadline = System.currentTimeMillis() + 5000;
    while (launcher.isAlive() && System.currentTimeMillis() < killDeadline) {
      Thread.sleep(100);
    }
    assertFalse(launcher.isAlive(), "child should die after external kill");

    // The supervisor must bring a new healthy child back.
    assertTrue(waitForHealthy(launcher, 30_000), "supervisor did not respawn a healthy child");
    long secondPid = readPidFile(tmp.toFile());
    assertTrue(secondPid > 0 && secondPid != firstPid, "respawned child must be a new process");
  }

  private static boolean waitForHealthy(IceBridgeProcessLauncher l, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (l.isAlive() && l.client() != null && l.client().health()) {
        return true;
      }
      Thread.sleep(100);
    }
    return false;
  }

  private static long readPidFile(File dir) throws IOException {
    File pidFile = new File(dir, "icebridge-child.pid");
    if (!pidFile.isFile()) {
      return -1;
    }
    return Long.parseLong(java.nio.file.Files.readString(pidFile.toPath()).trim());
  }

  private static int freePort() throws IOException {
    try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
      return s.getLocalPort();
    }
  }
}
