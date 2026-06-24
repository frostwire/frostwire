/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectivityDetectorTest {

    @Test
    void defaultsToNotConnectable() {
        ConnectivityDetector.instance().reset();
        assertFalse(ConnectivityDetector.instance().isConnectable());
    }

    @Test
    void markConnectableSetsFlag() {
        ConnectivityDetector.instance().reset();
        ConnectivityDetector.instance().markConnectable();
        assertTrue(ConnectivityDetector.instance().isConnectable());
    }

    @Test
    void markConnectableIsIdempotent() {
        ConnectivityDetector.instance().reset();
        ConnectivityDetector.instance().markConnectable();
        ConnectivityDetector.instance().markConnectable();
        assertTrue(ConnectivityDetector.instance().isConnectable());
    }

    @Test
    void resetClearsFlag() {
        ConnectivityDetector.instance().markConnectable();
        ConnectivityDetector.instance().reset();
        assertFalse(ConnectivityDetector.instance().isConnectable());
    }
}
