/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NodeCapabilitiesTest {

  @Test
  void fromRoleMapsRoles() {
    assertTrue(
        NodeCapabilities.has(NodeCapabilities.fromRole("FORWARDER"), NodeCapabilities.RELAY));
    assertFalse(
        NodeCapabilities.has(NodeCapabilities.fromRole("FORWARDER"), NodeCapabilities.SEARCH));
    assertTrue(NodeCapabilities.has(NodeCapabilities.fromRole("CLIENT"), NodeCapabilities.SEARCH));
    assertTrue(
        NodeCapabilities.has(
            NodeCapabilities.fromRole("BOTH"), NodeCapabilities.RELAY | NodeCapabilities.SEARCH));
  }

  @Test
  void toRoleRoundTripsCommonCases() {
    assertEquals("FORWARDER", NodeCapabilities.toRole(NodeCapabilities.DEFAULT_FORWARDER));
    assertEquals("CLIENT", NodeCapabilities.toRole(NodeCapabilities.DEFAULT_PEER));
    assertEquals("BOTH", NodeCapabilities.toRole(NodeCapabilities.DEFAULT_BOTH));
  }

  @Test
  void publicCatalogIsIsolatedOptInBit() {
    assertEquals(1L << 7, NodeCapabilities.PUBLIC_CATALOG);

    // Explicit opt-in: absent from every default role mask.
    assertFalse(
        NodeCapabilities.has(NodeCapabilities.DEFAULT_PEER, NodeCapabilities.PUBLIC_CATALOG));
    assertFalse(
        NodeCapabilities.has(NodeCapabilities.DEFAULT_FORWARDER, NodeCapabilities.PUBLIC_CATALOG));
    assertFalse(
        NodeCapabilities.has(NodeCapabilities.DEFAULT_BOTH, NodeCapabilities.PUBLIC_CATALOG));

    long with =
        NodeCapabilities.with(NodeCapabilities.DEFAULT_PEER, NodeCapabilities.PUBLIC_CATALOG);
    assertTrue(NodeCapabilities.has(with, NodeCapabilities.PUBLIC_CATALOG));
    assertTrue(NodeCapabilities.has(with, NodeCapabilities.SEARCH));

    long without = NodeCapabilities.without(with, NodeCapabilities.PUBLIC_CATALOG);
    assertFalse(NodeCapabilities.has(without, NodeCapabilities.PUBLIC_CATALOG));
    assertEquals(NodeCapabilities.DEFAULT_PEER, without);

    // Adding it must not shift the derived role.
    assertEquals("CLIENT", NodeCapabilities.toRole(with));
  }

  // IncomingSearchRequestHandler catalog-browse gating (setPublicCatalogEnabled)
  // is not unit-tested here: exercising it needs a signed RemoteCatalogBrowseRequest,
  // a transport payload fixture, an IdentityKeys target and a LocalIndex. That is an
  // integration test (IncomingSearchRequestHandlerTest / relay wire tests); the
  // coordinator can add it there. This test covers only the capability bit.
}
