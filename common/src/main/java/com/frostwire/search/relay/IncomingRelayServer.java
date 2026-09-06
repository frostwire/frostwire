/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PrivateKey;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
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
    private final PrivateKey identityKey;
    private final int port;
    private final int backlog;
    private final int workerPoolSize;
    private final int soTimeoutMs;
    private final String bindHost;
    private final AtomicInteger connectionCount = new AtomicInteger();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ThreadPoolExecutor workerPool;
    private ScheduledThreadPoolExecutor deadlinePool;
    private final Map<Socket, ScheduledFuture<?>> sockets = new ConcurrentHashMap<>();
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
     * Without a private key, identity authentication fails closed. Use the keyed
     * overload to serve possession proofs. Rejects all search requests.
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

    public IncomingRelayServer(RelayRole role, IdentityRecord record, PrivateKey identityKey, int port) {
        this(role, record, identityKey, port, null);
    }

    public IncomingRelayServer(RelayRole role, IdentityRecord record, PrivateKey identityKey,
                               int port, String bindHost) {
        this(role, record, identityKey, port, DEFAULT_BACKLOG, DEFAULT_WORKER_POOL_SIZE,
                DEFAULT_SO_TIMEOUT_MS, bindHost);
    }

    public IncomingRelayServer(IdentityRecord record, PrivateKey identityKey, int port) {
        this(null, record, identityKey, port, null);
    }

    public IncomingRelayServer(IdentityRecord record, PrivateKey identityKey, int port, String bindHost) {
        this(null, record, identityKey, port, bindHost);
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
        this(role, identityRecord, null, port, backlog, workerPoolSize, soTimeoutMs, bindHost);
    }

    /** Timeout is an absolute connection budget in milliseconds; zero uses the bounded default. */
    public IncomingRelayServer(RelayRole role, IdentityRecord identityRecord, PrivateKey identityKey,
                               int port, int backlog, int workerPoolSize, int soTimeoutMs, String bindHost) {
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
        this.identityKey = identityKey;
        this.port = port;
        this.backlog = backlog;
        this.workerPoolSize = workerPoolSize;
        this.soTimeoutMs = soTimeoutMs == 0 ? DEFAULT_SO_TIMEOUT_MS : soTimeoutMs;
        this.bindHost = bindHost;
    }

    /**
     * Bind the server socket and start accepting connections.
     * Idempotent: subsequent calls are no-ops.
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        if (workerPool != null && !workerPool.isTerminated()) {
            throw new IOException("previous relay workers are still stopping");
        }
        serverSocket = new ServerSocket();
        try {
            serverSocket.setReuseAddress(true);
            if (bindHost != null && !bindHost.isEmpty()) {
                serverSocket.bind(new InetSocketAddress(bindHost, port), backlog);
            } else {
                serverSocket.bind(new InetSocketAddress(port), backlog);
            }
            workerPool = new ThreadPoolExecutor(workerPoolSize, workerPoolSize, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(backlog), new WorkerThreadFactory());
            deadlinePool = new ScheduledThreadPoolExecutor(1, r -> {
                Thread thread = new Thread(r, "relay-server-deadlines");
                thread.setDaemon(true);
                return thread;
            });
            deadlinePool.setRemoveOnCancelPolicy(true);
            running = true;
            ServerSocket listener = serverSocket;
            ThreadPoolExecutor workers = workerPool;
            acceptThread = new Thread(() -> acceptLoop(listener, workers), "relay-server-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            String listenAddr = (bindHost != null && !bindHost.isEmpty()) ? bindHost : "0.0.0.0";
            LOG.info("IncomingRelayServer listening on " + listenAddr + ":" + port);
        } catch (IOException | RuntimeException e) {
            serverSocket.close();
            if (workerPool != null) workerPool.shutdownNow();
            if (deadlinePool != null) deadlinePool.shutdownNow();
            running = false;
            throw e;
        }
    }

    /**
     * Stop accepting new connections and shut down the worker
     * pool. Safe to call from any thread.
     */
    public synchronized void stop() {
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
            workerPool.shutdownNow();
        }
        for (Socket socket : sockets.keySet()) release(socket);
        sockets.clear();
        if (deadlinePool != null) deadlinePool.shutdownNow();
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

    /** Includes queued and executing sockets, not the cumulative accept count. */
    public int activeConnectionCount() {
        return sockets.size();
    }

    private void acceptLoop(ServerSocket listener, ThreadPoolExecutor workers) {
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                connectionCount.incrementAndGet();
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(soTimeoutMs);
                // Serializes admission against stop; no socket can escape shutdown's close sweep.
                synchronized (this) {
                    if (!running || listener != serverSocket
                            || sockets.size() >= (long) workerPoolSize + backlog) {
                        closeQuietly(socket);
                        continue;
                    }
                    sockets.put(socket, deadlinePool.schedule(() -> closeQuietly(socket),
                            Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
                }
                try {
                    socket.setSoTimeout(soTimeoutMs);
                    workers.execute(() -> handleConnection(socket, deadline));
                } catch (RejectedExecutionException e) {
                    release(socket);
                } catch (IOException e) {
                    release(socket);
                }
            } catch (IOException e) {
                if (running) {
                    LOG.debug("Accept failed", e);
                }
            }
        }
    }

    private void handleConnection(Socket socket, long deadline) {
        try {
            try (OutputStream out = socket.getOutputStream()) {
                byte[] frame = RelayWireCodec.readFrame(socket, deadline,
                        role == null ? RelayWireCodec.MAX_IDENTITY_PROOF_BYTES : RelayWireCodec.MAX_FRAME_BYTES);
                if (frame == null) {
                    LOG.debug("Empty frame; closing");
                    return;
                }
                if (RelayWireCodec.isIdentityChallenge(frame)) {
                    if (identityRecord != null && identityKey != null
                            && !socket.isClosed() && System.nanoTime() - deadline < 0) {
                        RelayWireCodec.writeFrame(out, RelayWireCodec.identityProof(frame, identityRecord, identityKey));
                    }
                    return;
                }
                if (RelayWireCodec.isIdentityRequest(frame)) {
                    // Legacy public-record probes cannot establish endpoint possession.
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
                if (socket.isClosed() || System.nanoTime() - deadline >= 0) return;
                java.util.Optional<RemoteSearchResponse> response = role.handleRequest(request);
                if (response.isEmpty()) {
                    return; // rejected silently
                }
                if (!socket.isClosed() && System.nanoTime() - deadline < 0) {
                    RelayWireCodec.writeResponse(out, response.get());
                }
            }
        } catch (Throwable t) {
            // Internet scanners / BitTorrent clients often hit TCP 6888 with non-FW frames
            // (e.g. BT handshake 0x13 'B' 'i' 't'… decodes as a huge length). Expected noise.
            String msg = t.getMessage();
            if (msg != null && msg.contains("not speaking the FrostWire relay protocol")) {
                LOG.debug("Ignoring non-protocol probe on identity port from "
                        + socket.getRemoteSocketAddress() + ": " + msg);
            } else {
                LOG.debug("Connection handler error from " + socket.getRemoteSocketAddress(), t);
            }
        } finally {
            release(socket);
        }
    }

    private void release(Socket socket) {
        closeQuietly(socket);
        ScheduledFuture<?> deadline = sockets.remove(socket);
        if (deadline != null) deadline.cancel(false);
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
