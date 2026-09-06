/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.services;

/** Revocation is synchronous; cleanup and startup may finish in any order. */
final class RelayServiceGeneration {
    private long generation;
    private boolean retired;

    synchronized long current() {
        return generation;
    }

    synchronized boolean isCurrent(long token) {
        return !retired && generation == token;
    }

    synchronized void invalidate() {
        generation++;
    }

    synchronized void retire() {
        retired = true;
        generation++;
    }
}
