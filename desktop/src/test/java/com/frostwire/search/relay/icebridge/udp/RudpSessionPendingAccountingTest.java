/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The relay's admission check read each session's pending bytes and packets by walking its queue,
 * for every session, for every outbound packet: about 15 % of the relay's CPU with a few stuck
 * peers (profile of the live relay and {@code RudpRelayLoadTest}). The session now keeps both
 * counts as it changes the queue, so they must always equal a recount of the queue, and the
 * capacity limits built on them must still hold and free up as packets are acknowledged.
 */
class RudpSessionPendingAccountingTest {

  private static final InetSocketAddress PEER = new InetSocketAddress("198.18.0.1", 62000);

  @Test
  void theCountsEqualARecountAfterAnyMixOfChanges() {
    RudpSession session = session();
    Random random = new Random(1397);
    int highest = 0;
    for (int step = 0; step < 4000; step++) {
      switch (random.nextInt(6)) {
        case 0, 1, 2 -> {
          int sequence = ++highest;
          if (session.addPending(sequence, pending(sequence, random.nextInt(900)))) {
            session.markSent(sequence);
          }
        }
        case 3 -> {
          // Replaces an entry that is already queued: the counts must not grow twice.
          int sequence = 1 + random.nextInt(Math.max(1, highest));
          session.addPending(sequence, pending(sequence, random.nextInt(900)));
        }
        case 4 -> session.ackLocal(1 + random.nextInt(Math.max(1, highest)));
        default -> session.removePending(random.nextInt(Math.max(1, highest)));
      }
      assertCountsMatchTheQueue(session, "step " + step);
    }
    session.clear();
    assertEquals(0, session.pendingCount());
    assertEquals(0, session.retainedBytes());
    assertTrue(session.pending().isEmpty());
  }

  @Test
  void aSessionAcceptsAtMostItsPacketLimitAndAcknowledgementsFreeRoom() {
    RudpSession session = session();
    for (int sequence = 1; sequence <= RudpSession.MAX_PENDING_PACKETS; sequence++) {
      assertTrue(session.addPending(sequence, pending(sequence, 0)), "packet " + sequence);
      session.markSent(sequence);
    }
    assertEquals(RudpSession.MAX_PENDING_PACKETS, session.pendingCount());
    int extra = RudpSession.MAX_PENDING_PACKETS + 1;
    assertFalse(session.addPending(extra, pending(extra, 0)), "over the packet limit");

    assertTrue(session.ackLocal(100));
    assertEquals(RudpSession.MAX_PENDING_PACKETS - 100, session.pendingCount());
    assertTrue(session.addPending(extra, pending(extra, 0)), "room after the acknowledgement");
    assertCountsMatchTheQueue(session, "after the acknowledgement");
  }

  @Test
  void aSessionAcceptsAtMostItsByteLimit() {
    RudpSession session = session();
    int payload = 60_000;
    int accepted = 0;
    for (int sequence = 1; sequence <= 100; sequence++) {
      if (session.addPending(sequence, pending(sequence, payload))) {
        accepted++;
      }
    }
    assertTrue(accepted > 0 && accepted < 100, "accepted " + accepted);
    assertTrue(session.retainedBytes() <= RudpSession.MAX_RETAINED_BYTES);
    assertCountsMatchTheQueue(session, "byte limit");
  }

  @Test
  void theManagerCannotChangeTheQueueBehindTheCounters() {
    RudpSession session = session();
    session.addPending(1, pending(1, 10));
    assertThrows(UnsupportedOperationException.class, () -> session.pending().remove(1));
    assertThrows(UnsupportedOperationException.class, () -> session.pending().clear());
    assertCountsMatchTheQueue(session, "after the refused changes");
  }

  private static void assertCountsMatchTheQueue(RudpSession session, String when) {
    long bytes = 0;
    int packets = 0;
    for (PendingPacket packet : session.pending().values()) {
      bytes += 2L * (packet.packet.size() + RudpAuth.SIGNATURE_LENGTH);
      packets++;
    }
    assertEquals(packets, session.pendingCount(), "packets, " + when);
    assertEquals(bytes, session.retainedBytes(), "bytes, " + when);
  }

  private static RudpSession session() {
    return new RudpSession(1, 2, PEER, new byte[32], false);
  }

  private static PendingPacket pending(int sequence, int payloadBytes) {
    return new PendingPacket(
        new RudpPacket(RudpPacket.Type.DATA, 2, sequence, 0, new byte[payloadBytes]), PEER, 0);
  }
}
