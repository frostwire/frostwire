/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.MeshEnvelope;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import org.junit.jupiter.api.Test;

class InboundMessageQueueBackpressureTest {

  @Test
  void fullTargetQueueRejectsNewMessageWithoutDroppingAcceptedMessages() {
    InboundMessageQueue queue = new InboundMessageQueue(2);
    byte[] target = new byte[32];
    byte[] first = MeshEnvelope.encodeForWire(MeshProtocolId.CHAT, new byte[] {1});
    byte[] second = MeshEnvelope.encodeForWire(MeshProtocolId.CHAT, new byte[] {2});
    byte[] rejected = MeshEnvelope.encodeForWire(MeshProtocolId.CHAT, new byte[] {3});

    assertTrue(queue.offerForTarget(target, null, first));
    assertTrue(queue.offerForTarget(target, null, second));
    assertFalse(queue.offerForTarget(target, null, rejected));

    java.util.List<InboundMessage> accepted = queue.pollForTarget(target, 3);
    assertEquals(2, accepted.size());
    assertEquals(1, accepted.get(0).payload()[0]);
    assertEquals(2, accepted.get(1).payload()[0]);
  }
}
