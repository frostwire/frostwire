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

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DistributedSearchTransportTest {
  @Test
  void cancelledOrExpiredOperationDoesNotSend() {
    RecordingTransport transport = new RecordingTransport();
    DistributedSearchTransport.SendOperation cancelled =
        transport.createSend(
            new byte[32], 1, new byte[] {1}, System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
    cancelled.cancel();
    cancelled.cancel();
    assertFalse(cancelled.execute());
    assertFalse(
        transport.createSend(new byte[32], 1, new byte[] {1}, System.nanoTime() - 1).execute());
    assertEquals(0, transport.sends);
  }

  @Test
  void operationIsSingleUseAndOwnsPayloadSnapshot() {
    RecordingTransport transport = new RecordingTransport();
    byte[] payload = {1};
    DistributedSearchTransport.SendOperation operation =
        transport.createSend(
            new byte[32], 1, payload, System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
    payload[0] = 2;
    assertTrue(operation.execute());
    assertFalse(operation.execute());
    assertEquals(1, transport.sends);
    assertEquals(1, transport.received[0]);
  }

  private static class RecordingTransport implements DistributedSearchTransport {
    int sends;
    byte[] received;

    public boolean send(byte[] target, int protocolId, byte[] payload) {
      sends++;
      received = payload;
      return true;
    }

    public void addListener(PayloadListener listener) {}

    public void removeListener(PayloadListener listener) {}
  }
}
