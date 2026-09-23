/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.sim;

import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchResult;
import com.frostwire.search.relay.DistributedSearchPerformer;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IndexDigest;
import com.frostwire.search.relay.LocalIndexTable;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.IceBridgeTopology;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** A bounded end-to-end loopback run using independent production IceBridge stacks. */
public final class LoopbackNetworkSimulator {

  public record NodeStats(
      int id, int controlPort, int udpPort, long packetsIn, long packetsOut, long bytesOut) {}

  public record Report(
      int nodes,
      int findableHits,
      int unfindableHits,
      long findableMillis,
      long unfindableMillis,
      long packetsIn,
      long packetsOut,
      long bytesOut,
      int digestsKnown,
      boolean uniquePorts,
      List<NodeStats> nodeStats) {}

  private static final class Node implements AutoCloseable {
    final int id;
    final IceBridgeServer server;
    final IceBridgeClient client;
    final IdentityKeys identity;
    final LocalIndexTable index;
    final PeerDirectory directory;
    final IceBridgeSearchTransport transport;
    final IncomingSearchRequestHandler handler;

    Node(int id, Path workDirectory) throws Exception {
      this.id = id;
      Path directoryPath = Files.createDirectories(workDirectory.resolve("node-" + id));
      Path identityPath = directoryPath.resolve("identity.dat");
      IdentityKeys.save(IdentityKeys.generate(0), identityPath.toFile());
      IceBridgeConfig config =
          IceBridgeConfig.newBuilder()
              .host("127.0.0.1")
              .rudpPort(0)
              .relayPort(0)
              .controlHttpPort(freeControlPort())
              .role(IceBridgeConfig.Role.BOTH)
              .identityFile(identityPath.toFile())
              .authTokensFile(directoryPath.resolve("tokens.txt").toFile())
              .maxPeers(32)
              .maxSessions(32)
              .peerTtlSec(300)
              .maxQpsPerKey(100)
              .dhtEnabled(false)
              .build();
      Deque<AutoCloseable> started = new ArrayDeque<>();
      try {
        server = new IceBridgeServer(config);
        started.push(server);
        server.start(true);
        identity = server.identity();
        client = new IceBridgeClient(server.controlPort());
        started.push(client);
        client.setAuthToken(server.authToken());
        if (!client.health()) {
          throw new IllegalStateException("control server not healthy for node " + id);
        }
        index = LocalIndexTable.open(directoryPath.resolve("shared.db").toFile());
        started.push(index);
        directory = new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null)));
        transport = new IceBridgeSearchTransport(client);
        started.push(transport);
        transport.setMetrics(server.metrics());
        transport.start();
        RelaySearchService service =
            new RelaySearchService(index, identity, hash -> index.get(hash).isPresent());
        handler = new IncomingSearchRequestHandler(transport, service, directory, identity, index);
        started.push(handler::stop);
        handler.start();
      } catch (Exception failure) {
        for (AutoCloseable resource : started) {
          try {
            resource.close();
          } catch (Exception cleanup) {
            failure.addSuppressed(cleanup);
          }
        }
        throw failure;
      }
    }

    @Override
    public void close() {
      try {
        handler.stop();
      } finally {
        try {
          transport.close();
        } finally {
          try {
            client.close();
          } finally {
            try {
              index.close();
            } finally {
              server.close();
            }
          }
        }
      }
    }
  }

  public Report run(int count, Path workDirectory) throws Exception {
    if (count < 2 || count > 20) {
      throw new IllegalArgumentException("loopback nodes must be between 2 and 20");
    }
    List<Node> nodes = new ArrayList<>();
    try {
      for (int id = 0; id < count; id++) {
        nodes.add(new Node(id, workDirectory));
      }
      for (Node from : nodes) {
        for (Node to : nodes) {
          if (from == to) {
            continue;
          }
          from.directory.upsertVerified(
              to.identity.ed25519PubRaw(), "127.0.0.1", to.server.rudpPort());
          if (!from.client.route(
              to.identity.ed25519PubRaw(),
              "127.0.0.1",
              to.server.rudpPort(),
              IceBridgeConfig.Role.BOTH)) {
            throw new IllegalStateException("failed to route node " + from.id + " to " + to.id);
          }
        }
      }

      Node holder = nodes.get(count - 1);
      holder.index.upsert(torrent(holder));
      Node origin = nodes.get(0);
      if (!holder.client.send(
          origin.identity.ed25519PubRaw(),
          MeshProtocolId.INDEX_DIGEST,
          IndexDigest.build(List.of("icebridgebenchmarkatlas")).toBytes())) {
        throw new IllegalStateException("holder digest announcement not queued");
      }
      long digestDeadline = System.nanoTime() + 5_000_000_000L;
      while (origin.directory.digestCount() == 0 && System.nanoTime() < digestDeadline) {
        Thread.sleep(20);
      }
      if (origin.directory.digestCount() == 0) {
        throw new IllegalStateException("holder digest was not applied by the transport handler");
      }
      // TTL=1 makes every response attributable to its directly queried signer, and empty
      // finals complete misses. Forwarding is exercised separately by MultiRelayMeshSearchTest.
      IceBridgeTopology topology = IceBridgeTopology.get();
      int previousTtl = topology.searchTtl();
      int found;
      int absent;
      long findableMillis;
      long unfindableMillis;
      topology.applyRemote(0, 0, 0, 1);
      try {
        long start = System.nanoTime();
        found = search(origin, "icebridgebenchmarkatlas", 1);
        findableMillis = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        absent = search(origin, "icebridgebenchmarknomatch", 2);
        unfindableMillis = (System.nanoTime() - start) / 1_000_000;
      } finally {
        topology.applyRemote(0, 0, 0, previousTtl);
      }

      long in = 0;
      long out = 0;
      long bytes = 0;
      Set<Integer> controlPorts = new HashSet<>();
      Set<Integer> udpPorts = new HashSet<>();
      List<NodeStats> stats = new ArrayList<>();
      for (Node node : nodes) {
        IceBridgeMetrics metrics = node.server.metrics();
        controlPorts.add(node.server.controlPort());
        udpPorts.add(node.server.rudpPort());
        in += metrics.rudpPacketsIn();
        out += metrics.rudpPacketsOut();
        bytes += metrics.rudpBytesOut();
        stats.add(
            new NodeStats(
                node.id,
                node.server.controlPort(),
                node.server.rudpPort(),
                metrics.rudpPacketsIn(),
                metrics.rudpPacketsOut(),
                metrics.rudpBytesOut()));
      }
      return new Report(
          count,
          found,
          absent,
          findableMillis,
          unfindableMillis,
          in,
          out,
          bytes,
          origin.directory.digestCount(),
          controlPorts.size() == count && udpPorts.size() == count,
          List.copyOf(stats));
    } finally {
      Collections.reverse(nodes);
      for (Node node : nodes) {
        node.close();
      }
    }
  }

  private static int search(Node origin, String keywords, long token) {
    class Results implements SearchListener {
      final List<SearchResult> rows = new CopyOnWriteArrayList<>();
      final AtomicReference<SearchError> error = new AtomicReference<>();

      @Override
      public void onResults(long ignored, List<? extends SearchResult> result) {
        rows.addAll(result);
      }

      @Override
      public void onError(long ignored, SearchError error) {
        this.error.set(error);
      }

      @Override
      public void onStopped(long ignored) {}
    }
    Results results = new Results();
    DistributedSearchPerformer performer =
        new DistributedSearchPerformer(
            token,
            keywords,
            origin.index,
            origin.directory,
            origin.identity,
            origin.transport,
            Math.min(64, origin.directory.size()),
            50,
            25,
            10);
    performer.setListener(results);
    performer.perform();
    if (results.error.get() != null) {
      throw new IllegalStateException("distributed search failed: " + results.error.get());
    }
    return results.rows.size();
  }

  private static LocalSharedTorrent torrent(Node holder) {
    byte[] hash = new byte[20];
    hash[0] = 42;
    long now = System.currentTimeMillis() / 1000;
    return new LocalSharedTorrent.Builder()
        .infoHash(hash)
        .name("icebridgebenchmarkatlas")
        .sizeBytes(1024)
        .fileCount(1)
        .filesJson("[]")
        .publisherNodeId(holder.identity.nodeId())
        .publisherEd25519Pub(holder.identity.ed25519PubRaw())
        .publisherUtpPort(0)
        .addedAt(now)
        .lastSeenAt(now)
        .build();
  }

  private static int freeControlPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }
}
