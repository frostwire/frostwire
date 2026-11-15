/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.util.Logger;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * rUDP listener for the IceBridge servent.
 *
 * <p>Binds a UDP socket and dispatches decoded packets to a {@link RudpSessionManager}. The manager
 * handles reliability, hole punching, and relay forwarding.
 */
@SuppressWarnings("deprecation")
public final class RudpServer implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(RudpServer.class);

  /**
   * A single ingest worker: Netty delivers datagrams for a channel on one event-loop thread in
   * order, and fragment reassembly depends on that ordering. More workers would reorder
   * DATA_FRAG/DATA_END frames across threads and break reassembly, and they could not process
   * faster anyway because {@link RudpSessionManager#onPacket} is globally serialized. The win here
   * is only that the event loop hands off instead of blocking on that monitor.
   *
   * <p>A full queue <b>drops</b> the newest datagram instead of running it on the event loop (which
   * would let it overtake already-queued, older frames and break FIFO ordering). Dropping is safe:
   * rUDP is reliable, so a datagram whose sequence/fragment was never accepted is retransmitted by
   * the sender, whereas an out-of-order delivery is discarded anyway and costs the same
   * retransmission without ever risking an ordering violation.
   */
  private static final int INGEST_THREADS = 1;

  private static final int INGEST_QUEUE_CAPACITY = 4096;

  private final IceBridgeConfig config;
  private final RudpSessionManager manager;
  private final IngestExecutor ingestExecutor =
      new IngestExecutor(INGEST_THREADS, INGEST_QUEUE_CAPACITY);
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
    Bootstrap bootstrap =
        new Bootstrap()
            .group(group)
            .channel(NioDatagramChannel.class)
            .handler(
                new ChannelInitializer<DatagramChannel>() {
                  @Override
                  protected void initChannel(DatagramChannel ch) {
                    ch.pipeline().addLast(new RudpPacketCodec()).addLast(new PacketHandler());
                  }
                });
    channel = bind(bootstrap, config.host(), port);
    manager.setChannel(channel);
    LOG.info("IceBridge rUDP server listening on " + config.host() + ":" + actualPort());
  }

  /**
   * Bind the rUDP socket so that no IPv4-only socket of another program shares its port.
   *
   * <p>Netty opens a dual-stack IPv6 socket, and an IPv4 host such as {@code 0.0.0.0} binds it to
   * the IPv6 wildcard. On macOS neither an ephemeral nor an explicit dual-stack bind is checked
   * against IPv4-only sockets: it succeeds on a port another program already holds as {@code
   * 0.0.0.0:port}, and the kernel then hands every IPv4 datagram for that port to the other socket.
   * The node is up but deaf to IPv4 peers: their requests are "sent" and never arrive, so a mesh
   * metadata fetch waits out its whole timeout.
   *
   * <p>So the port is first claimed with an IPv4 socket, whose bind the kernel does check against
   * IPv4 owners: a configured port another program holds fails here like any port in use, and an
   * ephemeral port is one no IPv4 socket holds. The dual-stack socket binds that port while the
   * claim still holds it. Where the kernel refuses that overlap (Linux, Windows, an IPv4-only
   * stack) it binds once the claim is released, and that kernel checks IPv4 owners itself.
   */
  static Channel bind(Bootstrap bootstrap, String host, int port) throws InterruptedException {
    if (!NetUtil.isValidIpV4Address(host)) {
      return bootstrap.bind(host, port).sync().channel();
    }
    java.nio.channels.DatagramChannel claim = claimIpv4Port(host, port);
    try {
      int claimed = ((InetSocketAddress) claim.socket().getLocalSocketAddress()).getPort();
      ChannelFuture overlapping = bootstrap.bind(host, claimed).await();
      if (overlapping.isSuccess()) {
        return overlapping.channel();
      }
      closeQuietly(claim);
      return bootstrap.bind(host, claimed).sync().channel();
    } finally {
      closeQuietly(claim);
    }
  }

  private static java.nio.channels.DatagramChannel claimIpv4Port(String host, int port) {
    java.nio.channels.DatagramChannel claim = null;
    try {
      claim = java.nio.channels.DatagramChannel.open(StandardProtocolFamily.INET);
      claim.bind(new InetSocketAddress(host, port));
      return claim;
    } catch (IOException e) {
      closeQuietly(claim);
      throw new IllegalStateException("rUDP port " + port + " is not available on " + host, e);
    }
  }

  private static void closeQuietly(java.nio.channels.DatagramChannel claim) {
    if (claim == null) {
      return;
    }
    try {
      claim.close();
    } catch (IOException ignored) {
      // The claim only ever reserved the port.
    }
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
      // ingest pool. A full queue drops the datagram (rUDP retransmits) rather
      // than running it here out of order.
      ingestExecutor.submit(() -> manager.onPacket(envelope));
    }
  }

  /**
   * Bounded hand-off from the Netty event loop to rUDP packet processing. Accepted tasks are
   * processed FIFO by a single worker. Once the queue is full the newest submission is dropped
   * (returned {@code false}) rather than run on the caller: running it would process it ahead of
   * queued older frames and violate the ordering that fragment reassembly and sequence checks rely
   * on. The sender's rUDP retransmission recovers the dropped datagram.
   */
  static final class IngestExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final int queueCapacity;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong dropped = new AtomicLong();

    IngestExecutor(int threads, int queueCapacity) {
      this.queueCapacity = queueCapacity;
      AtomicInteger sequence = new AtomicInteger();
      ThreadFactory threadFactory =
          runnable -> {
            Thread thread =
                new Thread(runnable, "icebridge-rudp-worker-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
          };
      executor =
          new ThreadPoolExecutor(
              threads,
              threads,
              0L,
              TimeUnit.MILLISECONDS,
              new ArrayBlockingQueue<>(queueCapacity),
              threadFactory,
              new ThreadPoolExecutor.AbortPolicy());
    }

    boolean submit(Runnable task) {
      if (task == null || closed.get()) {
        return false;
      }
      try {
        executor.execute(task);
        return true;
      } catch (RejectedExecutionException e) {
        dropped.incrementAndGet();
        return false;
      }
    }

    /** Datagrams dropped because the ingest queue was full (recovered by rUDP retransmit). */
    long droppedCount() {
      return dropped.get();
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
