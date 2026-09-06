/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class RudpPacketCodecTest {

  @Test
  void codecRoundTrip() {
    EmbeddedChannel channel = new EmbeddedChannel(new RudpPacketCodec());

    byte[] payload = "hello".getBytes();
    RudpPacket packet = new RudpPacket(RudpPacket.Type.DATA, 12345L, 1, 0, payload);
    RudpPacketEnvelope envelope =
        new RudpPacketEnvelope(
            packet,
            new InetSocketAddress("127.0.0.1", 6789),
            new InetSocketAddress("127.0.0.1", 6888));

    channel.writeOutbound(envelope);

    DatagramPacket datagram = channel.readOutbound();
    assertNotNull(datagram);

    channel.writeInbound(datagram);

    RudpPacketEnvelope decoded = channel.readInbound();
    assertNotNull(decoded);
    assertEquals(packet.type(), decoded.packet().type());
    assertEquals(packet.connectionId(), decoded.packet().connectionId());
    assertEquals(packet.sequence(), decoded.packet().sequence());
    assertEquals(packet.ackThrough(), decoded.packet().ackThrough());
    assertArrayEquals(payload, decoded.packet().payload());
    channel.finishAndReleaseAll();
  }

  @Test
  void dropsPacketWithBadMagic() {
    EmbeddedChannel channel = new EmbeddedChannel(new RudpPacketCodec());
    ByteBuf buf = Unpooled.buffer();
    buf.writeShort(0x1234);
    channel.writeInbound(new DatagramPacket(buf, new InetSocketAddress("127.0.0.1", 6000)));
    assertNull(channel.readInbound());
    channel.finishAndReleaseAll();
  }

  @Test
  void rejectsLegacyOversizedAndTrailingWireBytes() {
    EmbeddedChannel channel = new EmbeddedChannel(new RudpPacketCodec());
    try {
      for (int version : new int[] {1, RudpPacket.VERSION}) {
        for (int length : new int[] {0, RudpPacket.MAX_WIRE_PAYLOAD + 1}) {
          ByteBuf buf = Unpooled.buffer();
          buf.writeShort(RudpPacket.MAGIC)
              .writeByte(version)
              .writeByte(RudpPacket.Type.DATA.code())
              .writeLong(1)
              .writeInt(1)
              .writeInt(0)
              .writeShort(length);
          // Zero declared bytes with one trailing byte is also rejected.
          buf.writeZero(Math.max(1, length));
          assertFalse(
              channel.writeInbound(
                  new DatagramPacket(buf, new InetSocketAddress("127.0.0.1", 6000))));
          assertNull(channel.readInbound());
        }
      }
      assertThrows(
          io.netty.handler.codec.EncoderException.class,
          () ->
              channel.writeOutbound(
                  new RudpPacketEnvelope(
                      new RudpPacket(
                          RudpPacket.Type.DATA, 1, 1, 0, new byte[RudpPacket.MAX_WIRE_PAYLOAD + 1]),
                      null,
                      new InetSocketAddress("127.0.0.1", 6000))));
      assertNull(channel.readOutbound());
    } finally {
      channel.finishAndReleaseAll();
    }
  }
}
