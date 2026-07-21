/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The decoded envelope must carry the datagram's real sender in the sender slot — a swap here
 * (present since the first rUDP commit) addressed every inbound-learned session to the server's own
 * wildcard address, so all replies looped back to self (2026-07-20: Android HELLO learned as
 * [::]:6889, HELLO_ACK and search responses never left the forwarder).
 */
class RudpPacketCodecAddressTest {

  @Test
  void decodedEnvelopePreservesDatagramSender() {
    ByteBuf buf = Unpooled.buffer();
    buf.writeShort(RudpPacket.MAGIC & 0xffff);
    buf.writeByte(RudpPacket.VERSION);
    buf.writeByte(RudpPacket.Type.HELLO.code());
    buf.writeLong(0x1122334455667788L);
    buf.writeInt(0);
    buf.writeInt(0);
    buf.writeShort(0); // no payload

    InetSocketAddress realSender = new InetSocketAddress("76.130.145.63", 6889);
    InetSocketAddress localRecipient = new InetSocketAddress("54.172.26.106", 6889);
    DatagramPacket datagram = new DatagramPacket(buf, localRecipient, realSender);

    RudpPacketCodec codec = new RudpPacketCodec();
    List<Object> out = new ArrayList<>();
    codec.decode(null, datagram, out);

    assertEquals(1, out.size());
    RudpPacketEnvelope envelope = (RudpPacketEnvelope) out.get(0);
    assertEquals(
        realSender, envelope.sender(), "envelope.sender() must be the datagram's actual sender");
    assertEquals(localRecipient, envelope.recipient());
  }
}
