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

    assertTrue(queue.registerConsumer(target));
    assertTrue(queue.offerForTarget(target, null, first));
    assertTrue(queue.offerForTarget(target, null, second));
    assertFalse(queue.offerForTarget(target, null, rejected));

    java.util.List<InboundMessage> accepted = queue.pollForTarget(target, 3);
    assertEquals(2, accepted.size());
    assertEquals(1, accepted.get(0).payload()[0]);
    assertEquals(2, accepted.get(1).payload()[0]);
  }

  @Test
  void identityOnlyPollingDoesNotRetainUnusedSharedMirror() {
    InboundMessageQueue queue = new InboundMessageQueue(2);
    byte[] target = new byte[32];
    assertTrue(queue.setSharedConsumerEnabled(false));
    assertTrue(queue.registerConsumer(target));
    for (int i = 0; i < 1024; i++) {
      assertTrue(queue.offerFromRudp(target, target, new byte[] {1}));
      assertEquals(1, queue.pollForTarget(target, 1).size());
      assertEquals(0, queue.size());
    }
    assertTrue(queue.poll(256).isEmpty());
    assertTrue(queue.unregisterConsumer(target));
    assertFalse(queue.offerForTarget(target, null, new byte[] {1}));
    assertFalse(queue.offerFromRudp(target, null, new byte[] {1}));
  }

  @Test
  void consumerChangesCannotOrphanAcceptedWork() {
    InboundMessageQueue queue = new InboundMessageQueue(2);
    byte[] target = new byte[32];
    assertFalse(queue.offerForTarget(target, null, new byte[] {1}));
    queue.onMessage(null, new byte[] {1});
    assertFalse(queue.setSharedConsumerEnabled(false));
    assertTrue(queue.pollForTarget(null, 1).isEmpty());
    assertEquals(1, queue.poll(1).size());
    assertTrue(queue.setSharedConsumerEnabled(false));
    assertTrue(queue.registerConsumer(target));
    assertTrue(queue.registerConsumer(target));
    assertTrue(queue.offerForTarget(target, null, new byte[] {2}));
    assertFalse(queue.unregisterConsumer(target));
    assertEquals(1, queue.pollForTarget(target, 1).size());
    assertTrue(queue.unregisterConsumer(target));
  }

  @Test
  void consumerCardinalityAndGlobalMessagesAreBounded() {
    InboundMessageQueue queue = new InboundMessageQueue(100);
    for (int i = 0; i < 256; i++) {
      byte[] target = java.nio.ByteBuffer.allocate(32).putInt(i).array();
      assertTrue(queue.registerConsumer(target));
      for (int j = 0; j < 16; j++) {
        assertTrue(queue.offerForTarget(target, null, new byte[] {1}));
      }
    }
    assertEquals(4096, queue.size());
    assertFalse(queue.registerConsumer(java.nio.ByteBuffer.allocate(32).putInt(256).array()));
    assertFalse(queue.offerForTarget(new byte[32], null, new byte[] {1}));
    assertEquals(16, queue.pollForTarget(new byte[32], 256).size());
    assertTrue(queue.offerForTarget(new byte[32], null, new byte[] {1}));
  }

  @Test
  void aggregateBytesAndPollResponseBytesAreBounded() {
    InboundMessageQueue queue = new InboundMessageQueue(100);
    byte[] target = new byte[32];
    assertTrue(queue.registerConsumer(target));
    byte[] payload = new byte[256 * 1024];
    int accepted = 0;
    while (accepted < 100 && queue.offerForTarget(target, target, payload)) {
      accepted++;
    }
    assertEquals(63, accepted);
    assertEquals(1, queue.pollForTarget(target, 256).size());
    assertTrue(queue.offerForTarget(target, target, payload));
    assertFalse(queue.offerForTarget(target, target, new byte[payload.length + 1]));
  }
}
