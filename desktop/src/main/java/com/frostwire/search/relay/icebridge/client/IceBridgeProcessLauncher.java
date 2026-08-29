/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.util.Logger;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts and stops the IceBridge daemon as an external process for the FrostWire desktop client.
 *
 * <p>The launcher picks free ports if none are supplied, builds the command line for {@code
 * icebridge.jar}, and exposes a ready-to-use {@link IceBridgeClient} pointing at the control port.
 */
public final class IceBridgeProcessLauncher implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(IceBridgeProcessLauncher.class);

  private final File jarPath;
  private final File identityFile;
  private final int controlHttpPort;
  private final int rudpPort;
  private final int relayPort;
  private final String role;
  private final String host;
  private final String authToken;

  private Process process;
  private IceBridgeClient client;
  private File logDir;

  /** Construct a launcher with explicit ports (use 0 to auto-select). */
  public IceBridgeProcessLauncher(
      File jarPath, File identityFile, int controlHttpPort, int rudpPort, String role) {
    this(jarPath, identityFile, controlHttpPort, rudpPort, 6888, role, "127.0.0.1");
  }

  /**
   * Construct a launcher with explicit ports (use 0 to auto-select) and a custom rUDP bind host.
   * Use {@code "0.0.0.0"} to accept rUDP from remote peers (cloud forwarder mode); use {@code
   * "127.0.0.1"} for local-only daemon mode. The control HTTP server always binds to 127.0.0.1
   * regardless of this parameter.
   */
  public IceBridgeProcessLauncher(
      File jarPath,
      File identityFile,
      int controlHttpPort,
      int rudpPort,
      String role,
      String host) {
    this(jarPath, identityFile, controlHttpPort, rudpPort, 6888, role, host);
  }

  public IceBridgeProcessLauncher(
      File jarPath,
      File identityFile,
      int controlHttpPort,
      int rudpPort,
      int relayPort,
      String role,
      String host) {
    if (jarPath == null) {
      throw new IllegalArgumentException("jarPath is null");
    }
    if (identityFile == null) {
      throw new IllegalArgumentException("identityFile is null");
    }
    this.jarPath = jarPath;
    this.identityFile = identityFile;
    this.controlHttpPort = controlHttpPort <= 0 ? freePort() : controlHttpPort;
    this.rudpPort = rudpPort <= 0 ? freePort() : rudpPort;
    // relayPort=0 disables the child's identity TCP listener (embedder owns it).
    this.relayPort = relayPort;
    this.role = role == null || role.isEmpty() ? "BOTH" : role;
    this.host = host == null || host.isEmpty() ? "127.0.0.1" : host;
    // Generate a random auth token for the control API.
    byte[] tokenBytes = new byte[32];
    new java.security.SecureRandom().nextBytes(tokenBytes);
    this.authToken = com.frostwire.util.Hex.encode(tokenBytes);
  }

  public String authToken() {
    return authToken;
  }

  public IceBridgeClient client() {
    return client;
  }

  public int controlPort() {
    return controlHttpPort;
  }

  public int rudpPort() {
    return rudpPort;
  }

  public int relayPort() {
    return relayPort;
  }

  public String host() {
    return host;
  }

  /**
   * Start the IceBridge process. Daemon stdout/stderr are redirected to files under a temporary
   * directory so the subprocess cannot block on a shared Gradle worker pipe.
   *
   * <p>First kills any stale child left over by a previous crashed/killed FrostWire session
   * (pidfile next to the identity file) so it cannot steal the rUDP port — the recurring orphan
   * failure where a fresh Main gets EADDRINUSE and silently loses its answerer.
   *
   * @throws IOException if the jar is missing or the process cannot start
   */
  public synchronized void start() throws IOException {
    if (process != null && process.isAlive()) {
      return;
    }
    if (!jarPath.isFile()) {
      throw new IOException("IceBridge jar not found: " + jarPath.getAbsolutePath());
    }

    killStaleIceBridgeChild();
    waitForRudpPortFree();

    String java = ProcessHandle.current().info().command().orElse("java");
    List<String> command = new ArrayList<>();
    command.add(java);
    command.add("-jar");
    command.add(jarPath.getAbsolutePath());
    command.add("--rudp-port");
    command.add(String.valueOf(rudpPort));
    command.add("--relay-port");
    command.add(String.valueOf(relayPort));
    command.add("--control-http-port");
    command.add(String.valueOf(controlHttpPort));
    command.add("--role");
    command.add(role);
    command.add("--host");
    command.add(host);
    command.add("--auth-token");
    command.add(authToken);
    if (identityFile != null) {
      command.add("--identity-file");
      command.add(identityFile.getAbsolutePath());
    }
    // Child exits when this process dies — no orphan can survive a crash/kill -9.
    command.add("--parent-pid");
    command.add(String.valueOf(ProcessHandle.current().pid()));

    logDir = Files.createTempDirectory("icebridge-launcher-" + controlHttpPort).toFile();
    File stdout = new File(logDir, "stdout.log");
    File stderr = new File(logDir, "stderr.log");
    ProcessBuilder pb = new ProcessBuilder(command);
    // Embedder: parent FrostWire owns Protocol #1 + LocalIndex. Child must not start
    // SearchRelayApp (empty index + competing /poll) — see IceBridgeServer.main.
    pb.environment().put("ICEBRIDGE_SEARCH_APP", "false");
    pb.redirectOutput(stdout);
    pb.redirectError(stderr);
    LOG.info("Starting IceBridge: " + String.join(" ", command));
    process = pb.start();
    writePidFile(process.pid());
    client = new IceBridgeClient(controlHttpPort);
    client.setAuthToken(authToken);
  }

  /** Gracefully stop the IceBridge process and its supervision. */
  @Override
  public synchronized void close() {
    stopSupervision();
    stopProcess();
  }

  /** Kill the child process and clean up its pidfile/log dir (keeps supervision). */
  private synchronized void stopProcess() {
    if (process != null && process.isAlive()) {
      LOG.info("Stopping IceBridge process");
      process.destroy();
      try {
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
          process.destroyForcibly();
          process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
      }
    }
    process = null;
    client = null;
    clearPidFile();
    deleteLogDir();
  }

  private File pidFile() {
    File dir = identityFile != null ? identityFile.getParentFile() : null;
    if (dir == null) {
      dir = new File(System.getProperty("java.io.tmpdir"));
    }
    return new File(dir, "icebridge-child.pid");
  }

  private long childPid = -1;

  private void writePidFile(long pid) {
    childPid = pid;
    try {
      Files.writeString(pidFile().toPath(), String.valueOf(pid));
    } catch (Throwable t) {
      LOG.warn("Could not write IceBridge pid file", t);
    }
  }

  private void clearPidFile() {
    try {
      File f = pidFile();
      if (f.isFile() && childPid > 0) {
        String content = Files.readString(f.toPath()).trim();
        if (content.equals(String.valueOf(childPid))) {
          f.delete();
        }
      }
    } catch (Throwable ignored) {
    }
  }

  /**
   * Kill a stale child left by a previous FrostWire session: the pidfile next to the identity file
   * names an icebridge.jar process; if it is alive and is not this process, terminate it.
   * Fail-safe: unknown/dead/foreign pids are ignored.
   */
  private void killStaleIceBridgeChild() {
    File f = pidFile();
    if (!f.isFile()) {
      return;
    }
    try {
      long pid = Long.parseLong(Files.readString(f.toPath()).trim());
      if (pid <= 0 || pid == ProcessHandle.current().pid()) {
        return;
      }
      ProcessHandle stale = ProcessHandle.of(pid).orElse(null);
      if (stale == null || !stale.isAlive()) {
        return;
      }
      String commandLine = stale.info().commandLine().orElse("");
      if (!commandLine.contains("icebridge.jar")) {
        return; // never kill a process we did not spawn
      }
      LOG.warn("Killing stale IceBridge child pid " + pid + " left by a previous session");
      stale.destroy();
      if (!stale
          .onExit()
          .handle((p, t) -> p.isAlive())
          .get(5, java.util.concurrent.TimeUnit.SECONDS)) {
        return;
      }
      stale.destroyForcibly();
      stale.onExit().get(2, java.util.concurrent.TimeUnit.SECONDS);
    } catch (Throwable t) {
      LOG.warn("Stale IceBridge child cleanup failed (continuing)", t);
    }
  }

  /** Wait until our configured rUDP port is bindable again after a stale kill. */
  private void waitForRudpPortFree() {
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      try (java.net.DatagramSocket probe = new java.net.DatagramSocket(rudpPort)) {
        return; // port is free
      } catch (IOException notYetFree) {
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
    LOG.warn("rUDP port " + rudpPort + " still busy after stale-child cleanup; starting anyway");
  }

  private void deleteLogDir() {
    if (logDir == null) {
      return;
    }
    try {
      Files.walk(logDir.toPath())
          .sorted(java.util.Comparator.reverseOrder())
          .map(java.nio.file.Path::toFile)
          .forEach(File::delete);
      logDir.delete();
    } catch (Throwable t) {
      LOG.warn("Failed to delete IceBridge log dir: " + logDir, t);
    }
    logDir = null;
  }

  public boolean isAlive() {
    return process != null && process.isAlive();
  }

  /**
   * Supervise the child: periodically verify it is alive and its control endpoint answers; respawn
   * (stale-kill included) when it is not. Makes the desktop self-heal after orphaned-port
   * collisions, child crashes, and any other runtime loss — no restart required.
   */
  public synchronized void startSupervision(long periodMs) {
    if (periodMs <= 0) {
      throw new IllegalArgumentException("periodMs must be > 0");
    }
    if (supervisor != null) {
      return; // already supervised
    }
    supervisor =
        new Thread(
            () -> {
              while (supervising) {
                try {
                  Thread.sleep(periodMs);
                } catch (InterruptedException e) {
                  return;
                }
                if (!supervising) {
                  return;
                }
                try {
                  if (isAlive() && client != null && client.health()) {
                    continue;
                  }
                  if (!supervising) {
                    return;
                  }
                  LOG.warn(
                      "IceBridge child unhealthy (alive="
                          + isAlive()
                          + ") — respawning under supervision");
                  stopProcess();
                  start();
                } catch (Throwable t) {
                  LOG.warn("IceBridge supervision respawn failed (will retry)", t);
                }
              }
            },
            "icebridge-child-supervisor");
    supervisor.setDaemon(true);
    supervising = true;
    supervisor.start();
    LOG.info("IceBridge child supervision started (period " + periodMs + "ms)");
  }

  /** Stop supervision (does not stop the child). */
  public synchronized void stopSupervision() {
    supervising = false;
    if (supervisor != null) {
      supervisor.interrupt();
      supervisor = null;
    }
  }

  private Thread supervisor;
  private volatile boolean supervising;

  public File logDir() {
    return logDir;
  }

  private static int freePort() {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No free port available", e);
    }
  }
}
