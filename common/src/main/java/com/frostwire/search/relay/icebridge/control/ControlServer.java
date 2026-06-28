/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTokens;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import com.frostwire.search.relay.icebridge.udp.RudpSessionManager;
import com.frostwire.util.Logger;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.TimeUnit;

/**
 * Local HTTP control server for an IceBridge servent.
 *
 * <p>Binds to {@link IceBridgeConfig#controlHttpPort()} on localhost only so
 * only co-located FrostWire processes can manage it.
 */
@SuppressWarnings("deprecation")
public final class ControlServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ControlServer.class);
    private static final int MAX_CONTENT_LENGTH = 64 * 1024;

    private final PeerRegistry registry;
    private final IceBridgeMetrics metrics;
    private final IceBridgeConfig config;
    private final RudpSessionManager rudpSessionManager;
    private final InboundMessageQueue inboundQueue;
    private final IceBridgeTokens authTokens;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public ControlServer(PeerRegistry registry,
                         IceBridgeMetrics metrics,
                         IceBridgeConfig config,
                         RudpSessionManager rudpSessionManager,
                         InboundMessageQueue inboundQueue,
                         IceBridgeTokens authTokens) {
        this.registry = registry;
        this.metrics = metrics;
        this.config = config;
        this.rudpSessionManager = rudpSessionManager;
        this.inboundQueue = inboundQueue;
        this.authTokens = (authTokens != null) ? authTokens : new IceBridgeTokens(config.authTokensFile());
    }

    /**
     * For compatibility. With file-based tokens this may return null.
     * Prefer using the tokens object for validation.
     */
    public String authToken() {
        return null;
    }

    public IceBridgeTokens authTokens() {
        return authTokens;
    }

    public void start() throws InterruptedException {
        int port = config.controlHttpPort();
        if (port <= 0) {
            return;
        }
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("icebridge-control-boss"));
        workerGroup = new NioEventLoopGroup(0, new DefaultThreadFactory("icebridge-control-worker"));

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_REUSEADDR, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH))
                                .addLast(new ControlHandler(registry, metrics, config, rudpSessionManager, inboundQueue, authTokens));
                    }
                });

        channel = bootstrap.bind("127.0.0.1", port).sync().channel();
        LOG.info("IceBridge control server listening on http://127.0.0.1:" + port);
    }

    /**
     * Return the actual bound port (useful when {@code port} was 0).
     */
    public int port() {
        if (channel == null) {
            return config.controlHttpPort();
        }
        return ((java.net.InetSocketAddress) channel.localAddress()).getPort();
    }

    @Override
    public void close() {
        if (channel != null) {
            try {
                channel.close().awaitUninterruptibly(5, TimeUnit.SECONDS);
            } catch (Throwable ignored) {
            }
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS);
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS);
        }
        LOG.info("IceBridge control server stopped");
    }
}