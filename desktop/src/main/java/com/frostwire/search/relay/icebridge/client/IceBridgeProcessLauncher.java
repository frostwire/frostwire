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
 * Starts and stops the IceBridge daemon as an external process for the
 * FrostWire desktop client.
 *
 * <p>The launcher picks free ports if none are supplied, builds the command
 * line for {@code icebridge.jar}, and exposes a ready-to-use
 * {@link IceBridgeClient} pointing at the control port.
 */
public final class IceBridgeProcessLauncher implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(IceBridgeProcessLauncher.class);

    private final File jarPath;
    private final File identityFile;
    private final int controlHttpPort;
    private final int rudpPort;
    private final String role;

    private Process process;
    private IceBridgeClient client;
    private File logDir;

    /**
     * Construct a launcher with explicit ports (use 0 to auto-select).
     */
    public IceBridgeProcessLauncher(File jarPath,
                                    File identityFile,
                                    int controlHttpPort,
                                    int rudpPort,
                                    String role) {
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
        this.role = role == null || role.isEmpty() ? "BOTH" : role;
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

    /**
     * Start the IceBridge process. Daemon stdout/stderr are redirected to
     * files under a temporary directory so the subprocess cannot block on a
     * shared Gradle worker pipe.
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
        command.add("--control-http-port");
        command.add(String.valueOf(controlHttpPort));
        command.add("--role");
        command.add(role);
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
    }

    /**
     * Gracefully stop the IceBridge process.
     */
    @Override
    public synchronized void close() {
        if (process != null && process.isAlive()) {
            LOG.info("Stopping IceBridge process");
            process.destroy();
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        process = null;
        client = null;
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
