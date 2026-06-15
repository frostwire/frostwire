/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.util.Logger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.TimeUnit;

/**
 * rUDP listener for the IceBridge servent.
 *
 * <p>Binds a UDP socket and dispatches decoded packets to a
 * {@link RudpSessionManager}. The manager handles reliability, hole punching,
 * and relay forwarding.
 */
@SuppressWarnings("deprecation")
public final class RudpServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RudpServer.class);

    private final IceBridgeConfig config;
    private final RudpSessionManager manager;
    private EventLoopGroup group;
    private Channel channel;

    public RudpServer(IceBridgeConfig config, RudpSessionManager manager) {
        this.config = config;
        this.manager = manager;
    }

    public void start() throws InterruptedException {
        int port = config.rudpPort();
        if (port < 0) {
            return;
        }
        group = new NioEventLoopGroup(0, new DefaultThreadFactory("icebridge-rudp"));
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) {
                        ch.pipeline()
                                .addLast(new RudpPacketCodec())
                                .addLast(new PacketHandler());
                    }
                });
        channel = bootstrap.bind(config.host(), port).sync().channel();
        manager.setChannel(channel);
        LOG.info("IceBridge rUDP server listening on " + config.host() + ":" + actualPort());
    }

    public int port() {
        return actualPort();
    }

    private int actualPort() {
        if (channel == null) {
            return config.rudpPort();
        }
        return ((java.net.InetSocketAddress) channel.localAddress()).getPort();
    }

    @Override
    public void close() {
        manager.shutdown();
        if (channel != null) {
            channel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
        }
        if (group != null) {
            group.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS);
        }
        LOG.info("IceBridge rUDP server stopped");
    }

    private final class PacketHandler extends SimpleChannelInboundHandler<RudpPacketEnvelope> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, RudpPacketEnvelope envelope) {
            manager.onPacket(envelope);
        }
    }
}