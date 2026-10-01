/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityRecord;
import com.frostwire.search.relay.IdentityRecordPublisher;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.PeerRegistrySync;
import com.google.gson.JsonParser;
import com.limegroup.gnutella.gui.IceBridgeStartup;
import com.limegroup.gnutella.settings.SearchEnginesSettings.RudpPortMigration;
import com.sun.net.httpserver.HttpServer;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IceBridgeAutomaticPortTest {
  @Test
  void migratesLegacyDefaultOnlyOnceAndPreservesManualPorts() {
    assertEquals(0, RudpPortMigration.port(6889, false, false));
    assertEquals(0, RudpPortMigration.port(0, false, false));
    assertEquals(6890, RudpPortMigration.port(6890, false, false));
    assertEquals(45000, RudpPortMigration.port(45000, false, false));
    assertEquals(6889, RudpPortMigration.port(6889, true, false));
    assertEquals(0, RudpPortMigration.port(0, true, false));
    assertEquals(6889, RudpPortMigration.port(6889, false, true));
  }

  @Test
  void environmentOverridesSurviveMigrationWithoutChangingSavedPort() {
    int savedPort = RudpPortMigration.port(6889, false, false);
    assertEquals(6889, IceBridgeStartup.requestedPort(savedPort, "6889"));
    assertEquals(45001, IceBridgeStartup.requestedPort(savedPort, "45001"));
    assertEquals(0, IceBridgeStartup.requestedPort(45001, "0"));
    for (String invalid : new String[] {"", "oops", "-1", "65536"}) {
      assertEquals(45001, IceBridgeStartup.requestedPort(45001, invalid));
    }
    assertEquals(0, savedPort);
  }

  @Test
  void bindsBeforeReadingPortAndFeedsSameActualPortToAllAnnouncements() throws Exception {
    List<String> events = new ArrayList<>();
    AtomicReference<DatagramSocket> child = new AtomicReference<>();
    IdentityKeys identity = IdentityKeys.generate();
    AtomicReference<IdentityRecord> signedRecord = new AtomicReference<>();
    AtomicReference<IdentityRecord> dhtRecord = new AtomicReference<>();
    AtomicInteger registryPort = new AtomicInteger();
    HttpServer control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    control.createContext(
        "/register",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          registryPort.set(
              JsonParser.parseString(body).getAsJsonObject().get("rudpPort").getAsInt());
          byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          try (var output = exchange.getResponseBody()) {
            output.write(response);
          }
        });
    control.createContext(
        "/lookup",
        exchange -> {
          byte[] response = "{\"ok\":true,\"data\":[]}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          try (var output = exchange.getResponseBody()) {
            output.write(response);
          }
        });
    control.start();
    try (IceBridgeClient client =
        new IceBridgeClient("http://127.0.0.1:" + control.getAddress().getPort())) {
      int actualPort =
          IceBridgeStartup.startLocal(
              () -> {
                events.add("healthy");
                try {
                  child.set(new DatagramSocket(0));
                } catch (java.net.SocketException e) {
                  throw new IllegalStateException(e);
                }
                return true;
              },
              () -> {
                events.add("bound-port");
                return child.get().getLocalPort();
              },
              () -> true);
      assertTrue(actualPort > 0);
      assertEquals(child.get().getLocalPort(), actualPort);
      IceBridgeStartup.announce(
          actualPort,
          () -> true,
          port -> {
            events.add("tcp");
            signedRecord.set(
                IdentityRecord.createSigned(
                    identity.nodeId(),
                    identity.ed25519(),
                    identity.x25519PubRaw(),
                    6888,
                    port,
                    "BOTH"));
          },
          port -> {
            events.add("dht");
            SessionManager session =
                new SessionManager() {
                  @Override
                  public void dhtPutItem(byte[] pub, byte[] secret, Entry entry, byte[] salt) {
                    dhtRecord.set(IdentityRecord.fromEntry(entry));
                  }
                };
            assertEquals(
                1, new IdentityRecordPublisher(identity, 6888, port, "BOTH").publish(session));
          },
          port -> {
            events.add("registry");
            try (PeerKarmaCache karma =
                    new PeerKarmaCache(new RemoteKarmaChainFetcher(pub -> null));
                PeerRegistrySync sync =
                    new PeerRegistrySync(
                        client,
                        new PeerDirectory(karma),
                        "127.0.0.1",
                        port,
                        identity,
                        IceBridgeConfig.Role.BOTH)) {
              sync.sync();
            }
          });
      assertEquals(List.of("healthy", "bound-port", "tcp", "dht", "registry"), events);
      assertEquals(actualPort, signedRecord.get().rudpPort());
      assertTrue(signedRecord.get().verifySignature());
      assertNotNull(dhtRecord.get());
      assertEquals(actualPort, dhtRecord.get().rudpPort());
      assertTrue(dhtRecord.get().verifySignature());
      assertEquals(actualPort, registryPort.get());
    } finally {
      control.stop(0);
      if (child.get() != null) child.get().close();
    }
  }

  @Test
  void failedOrCancelledStartupCannotReadOrAnnounceAPort() throws Exception {
    AtomicBoolean active = new AtomicBoolean(true);
    AtomicInteger reads = new AtomicInteger();
    AtomicInteger announcements = new AtomicInteger();
    int failed = IceBridgeStartup.startLocal(() -> false, reads::incrementAndGet, active::get);
    IceBridgeStartup.announce(failed, active::get, announcements::set);
    int cancelled =
        IceBridgeStartup.startLocal(
            () -> {
              active.set(false);
              return true;
            },
            reads::incrementAndGet,
            active::get);
    IceBridgeStartup.announce(cancelled, active::get, announcements::set);
    assertEquals(0, reads.get());
    assertEquals(0, announcements.get());
    assertThrows(
        IllegalStateException.class,
        () -> IceBridgeStartup.startLocal(() -> true, () -> 0, () -> true));
    assertThrows(
        InterruptedException.class,
        () ->
            IceBridgeStartup.startLocal(
                () -> {
                  throw new InterruptedException();
                },
                reads::incrementAndGet,
                () -> true));
  }

  @Test
  void remoteUnknownPortIsNotAnnouncedAndCancellationStopsLaterServices() {
    AtomicInteger calls = new AtomicInteger();
    IceBridgeStartup.announce(0, () -> true, calls::set);
    assertEquals(0, calls.get());
    AtomicBoolean active = new AtomicBoolean(true);
    IceBridgeStartup.announce(
        45002,
        active::get,
        port -> {
          calls.incrementAndGet();
          active.set(false);
        },
        port -> calls.incrementAndGet());
    assertEquals(1, calls.get());
  }
}
