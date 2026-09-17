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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * rUDP ingest workers. A small fixed pool is enough: {@link
     * RudpSessionManager#onPacket} is globally synchronized, so extra threads only
     * absorb bursty arrivals while keeping the Netty event loop free. The bounded
     * queue plus {@link ThreadPoolExecutor.CallerRunsPolicy} provides backpressure
     * instead of silently dropping decoded datagrams.
     */
    /**
     * A single ingest worker: Netty delivers datagrams for a channel on one event-loop thread in
     * order, and fragment reassembly depends on that ordering. More workers would reorder
     * DATA_FRAG/DATA_END frames across threads and break reassembly, and they could not process
     * faster anyway because {@link RudpSessionManager#onPacket} is globally serialized. The win
     * here is only that the event loop hands off instead of blocking on that monitor.
     */
    private static final int INGEST_THREADS = 1;
    private static final int INGEST_QUEUE_CAPACITY = 4096;

    private final IceBridgeConfig config;
    private final RudpSessionManager manager;
    private final IngestExecutor ingestExecutor = new IngestExecutor(INGEST_THREADS, INGEST_QUEUE_CAPACITY);
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
        ingestExecutor.shutdown();
        if (group != null) {
            group.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS);
        }
        LOG.info("IceBridge rUDP server stopped");
    }

    private final class PacketHandler extends SimpleChannelInboundHandler<RudpPacketEnvelope> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, RudpPacketEnvelope envelope) {
            // Frame/decode only on the event loop; hand the envelope to the bounded
            // ingest pool. CallerRunsPolicy means a saturated pool briefly runs the
            // task on the event loop rather than dropping the datagram.
            ingestExecutor.submit(() -> manager.onPacket(envelope));
        }
    }

    /**
     * Bounded hand-off from the Netty event loop to rUDP packet processing. Submission
     * never throws and never blocks unbounded: once the queue is full the caller runs
     * the task itself ({@link ThreadPoolExecutor.CallerRunsPolicy}), and once closed
     * further submissions are ignored.
     */
    static final class IngestExecutor implements AutoCloseable {
        private final ThreadPoolExecutor executor;
        private final int queueCapacity;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        IngestExecutor(int threads, int queueCapacity) {
            this.queueCapacity = queueCapacity;
            AtomicInteger sequence = new AtomicInteger();
            ThreadFactory threadFactory = runnable -> {
                Thread thread = new Thread(runnable, "icebridge-rudp-worker-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacity), threadFactory,
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }

        boolean submit(Runnable task) {
            if (task == null || closed.get()) {
                return false;
            }
            try {
                executor.execute(task);
                return true;
            } catch (RejectedExecutionException e) {
                return false;
            }
        }

        int queueCapacity() {
            return queueCapacity;
        }

        boolean isShutdown() {
            return executor.isShutdown();
        }

        void shutdown() {
            if (closed.compareAndSet(false, true)) {
                executor.shutdown();
            }
        }

        @Override
        public void close() {
            shutdown();
        }
    }
}