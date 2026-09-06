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
    private volatile InetSocketAddress remoteAddress;
    /** Set on inbound HELLO or when HELLO_ACK proves the peer's pub. */
    private volatile byte[] remotePub;
    private final boolean weAreInitiator;
    private volatile boolean authenticated;
    byte[] helloPayload;
    byte[] ackPayload;
    byte[] transcript;
    String fragmentKey;
    int nextFragmentIndex;
    InetSocketAddress candidateAddress;
    byte[] pathChallenge;
    long pathChallengeNanos;
    private int highestSent;
    static final int MAX_PENDING_PACKETS = 512;
    static final int MAX_RETAINED_BYTES = 1024 * 1024;

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
        this.lastActivityMs = System.nanoTime() / 1_000_000;
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

    void setRemoteAddress(InetSocketAddress remoteAddress) {
        if (remoteAddress != null) {
            this.remoteAddress = remoteAddress;
        }
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

    boolean isAuthenticated() {
        return authenticated;
    }

    void authenticate() {
        if (remotePub == null || transcript == null) {
            throw new IllegalStateException("Incomplete transcript");
        }
        authenticated = true;
        markActivity();
    }

    void markActivity() {
        lastActivityMs = System.nanoTime() / 1_000_000;
    }

    long lastActivityMs() {
        return lastActivityMs;
    }

    int nextLocalSequence() {
        if (nextLocalSeq.get() == 0) {
            throw new IllegalStateException("Sequence space exhausted; reconnect required");
        }
        return nextLocalSeq.getAndIncrement();
    }

    boolean hasSequenceCapacity(int count) {
        long next = Integer.toUnsignedLong(nextLocalSeq.get());
        return count > 0 && next != 0 && count <= 0x1_0000_0000L - next;
    }

    int ackedThroughLocal() {
        return ackedThroughLocal.get();
    }

    /**
     * Mark local packets as acknowledged up to and including
     * {@code ackThrough} (unsigned comparison). Removes acknowledged
     * entries from the pending map.
     */
    synchronized boolean ackLocal(int ackThrough) {
        if (Integer.compareUnsigned(ackThrough, highestSent) > 0) {
            return false;
        }
        int current;
        do {
            current = ackedThroughLocal.get();
            if (Integer.compareUnsigned(ackThrough, current) <= 0) {
                return true;
            }
        } while (!ackedThroughLocal.compareAndSet(current, ackThrough));
        pending.headMap(ackThrough, true).clear();
        return true;
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
                return false; // gap; retry when the prefix has been admitted
            }
        } while (!receivedThroughRemote.compareAndSet(current, sequence));
        markActivity();
        return true;
    }

    synchronized boolean addPending(int sequence, PendingPacket packet) {
        if (pending.size() >= MAX_PENDING_PACKETS
                || retainedBytes() > MAX_RETAINED_BYTES - 2L * (packet.packet.size() + RudpAuth.SIGNATURE_LENGTH)) {
            return false;
        }
        pending.put(sequence, packet);
        return true;
    }

    synchronized long retainedBytes() {
        long bytes = 0;
        for (PendingPacket packet : pending.values()) {
            // Pending owns both the plaintext packet and its cached signed wire copy.
            bytes += 2L * (packet.packet.size() + RudpAuth.SIGNATURE_LENGTH);
        }
        return bytes;
    }

    synchronized void markSent(int sequence) {
        if (Integer.compareUnsigned(sequence, highestSent) > 0) {
            highestSent = sequence;
        }
    }

    synchronized boolean validAck(int ackThrough) {
        return Integer.compareUnsigned(ackThrough, highestSent) <= 0;
    }

    synchronized void clear() {
        pending.clear();
        authenticated = false;
        transcript = null;
        helloPayload = null;
        ackPayload = null;
        fragmentKey = null;
        candidateAddress = null;
        pathChallenge = null;
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

}
