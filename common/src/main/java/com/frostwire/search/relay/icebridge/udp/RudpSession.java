/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * State for one rUDP association between two IceBridge servents.
 *
 * <p>Sequence numbers are treated as <em>unsigned</em> 32-bit integers
 * (0..4294967295) for comparison purposes, using
 * {@link Integer#compareUnsigned}. This prevents a deadlock at the
 * {@code Integer.MAX_VALUE → Integer.MIN_VALUE} boundary where signed
 * comparison would reject the wrapped sequence as a duplicate.
 */
final class RudpSession {

    private final long localConnectionId;
    private final long remoteConnectionId;
    private final InetSocketAddress remoteAddress;
    /** Set on inbound HELLO or when HELLO_ACK proves the peer's pub. */
    private volatile byte[] remotePub;
    private final boolean weAreInitiator;

    private volatile long lastActivityMs;
    private final AtomicInteger nextLocalSeq = new AtomicInteger(1);
    private final AtomicInteger ackedThroughLocal = new AtomicInteger(0);
    private final AtomicInteger receivedThroughRemote = new AtomicInteger(0);

    /**
     * Pending packets keyed by sequence number, using unsigned comparison
     * so that {@code headMap(ackThrough, true)} correctly clears entries
     * across the signed-int wrap boundary.
     */
    private final ConcurrentNavigableMap<Integer, PendingPacket> pending =
            new ConcurrentSkipListMap<>(Integer::compareUnsigned);

    RudpSession(long localConnectionId,
                long remoteConnectionId,
                InetSocketAddress remoteAddress,
                byte[] remotePub,
                boolean weAreInitiator) {
        this.localConnectionId = localConnectionId;
        this.remoteConnectionId = remoteConnectionId;
        this.remoteAddress = remoteAddress;
        this.remotePub = remotePub == null ? null : remotePub.clone();
        this.weAreInitiator = weAreInitiator;
        this.lastActivityMs = System.currentTimeMillis();
    }

    long localConnectionId() {
        return localConnectionId;
    }

    long remoteConnectionId() {
        return remoteConnectionId;
    }

    InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    byte[] remotePub() {
        byte[] p = remotePub;
        return p == null ? null : p.clone();
    }

    /**
     * Record the authenticated peer public key (inbound HELLO or HELLO_ACK).
     * First non-null value wins; subsequent mismatches are ignored.
     */
    void setRemotePub(byte[] pub) {
        if (pub == null || pub.length != 32) {
            return;
        }
        if (this.remotePub == null) {
            this.remotePub = pub.clone();
        }
    }

    boolean weAreInitiator() {
        return weAreInitiator;
    }

    void markActivity() {
        lastActivityMs = System.currentTimeMillis();
    }

    long lastActivityMs() {
        return lastActivityMs;
    }

    int nextLocalSequence() {
        return nextLocalSeq.getAndIncrement();
    }

    int ackedThroughLocal() {
        return ackedThroughLocal.get();
    }

    /**
     * Mark local packets as acknowledged up to and including
     * {@code ackThrough} (unsigned comparison). Removes acknowledged
     * entries from the pending map.
     */
    void ackLocal(int ackThrough) {
        int current;
        do {
            current = ackedThroughLocal.get();
            if (Integer.compareUnsigned(ackThrough, current) <= 0) {
                return;
            }
        } while (!ackedThroughLocal.compareAndSet(current, ackThrough));
        pending.headMap(ackThrough, true).clear();
    }

    int receivedThroughRemote() {
        return receivedThroughRemote.get();
    }

    /**
     * Accept an inbound data packet's sequence number if it is the next
     * expected one (in-order delivery). Uses unsigned comparison so that
     * the {@code MAX_VALUE → MIN_VALUE} wrap is handled correctly.
     *
     * @return true if the packet is new and in-order, false if duplicate or gap
     */
    boolean receiveRemote(int sequence) {
        int current;
        do {
            current = receivedThroughRemote.get();
            if (Integer.compareUnsigned(sequence, current) <= 0) {
                return false; // duplicate or old
            }
            // Check for in-order: sequence must be exactly current + 1.
            // Integer overflow wraps MAX_VALUE+1 → MIN_VALUE, which is
            // the correct "next" value under unsigned semantics.
            if (sequence != current + 1) {
                return false; // gap — v1 requires in-order
            }
        } while (!receivedThroughRemote.compareAndSet(current, sequence));
        markActivity();
        return true;
    }

    void addPending(int sequence, PendingPacket packet) {
        pending.put(sequence, packet);
    }

    ConcurrentNavigableMap<Integer, PendingPacket> pending() {
        return pending;
    }

    /** Prepare an ack packet for the highest contiguous received sequence. */
    RudpPacket dataAck() {
        return new RudpPacket(RudpPacket.Type.DATA_ACK, remoteConnectionId,
                0, receivedThroughRemote.get(), new byte[0]);
    }

    /** Prepare a data packet with the next local sequence. */
    RudpPacket data(byte[] payload) {
        return new RudpPacket(RudpPacket.Type.DATA, remoteConnectionId,
                nextLocalSequence(), receivedThroughRemote.get(), payload);
    }

    /**
     * Prepare a HELLO_ACK carrying this node's signed identity so the
     * initiator can learn remotePub (same payload shape as HELLO).
     */
    RudpPacket helloAck(byte[] signedHelloPayload) {
        if (signedHelloPayload == null) {
            signedHelloPayload = new byte[0];
        }
        return new RudpPacket(RudpPacket.Type.HELLO_ACK, remoteConnectionId,
                0, 0, signedHelloPayload);
    }
}
