/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.search.relay.icebridge.client;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeServer;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import java.io.File;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutomaticRudpBindingTest {
  @TempDir Path directory;

  @Test
  void automaticClientsBindDistinctEndpointsAndAuthenticateToTheSameHub() throws Exception {
    List<IceBridgeServer> servers = new ArrayList<>();
    List<IceBridgeClient> clients = new ArrayList<>();
    try {
      IceBridgeServer hub = server("hub");
      servers.add(hub);
      IceBridgeClient receiver = client(hub);
      clients.add(receiver);
      Set<Integer> boundPorts = new HashSet<>();
      boundPorts.add(hub.rudpPort());
      for (int i = 0; i < 4; i++) {
        IceBridgeServer leaf = server("leaf" + i);
        servers.add(leaf);
        assertTrue(
            boundPorts.add(leaf.rudpPort()), "Live UDP sockets must never share a local port");
        IceBridgeClient sender = client(leaf);
        clients.add(sender);
        assertEquals(leaf.rudpPort(), sender.rudpPort(2_000));
        assertTrue(
            sender.route(
                hub.identity().ed25519PubRaw(),
                "127.0.0.1",
                hub.rudpPort(),
                IceBridgeConfig.Role.BOTH));
        assertTrue(
            sender.send(
                hub.identity().ed25519PubRaw(), MeshProtocolId.TELEMETRY, new byte[] {(byte) i}));
      }
      Set<String> identities = new HashSet<>();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (identities.size() < 4 && System.nanoTime() < deadline) {
        for (IceBridgeClient.InboundMessage message : receiver.poll(10)) {
          assertEquals(MeshProtocolId.TELEMETRY, message.protocolId());
          identities.add(com.frostwire.util.Hex.encode(message.sourcePub()));
        }
        Thread.sleep(10);
      }
      assertEquals(
          4, identities.size(), "All identities must coexist on one host and reach the hub");
      for (int i = 1; i < servers.size(); i++) {
        assertTrue(
            identities.contains(
                com.frostwire.util.Hex.encode(servers.get(i).identity().ed25519PubRaw())));
      }
    } finally {
      for (IceBridgeClient client : clients) client.close();
      for (IceBridgeServer server : servers) server.close();
    }
  }

  @Test
  void childReportsActualUdpBindAndRetainsItAcrossSupervisedRespawn() throws Exception {
    File identity = identity("child");
    File jar = new File("build/libs/icebridge.jar");
    try (IceBridgeProcessLauncher launcher =
        new IceBridgeProcessLauncher(jar, identity, 0, 0, 0, "CLIENT", "127.0.0.1")) {
      assertEquals(0, launcher.rudpPort());
      assertTrue(launcher.startAndAwaitHealthy(15_000));
      int bound = launcher.rudpPort();
      assertTrue(bound > 0 && bound <= 65535);
      assertEquals(bound, launcher.client().rudpPort(2_000));
      assertThrows(
          SocketException.class,
          () -> {
            try (DatagramSocket unexpected = new DatagramSocket(bound)) {
              fail("The reported port must be occupied by the child's UDP socket");
            }
          });
      long pid =
          Long.parseLong(
              Files.readString(identity.toPath().getParent().resolve("icebridge-child.pid"))
                  .trim());
      launcher.startSupervision(100);
      ProcessHandle.of(pid).orElseThrow().destroyForcibly();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
      boolean recovered = false;
      while (System.nanoTime() < deadline) {
        if (launcher.isAlive()
            && launcher.client().health(200)
            && launcher.client().rudpPort(500) == bound
            && !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
          recovered = true;
          break;
        }
        Thread.sleep(50);
      }
      assertTrue(recovered, "Supervised recovery must retain the advertised endpoint");
      assertEquals(bound, launcher.rudpPort());
    }
  }

  private IceBridgeServer server(String name) throws Exception {
    int controlPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      controlPort = socket.getLocalPort();
    }
    IceBridgeServer server =
        new IceBridgeServer(
            IceBridgeConfig.newBuilder()
                .host("127.0.0.1")
                .rudpPort(0)
                .relayPort(0)
                .controlHttpPort(controlPort)
                .dhtEnabled(false)
                .identityFile(identity(name))
                .build());
    server.start();
    assertTrue(server.rudpPort() > 0);
    return server;
  }

  private IceBridgeClient client(IceBridgeServer server) {
    IceBridgeClient client = new IceBridgeClient(server.controlPort());
    client.setAuthToken(server.authToken());
    client.setOwnPub(server.identity().ed25519PubRaw());
    return client;
  }

  private File identity(String name) throws Exception {
    Path home = Files.createDirectories(directory.resolve(name));
    File file = home.resolve("identity.dat").toFile();
    IdentityKeys.save(IdentityKeys.generate(0), file);
    return file;
  }
}
