/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.relay.icebridge.client.IceBridgeClient;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link PeerProber} backed by the IceBridge daemon: asks it to open an rUDP identity-discovery
 * handshake with the endpoint and polls until the peer has proven its key, the budget runs out
 * (the handshake is cancelled so a dead endpoint holds no session) or the caller is interrupted.
 */
public final class RudpPeerProber implements PeerProber {

    /** What the prober needs from the daemon; {@link IceBridgeClient} is the production one. */
    public interface Daemon {
        IceBridgeClient.ProbeOutcome probe(String host, int port, boolean cancel);
    }

    /** A handshake that has not completed after this long is not going to (retransmits give up). */
    public static final long MAX_PROBE_MS = 3_000;
    private static final long POLL_INTERVAL_MS = 100;

    private final Daemon daemon;

    public RudpPeerProber(IceBridgeClient client) {
        this(client::probe);
    }

    public RudpPeerProber(Daemon daemon) {
        if (daemon == null) {
            throw new IllegalArgumentException("daemon is null");
        }
        this.daemon = daemon;
    }

    @Override
    public Optional<ProbedPeer> probe(String host, int port, long deadlineNanos) {
        if (host == null || host.isEmpty() || port <= 0 || port > 65535) {
            return Optional.empty();
        }
        long budgetEnd = Math.min(deadlineNanos, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MAX_PROBE_MS));
        try {
            while (!Thread.currentThread().isInterrupted() && budgetEnd - System.nanoTime() > 0) {
                IceBridgeClient.ProbeOutcome outcome = daemon.probe(host, port, false);
                switch (outcome.state()) {
                    case ESTABLISHED:
                        return Optional.of(new ProbedPeer(outcome.pub()));
                    case NONE:
                        // Invalid endpoint or the daemon refused (limits): nothing to wait for.
                        return Optional.empty();
                    default:
                        break;
                }
                long wait = Math.min(POLL_INTERVAL_MS,
                        TimeUnit.NANOSECONDS.toMillis(budgetEnd - System.nanoTime()));
                if (wait <= 0) {
                    break;
                }
                Thread.sleep(wait);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // fall through to the cancel below
        }
        cancel(host, port);
        return Optional.empty();
    }

    private void cancel(String host, int port) {
        try {
            daemon.probe(host, port, true);
        } catch (RuntimeException ignored) {
            // best effort: the daemon drops unanswered handshakes on its own after 15s
        }
    }
}
