/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.frostwire.util.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

/**
 * Netty codec for {@link RudpPacket} over UDP datagrams.
 */
final class RudpPacketCodec extends MessageToMessageCodec<DatagramPacket, RudpPacketEnvelope> {

    private static final Logger LOG = Logger.getLogger(RudpPacketCodec.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, RudpPacketEnvelope envelope, List<Object> out) {
        RudpPacket packet = envelope.packet();
        byte[] payload = packet.payload();
        if (payload.length > 65535) {
            LOG.warn("RudpPacketCodec: dropping packet with payload > 65535 bytes (" + payload.length + ")");
            return;
        }
        ByteBuf buf = ctx.alloc().buffer(RudpPacket.HEADER_SIZE + payload.length);
        buf.writeShort(RudpPacket.MAGIC);
        buf.writeByte(RudpPacket.VERSION);
        buf.writeByte(packet.type().code());
        buf.writeLong(packet.connectionId());
        buf.writeInt(packet.sequence());
        buf.writeInt(packet.ackThrough());
        buf.writeShort(payload.length);
        if (payload.length > 0) {
            buf.writeBytes(payload);
        }
        out.add(new DatagramPacket(buf, envelope.recipient(), envelope.sender()));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket datagram, List<Object> out) {
        ByteBuf buf = datagram.content();
        if (buf.readableBytes() < RudpPacket.HEADER_SIZE) {
            return;
        }
        int readerIndex = buf.readerIndex();
        int magic = buf.readUnsignedShort();
        if (magic != (RudpPacket.MAGIC & 0xffff)) {
            buf.readerIndex(readerIndex);
            LOG.debug("RudpPacketCodec: dropping packet with bad magic " + magic);
            return;
        }
        int version = buf.readByte();
        if (version != RudpPacket.VERSION) {
            buf.readerIndex(readerIndex);
            LOG.debug("RudpPacketCodec: dropping packet with bad version " + version);
            return;
        }
        int typeCode = buf.readByte();
        RudpPacket.Type type = RudpPacket.Type.fromCode(typeCode);
        if (type == null) {
            buf.readerIndex(readerIndex);
            LOG.debug("RudpPacketCodec: dropping packet with unknown type " + typeCode);
            return;
        }
        long connectionId = buf.readLong();
        int sequence = buf.readInt();
        int ackThrough = buf.readInt();
        int payloadLen = buf.readUnsignedShort();
        if (buf.readableBytes() < payloadLen) {
            buf.readerIndex(readerIndex);
            LOG.debug("RudpPacketCodec: incomplete payload");
            return;
        }
        byte[] payload = new byte[payloadLen];
        if (payloadLen > 0) {
            buf.readBytes(payload);
        }
        RudpPacket packet = new RudpPacket(type, connectionId, sequence, ackThrough, payload);
        out.add(new RudpPacketEnvelope(packet, datagram.recipient(), datagram.sender()));
    }
}