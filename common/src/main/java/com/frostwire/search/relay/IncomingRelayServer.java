/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain-TCP server that accepts relay search requests and
 * dispatches them to a {@link RelayRole}. Each connection runs
 * in a worker thread from a fixed pool.
 *
 * <p>Frame format: see {@link RelayWireCodec}. One frame per
 * connection; the server reads the request, dispatches it, writes
 * the response, and closes the connection. No long-lived
 * connections.
 *
 * <p><b>Threading:</b> {@link #start()} launches an accept loop
 * in a dedicated thread that hands each accepted socket to a
 * worker pool. {@link #stop()} shuts the server socket down and
 * terminates the pool; in-flight handlers finish or are
 * interrupted.
 *
 * <p><b>Fail-closed:</b> any read/write/dispatch error logs and
 * closes the connection. A bad request from a peer cannot crash
 * the server.
 *
 * <p>uTP is a future optimization. For now, plain TCP is enough
 * to prove the protocol end-to-end and run integration tests.
 */
public final class IncomingRelayServer {

    private static final Logger LOG = Logger.getLogger(IncomingRelayServer.class);

    private static final int DEFAULT_BACKLOG = 64;
    private static final int DEFAULT_WORKER_POOL_SIZE = 8;
    private static final int DEFAULT_SO_TIMEOUT_MS = 30_000;

    private final RelayRole role;
    private final IdentityRecord identityRecord;
    private final int port;
    private final int backlog;
    private final int workerPoolSize;
    private final int soTimeoutMs;
    private final String bindHost;
    private final AtomicInteger connectionCount = new AtomicInteger();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService workerPool;
    private volatile boolean running;

    public IncomingRelayServer(RelayRole role, int port) {
        this(role, null, port);
    }

    public IncomingRelayServer(RelayRole role, IdentityRecord identityRecord, int port) {
        this(role, identityRecord, port, DEFAULT_BACKLOG, DEFAULT_WORKER_POOL_SIZE, DEFAULT_SO_TIMEOUT_MS);
    }

    /**
     * Identity-only constructor for standalone forwarders (e.g. cloud
     * IceBridge relays) that don't have a LocalIndex or PeerDirectory.
     * Only serves identity handshakes; rejects all search requests.
     */
    public IncomingRelayServer(IdentityRecord identityRecord, int port) {
        this(null, identityRecord, port, DEFAULT_BACKLOG, DEFAULT_WORKER_POOL_SIZE, DEFAULT_SO_TIMEOUT_MS);
    }

    public IncomingRelayServer(RelayRole role, IdentityRecord identityRecord, int port, String bindHost) {
        this(role, identityRecord, port, DEFAULT_BACKLOG, DEFAULT_WORKER_POOL_SIZE, DEFAULT_SO_TIMEOUT_MS, bindHost);
    }

    public IncomingRelayServer(IdentityRecord identityRecord, int port, String bindHost) {
        this(null, identityRecord, port, DEFAULT_BACKLOG, DEFAULT_WORKER_POOL_SIZE, DEFAULT_SO_TIMEOUT_MS, bindHost);
    }

    public IncomingRelayServer(RelayRole role, int port, int backlog,
                               int workerPoolSize, int soTimeoutMs) {
        this(role, null, port, backlog, workerPoolSize, soTimeoutMs);
    }

    public IncomingRelayServer(RelayRole role, IdentityRecord identityRecord, int port, int backlog,
                               int workerPoolSize, int soTimeoutMs) {
        this(role, identityRecord, port, backlog, workerPoolSize, soTimeoutMs, null);
    }

    public IncomingRelayServer(RelayRole role, IdentityRecord identityRecord, int port, int backlog,
                               int workerPoolSize, int soTimeoutMs, String bindHost) {
        if (role == null && identityRecord == null) {
            throw new IllegalArgumentException("either role or identityRecord must be non-null");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
        if (backlog <= 0) {
            throw new IllegalArgumentException("backlog must be > 0");
        }
        if (workerPoolSize <= 0) {
            throw new IllegalArgumentException("workerPoolSize must be > 0");
        }
        if (soTimeoutMs < 0) {
            throw new IllegalArgumentException("soTimeoutMs must be >= 0");
        }
        this.role = role;
        this.identityRecord = identityRecord;
        this.port = port;
        this.backlog = backlog;
        this.workerPoolSize = workerPoolSize;
        this.soTimeoutMs = soTimeoutMs;
        this.bindHost = bindHost;
    }

    /**
     * Bind the server socket and start accepting connections.
     * Idempotent: subsequent calls are no-ops.
     */
    public void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        if (bindHost != null && !bindHost.isEmpty()) {
            serverSocket.bind(new InetSocketAddress(bindHost, port), backlog);
        } else {
            serverSocket.bind(new InetSocketAddress(port), backlog);
        }
        running = true;
        workerPool = Executors.newFixedThreadPool(workerPoolSize,
                new WorkerThreadFactory());
        acceptThread = new Thread(this::acceptLoop, "relay-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        String listenAddr = (bindHost != null && !bindHost.isEmpty()) ? bindHost : "0.0.0.0";
        LOG.info("IncomingRelayServer listening on " + listenAddr + ":" + port);
    }

    /**
     * Stop accepting new connections and shut down the worker
     * pool. Safe to call from any thread.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.debug("Error closing server socket", e);
        }
        if (acceptThread != null) {
            try {
                acceptThread.interrupt();
            } catch (SecurityException ignored) {
            }
        }
        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                workerPool.shutdownNow();
            }
        }
        LOG.info("IncomingRelayServer stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int port() {
        return serverSocket == null ? port : serverSocket.getLocalPort();
    }

    public int connectionCount() {
        return connectionCount.get();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                connectionCount.incrementAndGet();
                try {
                    workerPool.execute(() -> handleConnection(socket));
                } catch (RejectedExecutionException e) {
                    closeQuietly(socket);
                }
            } catch (IOException e) {
                if (running) {
                    LOG.debug("Accept failed", e);
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            socket.setSoTimeout(soTimeoutMs);
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {
                byte[] frame = RelayWireCodec.readFrame(in);
                if (frame == null) {
                    LOG.debug("Empty frame; closing");
                    return;
                }
                if (RelayWireCodec.isIdentityRequest(frame)) {
                    handleIdentityRequest(out);
                    return;
                }
                if (role == null) {
                    LOG.debug("Search request received but no role configured (identity-only server)");
                    return;
                }
                RemoteSearchRequest request = RelayWireCodec.decodeRequest(frame);
                if (request == null) {
                    LOG.debug("Invalid request frame; closing");
                    return;
                }
                java.util.Optional<RemoteSearchResponse> response = role.handleRequest(request);
                if (response.isEmpty()) {
                    return; // rejected silently
                }
                RelayWireCodec.writeResponse(out, response.get());
            }
        } catch (Throwable t) {
            LOG.debug("Connection handler error", t);
        } finally {
            closeQuietly(socket);
        }
    }

    private void handleIdentityRequest(OutputStream out) throws IOException {
        if (identityRecord == null) {
            LOG.debug("Identity request received but no identity record configured");
            return;
        }
        RelayWireCodec.writeIdentityRecord(out, identityRecord);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static final class WorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "relay-server-worker-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
