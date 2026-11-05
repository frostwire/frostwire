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

  /**
   * Starts {@code beacon} and returns the port it listens on. Beacons bind port 0 and report the
   * port they got: probing for a free port first and binding it later let another socket take it in
   * between, and a beacon that could not bind silently heard nothing (frostwire/frostwire#1487).
   */
  private static int started(LanPeerBeacon beacon) {
    beacon.start();
    int port = beacon.localPort();
    assertTrue(port > 0, "the beacon did not start listening");
    return port;
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
    try (LanPeerBeacon beacon = listener(0, LanPeerBeacon.ENTRY_TTL_MS)) {
      int udpPort = started(beacon);
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
    try (LanPeerBeacon beacon =
        new LanPeerBeacon(
            7000,
            0,
            Collections.emptyList(),
            LanPeerBeacon::isPrivateLanAddress,
            100,
            LanPeerBeacon.ENTRY_TTL_MS,
            false)) {
      int udpPort = started(beacon);
      send(udpPort, "FWIB1 6888"); // from loopback, which is not a private LAN address
      Thread.sleep(500);
      assertTrue(beacon.fetchEndpoints().isEmpty(), "a beacon from outside the LAN is spoofable");
    }
  }

  @Test
  void aNodeLearnsTheAddressAndRudpPortOfAnotherNodeOnTheNetwork() throws Exception {
    InetAddress loopback = InetAddress.getLoopbackAddress();
    try (LanPeerBeacon listener = listener(0, LanPeerBeacon.ENTRY_TTL_MS)) {
      int listenerUdp = started(listener);
      try (LanPeerBeacon announcer =
          new LanPeerBeacon(
              6889,
              0,
              List.of(new InetSocketAddress(loopback, listenerUdp)),
              address -> true,
              100,
              LanPeerBeacon.ENTRY_TTL_MS,
              false)) {
        started(announcer);

        assertTrue(
            await(() -> !listener.fetchEndpoints().isEmpty()), "the announcement must arrive");
        DiscoveredEndpoint found = listener.fetchEndpoints().get(0);
        assertEquals(loopback.getHostAddress(), found.host);
        assertEquals(6889, found.port);
      }
    }
  }

  @Test
  void aNodeThatWentAwayIsForgotten() throws Exception {
    try (LanPeerBeacon beacon = listener(0, 300)) {
      int udpPort = started(beacon);
      send(udpPort, "FWIB1 6888");
      assertTrue(await(() -> !beacon.fetchEndpoints().isEmpty()));

      assertTrue(await(() -> beacon.fetchEndpoints().isEmpty()), "entries expire after the ttl");
    }
  }

  @Test
  void theTableOfKnownNodesIsBounded() throws Exception {
    try (LanPeerBeacon beacon = listener(0, LanPeerBeacon.ENTRY_TTL_MS)) {
      int udpPort = started(beacon);
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
    assertEquals(-1, beacon.localPort(), "a closed beacon does not listen");
    assertTrue(beacon.fetchEndpoints().isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () -> new LanPeerBeacon(0, 0, Collections.emptyList(), address -> true, 100, 1000, false));
  }
}
