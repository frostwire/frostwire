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

import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Standalone FORWARDER hubs (e.g. EC2) default to the fat hub topology for unset env keys; leaves
 * keep the lean compiled defaults. Explicit env always wins. Uses system properties because config
 * env lookup falls back to them when real environment variables are absent.
 */
class IceBridgeConfigEnvTest {
  private static final String[] MANAGED_KEYS = {
    "ICEBRIDGE_ROLE",
    "ICEBRIDGE_MESH_FANOUT",
    "ICEBRIDGE_SEARCH_PEER_FANOUT",
    "ICEBRIDGE_MESH_HOP_TTL",
    "ICEBRIDGE_SEARCH_TTL",
    "ICEBRIDGE_MAX_SESSIONS"
  };

  @AfterEach
  void reset() {
    for (String key : MANAGED_KEYS) {
      System.clearProperty(key);
    }
    IceBridgeTopology.get().resetToDefaults();
  }

  @Test
  void forwarderRoleDefaultsToHubTopology() {
    IceBridgeTopology.get().resetToDefaults();
    System.setProperty("ICEBRIDGE_ROLE", "FORWARDER");

    IceBridgeConfig.fromEnv();

    IceBridgeTopology topo = IceBridgeTopology.get();
    assertEquals(IceBridgeTopology.HYBRID_EC2_MESH_FANOUT, topo.meshBroadcastFanout());
    assertEquals(IceBridgeTopology.HYBRID_EC2_SEARCH_PEER_FANOUT, topo.searchPeerFanout());
    assertEquals(IceBridgeTopology.HYBRID_EC2_MESH_HOP_TTL, topo.meshHopTtl());
    assertEquals(IceBridgeTopology.HYBRID_EC2_SEARCH_TTL, topo.searchTtl());
  }

  @Test
  void explicitEnvBeatsHubTopology() {
    IceBridgeTopology.get().resetToDefaults();
    System.setProperty("ICEBRIDGE_ROLE", "FORWARDER");
    System.setProperty("ICEBRIDGE_MESH_FANOUT", "6");

    IceBridgeConfig.fromEnv();

    IceBridgeTopology topo = IceBridgeTopology.get();
    assertEquals(6, topo.meshBroadcastFanout());
    assertEquals(IceBridgeTopology.HYBRID_EC2_SEARCH_PEER_FANOUT, topo.searchPeerFanout());
  }

  @Test
  void leafRoleKeepsLeanTopology() {
    IceBridgeTopology.get().resetToDefaults();
    System.setProperty("ICEBRIDGE_ROLE", "BOTH");

    IceBridgeConfig config = IceBridgeConfig.fromEnv();

    IceBridgeTopology topo = IceBridgeTopology.get();
    assertEquals(IceBridgeTopology.DEFAULT_MESH_BROADCAST_FANOUT, topo.meshBroadcastFanout());
    assertEquals(IceBridgeTopology.DEFAULT_SEARCH_PEER_FANOUT, topo.searchPeerFanout());
    assertEquals(IceBridgeTopology.DEFAULT_SEARCH_TTL, topo.searchTtl());
    assertEquals(IceBridgeConfig.DEFAULT_MAX_SESSIONS, config.maxSessions());
    assertEquals(1024, config.maxSessions());
  }

  @Test
  void forwarderRoleDefaultsToCloudSessions() {
    IceBridgeTopology.get().resetToDefaults();
    System.setProperty("ICEBRIDGE_ROLE", "FORWARDER");

    IceBridgeConfig config = IceBridgeConfig.fromEnv();

    assertEquals(IceBridgeConfig.CLOUD_MAX_SESSIONS, config.maxSessions());
    assertEquals(1024, config.maxSessions());
  }

  @Test
  void explicitMaxSessionsBeatsRoleDefault() {
    IceBridgeTopology.get().resetToDefaults();
    System.setProperty("ICEBRIDGE_ROLE", "FORWARDER");
    System.setProperty("ICEBRIDGE_MAX_SESSIONS", "512");

    IceBridgeConfig config = IceBridgeConfig.fromEnv();

    assertEquals(512, config.maxSessions());
  }

  @Test
  void explicitOverlayWinsWithoutReplacingUnspecifiedConfiguration() {
    System.setProperty("ICEBRIDGE_MAX_SESSIONS", "512");
    IceBridgeConfig config =
        IceBridgeConfig.fromEnv(
            Map.of(
                "ICEBRIDGE_ROLE", "CLIENT",
                "ICEBRIDGE_CONTROL_HTTP_PORT", "9123",
                "ICEBRIDGE_AUTH_TOKENS_FILE", "per-launch-tokens.txt",
                "ICEBRIDGE_BOOTSTRAP", "false",
                "ICEBRIDGE_DHT", "false"));
    assertEquals(IceBridgeConfig.Role.CLIENT, config.role());
    assertEquals(9123, config.controlHttpPort());
    assertEquals(512, config.maxSessions());
    assertEquals(new File("per-launch-tokens.txt"), config.authTokensFile());
    assertFalse(config.bootstrap());
    assertFalse(config.dhtEnabled());
  }

  @Test
  void invalidLimitsFailClosed() {
    for (String value : new String[] {"NaN", "Infinity", "-1", "0"}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> IceBridgeConfig.fromEnv(Map.of("ICEBRIDGE_MAX_QPS_PER_KEY", value)));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> IceBridgeConfig.fromEnv(Map.of("ICEBRIDGE_RUDP_PORT", "65536")));
    assertThrows(
        IllegalArgumentException.class,
        () -> IceBridgeConfig.fromEnv(Map.of("ICEBRIDGE_DHT", "maybe")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            IceBridgeConfig.fromEnv(
                Map.of("ICEBRIDGE_PEER_TTL_SEC", Long.toString(Long.MAX_VALUE))));
  }
}
