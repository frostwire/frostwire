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
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

  private volatile Process process;
  private volatile IceBridgeClient client;
  private File logDir;
  private Path tokenFile;

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
    startUntil(System.nanoTime() + TimeUnit.SECONDS.toNanos(15));
  }

  private synchronized void startUntil(long deadline) throws IOException {
    if (Thread.currentThread().isInterrupted() || deadline - System.nanoTime() <= 0) {
      throw new java.io.InterruptedIOException("IceBridge startup cancelled or timed out");
    }
    if (process != null && process.isAlive()) {
      return;
    }
    if (!jarPath.isFile()) {
      throw new IOException("IceBridge jar not found: " + jarPath.getAbsolutePath());
    }
    if (process != null || logDir != null) {
      stopProcess();
    }

    killStaleIceBridgeChild(deadline);
    waitForRudpPortFree(deadline);
    if (Thread.currentThread().isInterrupted() || deadline - System.nanoTime() <= 0) {
      throw new java.io.InterruptedIOException("IceBridge startup cancelled or timed out");
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
    if (identityFile != null) {
      command.add("--identity-file");
      command.add(identityFile.getAbsolutePath());
    }
    // Child exits when this process dies — no orphan can survive a crash/kill -9.
    command.add("--parent-pid");
    command.add(String.valueOf(ProcessHandle.current().pid()));

    logDir = Files.createTempDirectory("icebridge-launcher-" + controlHttpPort).toFile();
    try {
      tokenFile = createTokenFile(logDir.toPath());
      if (Thread.currentThread().isInterrupted() || deadline - System.nanoTime() <= 0) {
        throw new java.io.InterruptedIOException("IceBridge startup cancelled or timed out");
      }
      command.add("--auth-tokens-file");
      command.add(tokenFile.toString());
      File stdout = new File(logDir, "stdout.log");
      File stderr = new File(logDir, "stderr.log");
      ProcessBuilder pb = new ProcessBuilder(command);
      // Parent owns the local index and application polling; the child only transports.
      pb.environment().put("ICEBRIDGE_SEARCH_APP", "false");
      // Never inherit another control credential or let it override this launch's file.
      pb.environment().remove("ICEBRIDGE_AUTH_TOKEN");
      pb.environment().remove("ICEBRIDGE_AUTH_TOKEN_FILE");
      pb.environment().remove("ICEBRIDGE_AUTH_TOKENS_FILE");
      pb.environment().remove("ICEBRIDGE_TOKENS_FILE");
      pb.redirectOutput(stdout);
      pb.redirectError(stderr);
      LOG.info("Starting IceBridge: " + String.join(" ", command));
      process = pb.start();
      Path launchTokenFile = tokenFile;
      process.onExit().thenRun(() -> deleteTokenFile(launchTokenFile));
      writePidFile(process.pid());
      if (client == null) {
        client = new IceBridgeClient(controlHttpPort);
        client.setAuthToken(authToken);
      }
    } catch (IOException | RuntimeException e) {
      stopProcess();
      throw e;
    }
  }

  private Path createTokenFile(Path directory) throws IOException {
    Path path;
    if (Files.getFileAttributeView(directory, PosixFileAttributeView.class) != null) {
      path =
          Files.createTempFile(
              directory,
              "auth-tokens-",
              ".txt",
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    } else {
      path = Files.createTempFile(directory, "auth-tokens-", ".txt");
      try {
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null) {
          throw new IOException("Cannot restrict IceBridge token file permissions");
        }
        acl.setAcl(
            List.of(
                AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(Files.getOwner(path))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build()));
      } catch (IOException | RuntimeException e) {
        Files.deleteIfExists(path);
        throw e;
      }
    }
    try {
      // No credential bytes are written until permissions have been restricted.
      Files.writeString(path, authToken + "\n");
      return path;
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(path);
      throw e;
    }
  }

  private static void deleteTokenFile(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      LOG.warn("Failed to delete IceBridge launch credential file", e);
    }
  }

  /** Wait for readiness within one monotonic deadline, including each health request. */
  public boolean awaitHealthy(long timeoutMs) throws InterruptedException {
    if (timeoutMs <= 0) {
      throw new IllegalArgumentException("timeoutMs must be > 0");
    }
    return awaitHealthyUntil(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs));
  }

  /** Start the child and await readiness using one budget, in milliseconds. */
  public boolean startAndAwaitHealthy(long timeoutMs) throws IOException, InterruptedException {
    if (timeoutMs <= 0) {
      throw new IllegalArgumentException("timeoutMs must be > 0");
    }
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    startUntil(deadline);
    return awaitHealthyUntil(deadline);
  }

  private boolean awaitHealthyUntil(long deadline) throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException();
    }
    while (isAlive()) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      }
      long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
      if (remainingMs <= 0) {
        return false;
      }
      IceBridgeClient currentClient = client;
      if (currentClient != null && currentClient.health(remainingMs)) {
        return System.nanoTime() - deadline < 0;
      }
      long remainingNanos = deadline - System.nanoTime();
      if (remainingNanos <= 0) {
        return false;
      }
      TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(100), remainingNanos));
    }
    return false;
  }

  /** Gracefully stop the IceBridge process and its supervision. */
  @Override
  public synchronized void close() {
    stopSupervision();
    if (client != null) {
      client.close();
      client = null;
    }
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
    deleteTokenFile(tokenFile);
    tokenFile = null;
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
   * names our JAR and identity; never terminate a child whose recorded parent is still alive.
   * Fail-safe: unknown/dead/foreign pids are ignored.
   */
  private void killStaleIceBridgeChild(long deadline) {
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
      List<String> arguments = List.of(stale.info().arguments().orElse(new String[0]));
      int jarArgument = arguments.indexOf("-jar");
      int identityArgument = arguments.indexOf("--identity-file");
      if (jarArgument < 0
          || jarArgument + 1 >= arguments.size()
          || identityArgument < 0
          || identityArgument + 1 >= arguments.size()
          || !new File(arguments.get(jarArgument + 1))
              .getCanonicalFile()
              .equals(jarPath.getCanonicalFile())
          || !new File(arguments.get(identityArgument + 1))
              .getCanonicalFile()
              .equals(identityFile.getCanonicalFile())) {
        return;
      }
      int parentArgument = arguments.indexOf("--parent-pid");
      if (parentArgument >= 0) {
        if (parentArgument + 1 >= arguments.size()) {
          return;
        }
        long parentPid = Long.parseLong(arguments.get(parentArgument + 1));
        if (parentPid <= 0
            || ProcessHandle.of(parentPid).map(ProcessHandle::isAlive).orElse(false)) {
          return;
        }
      }
      if (Thread.currentThread().isInterrupted() || deadline - System.nanoTime() <= 0) {
        return;
      }
      LOG.warn("Killing stale IceBridge child pid " + pid + " left by a previous session");
      stale.destroy();
      if (!stale
          .onExit()
          .handle((p, t) -> p.isAlive())
          .get(
              Math.max(1, Math.min(TimeUnit.SECONDS.toNanos(5), deadline - System.nanoTime())),
              TimeUnit.NANOSECONDS)) {
        return;
      }
      stale.destroyForcibly();
      stale
          .onExit()
          .get(
              Math.max(1, Math.min(TimeUnit.SECONDS.toNanos(2), deadline - System.nanoTime())),
              TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Throwable t) {
      LOG.warn("Stale IceBridge child cleanup failed (continuing)", t);
    }
  }

  /** Wait until our configured rUDP port is bindable again after a stale kill. */
  private void waitForRudpPortFree(long startupDeadline) {
    long deadline =
        System.nanoTime()
            + Math.min(
                TimeUnit.SECONDS.toNanos(5), Math.max(0, startupDeadline - System.nanoTime()));
    while (deadline - System.nanoTime() > 0) {
      try (java.net.DatagramSocket probe = new java.net.DatagramSocket(rudpPort)) {
        return; // port is free
      } catch (IOException notYetFree) {
        try {
          long remaining = deadline - System.nanoTime();
          if (remaining > 0) {
            TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(200), remaining));
          }
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
    try (var paths = Files.walk(logDir.toPath())) {
      paths
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
    Process current = process;
    return current != null && current.isAlive();
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
                  IceBridgeClient currentClient = client;
                  if (isAlive() && currentClient != null && currentClient.health()) {
                    continue;
                  }
                  synchronized (IceBridgeProcessLauncher.this) {
                    if (!supervising || supervisor != Thread.currentThread()) {
                      return;
                    }
                    LOG.warn(
                        "IceBridge child unhealthy (alive="
                            + isAlive()
                            + ") - respawning under supervision");
                    stopProcess();
                    start();
                  }
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
