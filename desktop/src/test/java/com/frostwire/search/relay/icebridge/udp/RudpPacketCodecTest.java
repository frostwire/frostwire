/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class RudpPacketCodecTest {

    @Test
    void codecRoundTrip() {
        EmbeddedChannel channel = new EmbeddedChannel(new RudpPacketCodec());

        byte[] payload = "hello".getBytes();
        RudpPacket packet = new RudpPacket(RudpPacket.Type.DATA, 12345L,
                1, 0, payload);
        RudpPacketEnvelope envelope = new RudpPacketEnvelope(packet,
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
    }

    @Test
    void dropsPacketWithBadMagic() {
        EmbeddedChannel channel = new EmbeddedChannel(new RudpPacketCodec());
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(0x1234);
        channel.writeInbound(new DatagramPacket(buf, new InetSocketAddress("127.0.0.1", 6000)));
        assertNull(channel.readInbound());
    }
}