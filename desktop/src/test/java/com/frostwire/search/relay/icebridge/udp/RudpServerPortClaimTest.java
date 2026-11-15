/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.PowTestIdentities;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The rUDP socket is dual-stack. On macOS its bind is not checked against IPv4-only sockets, so it
 * could share a port with another program's {@code 0.0.0.0:port} socket, which then received every
 * IPv4 datagram meant for the node. {@link RudpServer} must never end up on such a port.
 */
class RudpServerPortClaimTest {

  /** Ephemeral binds land on a port no IPv4 socket on the machine holds. */
  @Test
  @SuppressWarnings("deprecation") // the same event loop RudpServer uses
  void ephemeralPortIsNeverOneAnIpv4SocketAlreadyHolds() throws Exception {
    List<DatagramChannel> others = new ArrayList<>();
    Set<Integer> taken = new HashSet<>();
    EventLoopGroup group = new NioEventLoopGroup(1);
    try {
      // About an eighth of the ephemeral range, so 60 unchecked binds all miss it by chance
      // well under once in a thousand runs.
      for (int i = 0; i < 2_000; i++) {
        DatagramChannel other = DatagramChannel.open(StandardProtocolFamily.INET);
        others.add(other);
        other.bind(new InetSocketAddress("0.0.0.0", 0));
        taken.add(((InetSocketAddress) other.getLocalAddress()).getPort());
      }
      Bootstrap bootstrap =
          new Bootstrap()
              .group(group)
              .channel(NioDatagramChannel.class)
              .handler(
                  new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel channel) {}
                  });
      for (int i = 0; i < 60; i++) {
        Channel channel = RudpServer.bind(bootstrap, "0.0.0.0", 0);
        try {
          int port = ((InetSocketAddress) channel.localAddress()).getPort();
          assertFalse(
              taken.contains(port), "rUDP bound port " + port + " that an IPv4 socket holds");
        } finally {
          channel.close().sync();
        }
      }
    } finally {
      for (DatagramChannel other : others) {
        other.close();
      }
      group.shutdownGracefully(0, 0, java.util.concurrent.TimeUnit.MILLISECONDS).sync();
    }
  }

  /** A configured port another program holds over IPv4 is refused like any port in use. */
  @Test
  void configuredPortAnIpv4SocketHoldsIsRefused() throws Exception {
    try (DatagramChannel other = DatagramChannel.open(StandardProtocolFamily.INET)) {
      other.bind(new InetSocketAddress("0.0.0.0", 0));
      int port = ((InetSocketAddress) other.getLocalAddress()).getPort();
      IceBridgeConfig config = IceBridgeConfig.newBuilder().rudpPort(port).build();
      RudpSessionManager manager =
          new RudpSessionManager(
              PowTestIdentities.next(),
              new PeerRegistry(config),
              new IceBridgeMetrics(),
              (pub, payload) -> {});
      RudpServer server = new RudpServer(config, manager);
      try {
        Exception refused = assertThrows(Exception.class, server::start);
        assertTrue(
            refused instanceof IllegalStateException || refused instanceof java.io.IOException,
            "unexpected failure " + refused);
      } finally {
        server.close();
      }
    }
  }
}
