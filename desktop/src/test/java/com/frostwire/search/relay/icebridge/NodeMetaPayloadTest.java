/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.NodeCapabilities;
import org.junit.jupiter.api.Test;

class NodeMetaPayloadTest {

  @Test
  void roundTripsCapabilities() {
    assertEquals(
        NodeCapabilities.DEFAULT_BOTH,
        NodeMetaPayload.decode(NodeMetaPayload.encode(NodeCapabilities.DEFAULT_BOTH)));
    assertEquals(
        NodeCapabilities.NONE,
        NodeMetaPayload.decode(NodeMetaPayload.encode(NodeCapabilities.NONE)));
    // High bit (PUBLIC_CATALOG) must survive the big-endian encoding.
    assertEquals(
        NodeCapabilities.PUBLIC_CATALOG,
        NodeMetaPayload.decode(NodeMetaPayload.encode(NodeCapabilities.PUBLIC_CATALOG)));
  }

  @Test
  void rejectsMalformedFrames() {
    assertEquals(-1L, NodeMetaPayload.decode(null));
    assertEquals(-1L, NodeMetaPayload.decode(new byte[0]));
    assertEquals(-1L, NodeMetaPayload.decode(new byte[NodeMetaPayload.LENGTH]));
    assertEquals(-1L, NodeMetaPayload.decode(new byte[NodeMetaPayload.LENGTH + 1]));
    byte[] wrongVersion = NodeMetaPayload.encode(NodeCapabilities.DEFAULT_PEER);
    wrongVersion[0] = 99;
    assertEquals(-1L, NodeMetaPayload.decode(wrongVersion));
  }

  @Test
  void protocolIdIsKnownAndNamed() {
    assertTrue(MeshProtocolId.isKnown(MeshProtocolId.NODE_META));
    assertEquals("NODE_META", MeshProtocolId.name(MeshProtocolId.NODE_META));
    assertTrue(MeshProtocolId.isKnown(MeshProtocolId.CATALOG));
    assertEquals("CATALOG", MeshProtocolId.name(MeshProtocolId.CATALOG));
  }
}
