/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Authenticates a peer by connecting over TCP and fetching its
 * {@link IdentityRecord} via the relay identity handshake. The
 * record's self-signature and a fresh, peer-bound possession proof
 * are verified before the endpoint is considered valid.
 *
 * <p>This is the production authenticator for the direct TCP
 * peer-search protocol: a discovered endpoint is not registered
 * in the {@link PeerDirectory} until its identity record has been
 * fetched and verified.
 */
public final class DirectTcpPeerAuthenticator implements PeerAuthenticator, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(DirectTcpPeerAuthenticator.class);

    // Process-wide cap also bounds uninterruptible DNS work. Idle daemon workers retire;
    // instances own/cancel their calls, but never shut down another instance's pool.
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(4, 4, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> {
                Thread thread = new Thread(r, "relay-identity-probe");
                thread.setDaemon(true);
                return thread;
            });
    static {
        WORKERS.allowCoreThreadTimeOut(true);
    }
    private static final SecureRandom RANDOM = new SecureRandom();
    private final KeyPair identityKeys;
    private final int timeoutMs;
    private final Map<Socket, FutureTask<Optional<IdentityRecord>>> calls = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /** Legacy construction disables authentication; a public record is not a possession proof. */
    public DirectTcpPeerAuthenticator() {
        this((KeyPair) null, PeerDiscovery.DEFAULT_IDENTITY_TIMEOUT_MS);
    }

    public DirectTcpPeerAuthenticator(OutgoingRelayClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client is null");
        }
        this.identityKeys = null;
        this.timeoutMs = PeerDiscovery.DEFAULT_IDENTITY_TIMEOUT_MS;
    }

    public DirectTcpPeerAuthenticator(KeyPair identityKeys) {
        this(identityKeys, PeerDiscovery.DEFAULT_IDENTITY_TIMEOUT_MS);
    }

    /** Overall timeout in milliseconds, including queueing, DNS, connect, writes and reads. */
    public DirectTcpPeerAuthenticator(KeyPair identityKeys, int timeoutMs) {
        if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be > 0");
        this.identityKeys = identityKeys;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Optional<IdentityRecord> authenticate(String host, int port) {
        return authenticate(host, port, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs));
    }

    Optional<IdentityRecord> authenticate(String host, int port, long passDeadlineNanos) {
        if (closed || identityKeys == null || host == null || port <= 0 || port > 65535) {
            return Optional.empty();
        }
        long now = System.nanoTime();
        long budget = Math.min(TimeUnit.MILLISECONDS.toNanos(timeoutMs), passDeadlineNanos - now);
        if (budget <= 0 || Thread.currentThread().isInterrupted()) return Optional.empty();
        long deadline = now + budget;
        Socket socket = new Socket();
        FutureTask<Optional<IdentityRecord>> call = new FutureTask<>(() -> {
            if (deadline - System.nanoTime() <= 0 || socket.isClosed()
                    || Thread.currentThread().isInterrupted()) return Optional.empty();
            InetSocketAddress address = new InetSocketAddress(host, port);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || socket.isClosed() || Thread.currentThread().isInterrupted()) {
                return Optional.empty();
            }
            socket.connect(address, (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
            byte[] nonce = new byte[32];
            RANDOM.nextBytes(nonce);
            byte[] challenge = RelayWireCodec.identityChallenge(identityKeys, nonce);
            RelayWireCodec.writeFrame(socket.getOutputStream(), challenge);
            byte[] proof = RelayWireCodec.readFrame(socket, deadline, RelayWireCodec.MAX_IDENTITY_PROOF_BYTES);
            return Optional.ofNullable(RelayWireCodec.verifyIdentityProof(challenge, proof));
        });
        try {
            synchronized (this) {
                if (closed || calls.size() >= 32) return Optional.empty();
                calls.put(socket, call);
            }
            WORKERS.execute(call);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return Optional.empty();
            Optional<IdentityRecord> result = call.get(remaining, TimeUnit.NANOSECONDS);
            return !closed && System.nanoTime() - deadline < 0 ? result : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (java.util.concurrent.TimeoutException e) {
            LOG.debug("Identity possession proof timed out for " + host + ":" + port);
            return Optional.empty();
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.net.ConnectException) {
                LOG.debug("Identity peer refused connection at " + host + ":" + port);
            } else if (cause instanceof java.net.SocketTimeoutException) {
                LOG.debug("Identity peer did not answer at " + host + ":" + port);
            } else if (cause instanceof java.net.UnknownHostException) {
                LOG.debug("Identity peer host did not resolve: " + host);
            } else if (cause instanceof java.io.IOException) {
                LOG.debug("Identity peer unreachable at " + host + ":" + port);
            } else {
                LOG.debug(
                        "Identity possession proof unavailable for " + host + ":" + port, e);
            }
            return Optional.empty();
        } catch (Exception e) {
            LOG.debug("Identity possession proof unavailable for " + host + ":" + port, e);
            return Optional.empty();
        } finally {
            call.cancel(true);
            WORKERS.remove(call);
            calls.remove(socket);
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        for (Map.Entry<Socket, FutureTask<Optional<IdentityRecord>>> entry : calls.entrySet()) {
            entry.getValue().cancel(true);
            WORKERS.remove(entry.getValue());
            try { entry.getKey().close(); } catch (IOException ignored) { }
        }
        calls.clear();
    }
}
