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
 */
final class RudpSession {

    private final long localConnectionId;
    private final long remoteConnectionId;
    private final InetSocketAddress remoteAddress;
    private final byte[] remotePub;
    private final boolean weAreInitiator;

    private volatile long lastActivityMs;
    private final AtomicInteger nextLocalSeq = new AtomicInteger(1);
    private final AtomicInteger ackedThroughLocal = new AtomicInteger(0);
    private final AtomicInteger receivedThroughRemote = new AtomicInteger(0);
    private final ConcurrentNavigableMap<Integer, PendingPacket> pending = new ConcurrentSkipListMap<>();

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
        return remotePub == null ? null : remotePub.clone();
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

    void ackLocal(int ackThrough) {
        int current;
        do {
            current = ackedThroughLocal.get();
            if (ackThrough <= current) {
                return;
            }
        } while (!ackedThroughLocal.compareAndSet(current, ackThrough));
        pending.headMap(ackThrough, true).clear();
    }

    int receivedThroughRemote() {
        return receivedThroughRemote.get();
    }

    boolean receiveRemote(int sequence) {
        int current;
        do {
            current = receivedThroughRemote.get();
            if (sequence <= current) {
                return false; // duplicate or old
            }
            if (sequence != current + 1) {
                // v1: require in-order; drop gap
                return false;
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

    /** Prepare a hello ack for an inbound hello. */
    RudpPacket helloAck() {
        return new RudpPacket(RudpPacket.Type.HELLO_ACK, remoteConnectionId,
                0, 0, new byte[0]);
    }
}