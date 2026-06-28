/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IceBridgeServerParseArgsTest {

    @Test
    void parseArgsRejectsZeroControlHttpPort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> IceBridgeServer.parseArgs(new String[]{
                        "--control-http-port", "0",
                        "--role", "BOTH"
                }));
        assertTrue(ex.getMessage().contains("control-http-port"),
                "expected clear error for --control-http-port 0, got: " + ex.getMessage());
    }

    @Test
    void parseArgsAcceptsPositiveControlHttpPort() {
        IceBridgeConfig config = IceBridgeServer.parseArgs(new String[]{
                "--control-http-port", "8797",
                "--role", "BOTH"
        });
        assertTrue(config.controlHttpPort() > 0);
    }
}