/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import java.util.Map;
import java.util.TreeMap;

/**
 * Reassembles fragmented rUDP payloads.
 *
 * <p>Each fragment group is identified by a 32-bit id (stored in the
 * {@code ackThrough} field of {@link RudpPacket}). Fragments arrive with
 * a 0-based index in the {@code sequence} field. {@link RudpPacket.Type#DATA_FRAG}
 * marks intermediate fragments; {@link RudpPacket.Type#DATA_END} marks the
 * final fragment. When the final fragment arrives and all preceding fragments
 * are present, the reassembled payload is returned.
 *
 * <p>Thread-safety: all methods are synchronized. The session manager calls
 * these from the Netty event loop thread, so contention is minimal.
 */
final class FragmentReassembler {

    /** Maximum number of concurrent incomplete fragment groups. */
    private static final int MAX_PENDING_GROUPS = 64;

    /** Maximum age of an incomplete fragment group before eviction (ms). */
    private static final long GROUP_TIMEOUT_MS = 30_000;

    /** Maximum number of fragments per group. Prevents DoS via huge fragIndex. */
    static final int MAX_FRAGMENTS_PER_GROUP = 4096;

    /** Maximum total reassembled payload size (16 MB). */
    static final long MAX_ASSEMBLED_SIZE = 16L * 1024 * 1024;

    private final Map<Integer, FragmentGroup> groups = new TreeMap<>();

    /**
     * Add a fragment. Returns the fully reassembled payload if this fragment
     * completes the group, or {@code null} if more fragments are needed.
     *
     * <p>Rejects fragments with negative indices or indices exceeding
     * {@link #MAX_FRAGMENTS_PER_GROUP}. Rejects groups whose total
     * assembled size would exceed {@link #MAX_ASSEMBLED_SIZE}.
     *
     * @param groupId    fragment group id (from packet.ackThrough)
     * @param fragIndex  0-based fragment index (from packet.sequence)
     * @param isLast     true if this is the DATA_END fragment
     * @param payload    the fragment payload bytes
     * @return reassembled payload, or null if incomplete or rejected
     */
    synchronized byte[] addFragment(int groupId, int fragIndex, boolean isLast, byte[] payload) {
        if (fragIndex < 0 || fragIndex >= MAX_FRAGMENTS_PER_GROUP) {
            return null;
        }
        if (payload == null || payload.length == 0) {
            return null;
        }

        if (groups.size() >= MAX_PENDING_GROUPS && !groups.containsKey(groupId)) {
            evictOldest();
        }
        FragmentGroup group = groups.computeIfAbsent(groupId, k -> new FragmentGroup());

        // Reject if total size would exceed the cap.
        long projectedSize = (long) group.totalFragmentBytes() + (long) payload.length;
        if (projectedSize > MAX_ASSEMBLED_SIZE) {
            groups.remove(groupId);
            return null;
        }

        group.add(fragIndex, payload, isLast);
        group.lastUpdatedMs = System.currentTimeMillis();

        if (group.isComplete()) {
            byte[] assembled = group.assemble();
            groups.remove(groupId);
            return assembled;
        }
        return null;
    }

    /**
     * Evict incomplete groups that have exceeded the timeout.
     */
    synchronized void evictStale() {
        long now = System.currentTimeMillis();
        groups.entrySet().removeIf(e -> now - e.getValue().lastUpdatedMs > GROUP_TIMEOUT_MS);
    }

    private void evictOldest() {
        long oldest = Long.MAX_VALUE;
        Integer oldestKey = null;
        for (Map.Entry<Integer, FragmentGroup> e : groups.entrySet()) {
            if (e.getValue().lastUpdatedMs < oldest) {
                oldest = e.getValue().lastUpdatedMs;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            groups.remove(oldestKey);
        }
    }

    synchronized int pendingGroupCount() {
        return groups.size();
    }

    private static final class FragmentGroup {
        private final TreeMap<Integer, byte[]> fragments = new TreeMap<>();
        private boolean lastReceived = false;
        private int lastIndex = -1;
        private long totalBytes = 0;
        private volatile long lastUpdatedMs = System.currentTimeMillis();

        void add(int index, byte[] payload, boolean isLast) {
            if (!fragments.containsKey(index)) {
                fragments.put(index, payload);
                totalBytes += payload.length;
            }
            if (isLast) {
                lastReceived = true;
                lastIndex = index;
            }
        }

        long totalFragmentBytes() {
            return totalBytes;
        }

        boolean isComplete() {
            if (!lastReceived || fragments.isEmpty()) {
                return false;
            }
            if (lastIndex >= MAX_FRAGMENTS_PER_GROUP) {
                return false;
            }
            // All indices 0..lastIndex must be present.
            for (int i = 0; i <= lastIndex; i++) {
                if (!fragments.containsKey(i)) {
                    return false;
                }
            }
            return true;
        }

        byte[] assemble() {
            // Use long to detect overflow before allocating.
            long total = totalBytes;
            if (total > MAX_ASSEMBLED_SIZE || total < 0) {
                return null;
            }
            byte[] out = new byte[(int) total];
            int offset = 0;
            for (int i = 0; i <= lastIndex; i++) {
                byte[] frag = fragments.get(i);
                if (frag == null) {
                    return null;
                }
                System.arraycopy(frag, 0, out, offset, frag.length);
                offset += frag.length;
            }
            return out;
        }
    }
}
