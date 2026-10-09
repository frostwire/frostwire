/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class LanPeerBeaconTest {

  private static final long AWAIT_MS = 3_000;

  private static int freeUdpPort() throws Exception {
    try (DatagramSocket s = new DatagramSocket(0)) {
      return s.getLocalPort();
    }
  }

  private static LanPeerBeacon listener(int udpPort, long entryTtlMs) {
    return new LanPeerBeacon(
        7000, udpPort, Collections.emptyList(), address -> true, 100, entryTtlMs, false);
  }

  private static void send(int udpPort, String text) throws Exception {
    byte[] data = text.getBytes(StandardCharsets.US_ASCII);
    try (DatagramSocket socket = new DatagramSocket()) {
      socket.send(
          new DatagramPacket(
              data, data.length, new InetSocketAddress(InetAddress.getLoopbackAddress(), udpPort)));
    }
  }

  private static boolean await(BooleanSupplier condition) throws Exception {
    long deadline = System.currentTimeMillis() + AWAIT_MS;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) return true;
      Thread.sleep(25);
    }
    return condition.getAsBoolean();
  }

  @Test
  void onlyWellFormedBeaconsAreBelieved() throws Exception {
    int udpPort = freeUdpPort();
    try (LanPeerBeacon beacon = listener(udpPort, LanPeerBeacon.ENTRY_TTL_MS)) {
      beacon.start();
      for (String bad :
          new String[] {
            "",
            "FWIB1",
            "FWIB1 ",
            "FWIB1 0",
            "FWIB1 65536",
            "FWIB1 12a",
            "FWIB1 -5",
            "FWIB2 6888",
            "fwib1 6888",
            "FWIB1 6888 extra",
            "XXXXX 5",
            "FWIB1 123456",
            "A".repeat(65)
          }) {
        send(udpPort, bad);
      }
      send(udpPort, "FWIB1 6888");

      assertTrue(await(() -> !beacon.fetchEndpoints().isEmpty()), "the valid beacon must arrive");
      List<DiscoveredEndpoint> found = beacon.fetchEndpoints();
      assertEquals(1, found.size(), "malformed datagrams are ignored: " + found);
      assertEquals(InetAddress.getLoopbackAddress().getHostAddress(), found.get(0).host);
      assertEquals(6888, found.get(0).port);
      assertTrue(found.get(0).preferred, "same-network peers are tried first");
    }
  }

  @Test
  void onlyPrivateIpv4SendersAreHonestLanPeers() throws Exception {
    for (String address : new String[] {"192.168.4.36", "10.0.0.9", "172.16.0.1", "172.31.255.1"}) {
      assertTrue(LanPeerBeacon.isPrivateLanAddress(InetAddress.getByName(address)), address);
    }
    for (String address :
        new String[] {
          "8.8.8.8",
          "76.130.103.132",
          "172.32.0.1",
          "127.0.0.1",
          "169.254.1.1",
          "::1",
          "fe80::1",
          "100.64.0.1"
        }) {
      assertFalse(LanPeerBeacon.isPrivateLanAddress(InetAddress.getByName(address)), address);
    }
  }

  @Test
  void sendersThatAreNotOnTheLanAreIgnored() throws Exception {
    int udpPort = freeUdpPort();
    try (LanPeerBeacon beacon =
        new LanPeerBeacon(
            7000,
            udpPort,
            Collections.emptyList(),
            LanPeerBeacon::isPrivateLanAddress,
            100,
            LanPeerBeacon.ENTRY_TTL_MS,
            false)) {
      beacon.start();
      send(udpPort, "FWIB1 6888"); // from loopback, which is not a private LAN address
      Thread.sleep(500);
      assertTrue(beacon.fetchEndpoints().isEmpty(), "a beacon from outside the LAN is spoofable");
    }
  }

  @Test
  void aNodeLearnsTheAddressAndRudpPortOfAnotherNodeOnTheNetwork() throws Exception {
    int listenerUdp = freeUdpPort();
    int announcerUdp = freeUdpPort();
    InetAddress loopback = InetAddress.getLoopbackAddress();
    try (LanPeerBeacon listener = listener(listenerUdp, LanPeerBeacon.ENTRY_TTL_MS);
        LanPeerBeacon announcer =
            new LanPeerBeacon(
                6889,
                announcerUdp,
                List.of(new InetSocketAddress(loopback, listenerUdp)),
                address -> true,
                100,
                LanPeerBeacon.ENTRY_TTL_MS,
                false)) {
      listener.start();
      announcer.start();

      assertTrue(await(() -> !listener.fetchEndpoints().isEmpty()), "the announcement must arrive");
      DiscoveredEndpoint found = listener.fetchEndpoints().get(0);
      assertEquals(loopback.getHostAddress(), found.host);
      assertEquals(6889, found.port);
    }
  }

  @Test
  void aNodeThatWentAwayIsForgotten() throws Exception {
    int udpPort = freeUdpPort();
    try (LanPeerBeacon beacon = listener(udpPort, 300)) {
      beacon.start();
      send(udpPort, "FWIB1 6888");
      assertTrue(await(() -> !beacon.fetchEndpoints().isEmpty()));

      assertTrue(await(() -> beacon.fetchEndpoints().isEmpty()), "entries expire after the ttl");
    }
  }

  @Test
  void theTableOfKnownNodesIsBounded() throws Exception {
    int udpPort = freeUdpPort();
    try (LanPeerBeacon beacon = listener(udpPort, LanPeerBeacon.ENTRY_TTL_MS)) {
      beacon.start();
      int sent = LanPeerBeacon.MAX_ENTRIES + 40;
      for (int i = 0; i < sent; i++) {
        send(udpPort, "FWIB1 " + (10_000 + i));
      }

      assertTrue(await(() -> beacon.fetchEndpoints().size() == LanPeerBeacon.MAX_ENTRIES));
      Thread.sleep(300);
      assertEquals(LanPeerBeacon.MAX_ENTRIES, beacon.fetchEndpoints().size());
    }
  }

  @Test
  void aClosedBeaconIsInertAndBadPortsAreRejected() {
    LanPeerBeacon beacon = listener(0, LanPeerBeacon.ENTRY_TTL_MS);
    beacon.close();
    beacon.start();
    assertTrue(beacon.fetchEndpoints().isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () -> new LanPeerBeacon(0, 0, Collections.emptyList(), address -> true, 100, 1000, false));
  }
}
