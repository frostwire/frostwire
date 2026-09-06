/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Bounded retained assemblies. A completed group remains owned until release. */
final class FragmentReassembler {
    private static final int MAX_PENDING_GROUPS = 64;
    private static final long GROUP_TIMEOUT_MS = 30_000;
    static final int MAX_FRAGMENTS_PER_GROUP = 256;
    static final long MAX_ASSEMBLED_SIZE = 256L * 1024;
    // Each slot reserves up to 256 KiB of fragments plus a completed copy.
    // Thus retained payload bytes are bounded by 32 MiB, including blocked delivery.
    private final Map<String, Group> groups = new HashMap<>();

    enum State { REJECTED, RETAINED, COMPLETE }

    static final class Result {
        final State state;
        final byte[] payload;

        Result(State state, byte[] payload) {
            this.state = state;
            this.payload = payload;
        }
    }

    synchronized Result accept(String key, int index, int total, boolean last, byte[] payload) {
        if (key == null || total <= 0 || total > MAX_FRAGMENTS_PER_GROUP || index < 0 || index >= total
                || last != (index == total - 1) || payload == null || payload.length == 0
                || payload.length > RudpPacket.MAX_FRAGMENT_PAYLOAD) {
            return new Result(State.REJECTED, null);
        }
        Group group = groups.get(key);
        if (group == null) {
            if (groups.size() >= MAX_PENDING_GROUPS) {
                return new Result(State.REJECTED, null);
            }
            group = new Group(total);
            groups.put(key, group);
        }
        if (group.fragments.length != total) {
            return new Result(State.REJECTED, null);
        }
        byte[] existing = group.fragments[index];
        if (existing != null && !Arrays.equals(existing, payload)) {
            return new Result(State.REJECTED, null);
        }
        if (existing == null) {
            if (group.bytes > MAX_ASSEMBLED_SIZE - payload.length) {
                return new Result(State.REJECTED, null);
            }
            group.fragments[index] = payload.clone();
            group.bytes += payload.length;
            group.count++;
        }
        if (group.count == total) {
            if (group.completed == null) {
                group.completed = new byte[group.bytes];
                int offset = 0;
                for (byte[] fragment : group.fragments) {
                    System.arraycopy(fragment, 0, group.completed, offset, fragment.length);
                    offset += fragment.length;
                }
            }
            return new Result(State.COMPLETE, group.completed);
        }
        return new Result(State.RETAINED, null);
    }

    synchronized void release(String key) {
        groups.remove(key);
    }

    synchronized void removeSession(String prefix) {
        groups.keySet().removeIf(key -> key.startsWith(prefix));
    }

    synchronized boolean hasExpired(String prefix) {
        long now = System.nanoTime();
        return groups.entrySet().stream().anyMatch(e -> e.getKey().startsWith(prefix)
                && (now - e.getValue().createdNanos) / 1_000_000 > GROUP_TIMEOUT_MS);
    }

    synchronized int pendingGroupCount() {
        return groups.size();
    }

    private static final class Group {
        final byte[][] fragments;
        final long createdNanos = System.nanoTime();
        int count;
        int bytes;
        byte[] completed;

        Group(int total) {
            fragments = new byte[total][];
        }
    }
}
