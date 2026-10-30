/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * Feeds {@link RudpPacketCodec} the UDP datagrams anyone on the internet can send to an IceBridge
 * servent's rUDP port, through a Netty {@link EmbeddedChannel} as {@code RudpServer} installs it.
 * Every datagram is untrusted: the codec runs before any authentication.
 *
 * <p>The codec documents no exception: a datagram that isn't exactly one well-formed packet (bad
 * magic, version or type, short header, payload length not matching the datagram or over {@link
 * RudpPacket#MAX_WIRE_PAYLOAD}) is dropped. A finding is anything escaping the pipeline, or a
 * decoded packet that doesn't encode back to the exact bytes it was decoded from.
 */
class RudpPacketCodecFuzzTest {

  private static final InetSocketAddress SENDER = new InetSocketAddress("10.0.0.1", 4000);
  private static final InetSocketAddress LOCAL = new InetSocketAddress("10.0.0.2", 4001);

  @FuzzTest
  void decode(FuzzedDataProvider data) {
    byte[] datagram = data.consumeRemainingAsBytes();
    EmbeddedChannel channel = new EmbeddedChannel(new RudpPacketCodec());
    try {
      channel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(datagram), LOCAL, SENDER));
      RudpPacketEnvelope envelope = channel.readInbound();
      if (envelope == null) {
        return;
      }
      if (channel.readInbound() != null) {
        throw new IllegalStateException("one datagram decoded to several packets");
      }
      if (!SENDER.equals(envelope.sender()) || !LOCAL.equals(envelope.recipient())) {
        throw new IllegalStateException("decoded envelope swapped or lost the datagram addresses");
      }
      channel.writeOutbound(envelope);
      DatagramPacket encoded = channel.readOutbound();
      try {
        ByteBuf content = encoded.content();
        byte[] reencoded = ByteBufUtil.getBytes(content);
        if (!Arrays.equals(datagram, reencoded)) {
          throw new IllegalStateException("decoded packet does not encode back to its datagram");
        }
      } finally {
        encoded.release();
      }
    } finally {
      channel.finishAndReleaseAll();
    }
  }
}
