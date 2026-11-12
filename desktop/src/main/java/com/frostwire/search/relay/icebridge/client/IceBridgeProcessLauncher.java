/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.icebridge.IceBridgeControlPortAnnouncement;
import com.frostwire.util.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * <p>The launcher lets the child's own binds pick the ports that are not supplied, builds the
 * command line for {@code icebridge.jar}, and exposes a ready-to-use {@link IceBridgeClient}
 * pointing at the control port.
 */
public final class IceBridgeProcessLauncher implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(IceBridgeProcessLauncher.class);

  private final File jarPath;
  private final File identityFile;

  /** The caller's control port, or 0 for the port the child's bind picks on each start. */
  private final int requestedControlPort;

  private volatile int controlHttpPort;
  private volatile int rudpPort;
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
    if (controlHttpPort > 65535) {
      throw new IllegalArgumentException("controlHttpPort must be at most 65535");
    }
    // Let the child bind the control port and announce it: a port probed here and released can be
    // taken by another process before the child binds it, on every respawn
    // (frostwire/frostwire#1478).
    this.requestedControlPort = Math.max(0, controlHttpPort);
    this.controlHttpPort = requestedControlPort;
    if (rudpPort < 0 || rudpPort > 65535) {
      throw new IllegalArgumentException("rudpPort must be between 0 and 65535");
    }
    // Let the child's real UDP bind atomically select a port; probing a TCP socket
    // selects the wrong transport and leaves a release/rebind race.
    this.rudpPort = rudpPort;
    // relayPort=0 selects embedder mode: the parent owns search (Protocol #1), the child only
    // transports.
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

  /**
   * The control port of the current child. A caller-supplied port is returned as given. With an
   * automatic port this is 0 until {@link #start()} has read the port the child announced, then
   * that port; a respawned child announces its own port, and it is 0 again once the child is
   * stopped.
   */
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

    String java = resolveJavaBinary();
    List<String> command = new ArrayList<>();
    command.add(java);
    command.addAll(jvmOptions(Runtime.getRuntime().availableProcessors()));
    command.add("-jar");
    command.add(jarPath.getAbsolutePath());
    command.add("--rudp-port");
    command.add(String.valueOf(rudpPort));
    command.add("--relay-port");
    command.add(String.valueOf(relayPort));
    command.add("--control-http-port");
    command.add(String.valueOf(requestedControlPort));
    command.add("--role");
    command.add(role);
    command.add("--host");
    command.add(host);
    if (identityFile != null) {
      command.add("--identity-file");
      command.add(identityFile.getAbsolutePath());
    }
    // The parent owns DHT discovery (BTEngine announces our identity on the real identity port). A
    // child announcing too would republish the same identity record with port 0 and fill the
    // shared topics with endpoints that nobody can connect to.
    command.add("--no-dht");
    // Child exits when this process dies — no orphan can survive a crash/kill -9.
    command.add("--parent-pid");
    command.add(String.valueOf(ProcessHandle.current().pid()));

    logDir = Files.createTempDirectory("icebridge-launcher-").toFile();
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
      int port =
          requestedControlPort > 0
              ? requestedControlPort
              : awaitAnnouncedControlPort(process, stdout.toPath(), stderr.toPath(), deadline);
      controlHttpPort = port;
      if (client == null) {
        client = new IceBridgeClient(port);
        client.setAuthToken(authToken);
      } else {
        // The transport and registry sync keep this client across respawns.
        client.setControlPort(port);
      }
    } catch (IOException | RuntimeException e) {
      stopProcess();
      throw e;
    }
  }

  /**
   * Wait for the child's control port announcement on its stdout. Fails as soon as the child exits
   * without one, and when the startup deadline passes or the thread is interrupted.
   */
  private static int awaitAnnouncedControlPort(
      Process child, Path stdout, Path stderr, long deadline) throws IOException {
    while (true) {
      // Check liveness before reading: a child that announced and then exited must not be
      // reported as never having announced.
      boolean exited = !child.isAlive();
      int port = IceBridgeControlPortAnnouncement.parse(readLog(stdout));
      if (port > 0) {
        return port;
      }
      if (exited) {
        throw new IOException(
            "IceBridge child exited with status "
                + child.exitValue()
                + " before announcing its control port; stderr: "
                + tail(readLog(stderr)));
      }
      long remaining = deadline - System.nanoTime();
      if (Thread.currentThread().isInterrupted() || remaining <= 0) {
        throw new java.io.InterruptedIOException(
            "IceBridge startup cancelled or timed out before the child announced its control port");
      }
      try {
        TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(20), remaining));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new java.io.InterruptedIOException(
            "IceBridge startup cancelled before the child announced its control port");
      }
    }
  }

  /**
   * The child's log so far; ISO-8859-1 maps every byte, so a half-written character cannot fail.
   */
  private static String readLog(Path log) throws IOException {
    return Files.isRegularFile(log)
        ? new String(Files.readAllBytes(log), StandardCharsets.ISO_8859_1)
        : "";
  }

  private static String tail(String log) {
    String trimmed = log.trim();
    return trimmed.length() <= 2000 ? trimmed : "..." + trimmed.substring(trimmed.length() - 2000);
  }

  /**
   * JVM options for the child on a computer with one or two CPUs, which is also the computer with
   * little memory to spare. The child is a second JVM next to FrostWire's own, and idles at a small
   * fraction of one CPU, so what it should be is small: one serial collector thread instead of
   * G1's, only the quick compiler, one compiler thread, a heap capped well above what its bounded
   * queues can hold (16 MiB of retained packets, 32 MiB of fragments), small thread stacks.
   * Measured idle on macOS: 95 MB to 72 MB resident and 0.55 s to 0.30 s of CPU in the first 40 s.
   * Other computers keep the JVM defaults. Part of #1512, whose reports come from 1-CPU Windows
   * machines with 50 to 160 MB of free memory.
   *
   * @param cpus the computer's processor count
   */
  static List<String> jvmOptions(int cpus) {
    if (cpus > 2) {
      return List.of();
    }
    return List.of(
        "-XX:+UseSerialGC",
        "-XX:TieredStopAtLevel=1",
        "-XX:CICompilerCount=1",
        "-Xmx256m",
        "-Xss512k",
        "-XX:-UsePerfData");
  }

  /**
   * Resolve the {@code java} binary used to spawn the IceBridge child.
   *
   * <p>Never trusts {@code ProcessHandle.current().info().command()}: native launchers (macOS .app,
   * Windows .exe) boot the JVM in-process via JNI, so the current process image is the application
   * binary itself. Spawning it with {@code -jar} would boot a second full FrostWire instead of the
   * relay daemon — and each copy spawns its own child (fork bomb). Prefer {@code
   * <java.home>/bin/java}; fail closed otherwise.
   */
  static String resolveJavaBinary() throws IOException {
    String javaHome = System.getProperty("java.home", "");
    if (!javaHome.isEmpty()) {
      String exe = com.frostwire.util.OSUtils.isWindows() ? "java.exe" : "java";
      File candidate = new File(javaHome + File.separator + "bin" + File.separator + exe);
      if (isJavaBinary(candidate)) {
        return candidate.getAbsolutePath();
      }
    }
    String current = ProcessHandle.current().info().command().orElse("");
    if (!current.isEmpty() && isJavaBinary(new File(current))) {
      return current;
    }
    throw new IOException(
        "Cannot launch IceBridge child: no java binary found (java.home="
            + javaHome
            + ", current process image="
            + current
            + "). The current image is a native launcher, not java.");
  }

  static boolean isJavaBinary(File f) {
    if (f == null || !f.isFile() || !f.canExecute()) {
      return false;
    }
    String name = f.getName().toLowerCase(java.util.Locale.ROOT);
    return name.equals("java")
        || name.equals("java.exe")
        || name.equals("javaw")
        || name.equals("javaw.exe");
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
    int attempts = 0;
    while (isAlive()) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      }
      long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
      if (remainingMs <= 0) {
        return false;
      }
      IceBridgeClient currentClient = client;
      if (currentClient != null && currentClient.healthQuiet(remainingMs)) {
        if (rudpPort == 0) {
          long portBudgetMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
          int boundPort = currentClient.rudpPortQuiet(portBudgetMs);
          if (boundPort > 0) {
            // Keep the first bound port across supervised child restarts so the
            // parent's signed announcements never refer to a superseded endpoint.
            rudpPort = boundPort;
          }
        }
        if (rudpPort > 0) {
          return System.nanoTime() - deadline < 0;
        }
      }
      long remainingNanos = deadline - System.nanoTime();
      if (remainingNanos <= 0) {
        return false;
      }
      // Back off while the child JVM boots: connection refused is expected,
      // so poll less often the longer startup takes (100ms -> 250ms -> 500ms).
      attempts++;
      long sleepMs = attempts < 4 ? 100 : attempts < 10 ? 250 : 500;
      TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(sleepMs), remainingNanos));
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
    controlHttpPort = requestedControlPort;
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
    if (rudpPort == 0) {
      return;
    }
    long deadline =
        System.nanoTime()
            + Math.min(
                TimeUnit.SECONDS.toNanos(5), Math.max(0, startupDeadline - System.nanoTime()));
    while (deadline - System.nanoTime() > 0) {
      try {
        new java.net.DatagramSocket(rudpPort).close();
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
                  // A fresh JVM needs a readiness budget before the next short-period
                  // health check; otherwise supervision can kill every replacement
                  // before it has finished binding. Do not hold the launch lock here.
                  awaitHealthy(15_000);
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
}
