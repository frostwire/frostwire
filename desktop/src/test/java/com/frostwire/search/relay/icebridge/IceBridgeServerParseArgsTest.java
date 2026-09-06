/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IceBridgeServerParseArgsTest {

  @AfterEach
  void resetTopology() {
    IceBridgeTopology.get().resetToDefaults();
  }

  @Test
  void parseArgsRejectsZeroControlHttpPort() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                IceBridgeServer.parseArgs(
                    new String[] {
                      "--control-http-port", "0",
                      "--role", "BOTH"
                    }));
    assertTrue(
        ex.getMessage().contains("control-http-port"),
        "expected clear error for --control-http-port 0, got: " + ex.getMessage());
  }

  @Test
  void parseArgsAcceptsPositiveControlHttpPort() {
    IceBridgeConfig config =
        IceBridgeServer.parseArgs(
            new String[] {
              "--control-http-port", "8797",
              "--role", "BOTH"
            });
    assertTrue(config.controlHttpPort() > 0);
  }

  @Test
  void explicitOptionsOverlayEnvironmentDefaults() {
    IceBridgeConfig config =
        IceBridgeServer.parseArgs(
            new String[] {
              "--role",
              "CLIENT",
              "--relay-port",
              "0",
              "--control-http-port",
              "9001",
              "--max-sessions",
              "73",
              "--no-dht",
              "--no-bootstrap",
              "--auth-tokens-file",
              "first.txt",
              "--auth-tokens-file",
              "last.txt"
            });
    assertEquals(IceBridgeConfig.Role.CLIENT, config.role());
    assertEquals(0, config.relayPort());
    assertEquals(9001, config.controlHttpPort());
    assertEquals(73, config.maxSessions());
    assertEquals(new File("last.txt"), config.authTokensFile());
    assertFalse(config.dhtEnabled());
    assertFalse(config.bootstrap());
  }

  @Test
  void rejectsSecretArgumentsAndMissingValues() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> IceBridgeServer.parseArgs(new String[] {"--auth-token", "fixture-secret"}));
    assertFalse(error.getMessage().contains("fixture-secret"));
    assertThrows(
        IllegalArgumentException.class,
        () -> IceBridgeServer.parseArgs(new String[] {"--auth-tokens-file"}));
    assertThrows(
        IllegalArgumentException.class,
        () -> IceBridgeServer.parseArgs(new String[] {"--max-sessions", "0"}));
  }
}
