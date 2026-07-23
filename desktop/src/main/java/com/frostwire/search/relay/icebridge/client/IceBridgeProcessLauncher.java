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
   * @throws IOException if the jar is missing or the process cannot start
   */
  public synchronized void start() throws IOException {
    if (process != null && process.isAlive()) {
      return;
    }
    if (!jarPath.isFile()) {
      throw new IOException("IceBridge jar not found: " + jarPath.getAbsolutePath());
    }

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

    logDir = Files.createTempDirectory("icebridge-launcher-" + controlHttpPort).toFile();
    File stdout = new File(logDir, "stdout.log");
    File stderr = new File(logDir, "stderr.log");
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectOutput(stdout);
    pb.redirectError(stderr);
    LOG.info("Starting IceBridge: " + String.join(" ", command));
    process = pb.start();
    client = new IceBridgeClient(controlHttpPort);
    client.setAuthToken(authToken);
  }

  /** Gracefully stop the IceBridge process. */
  @Override
  public synchronized void close() {
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
    deleteLogDir();
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
