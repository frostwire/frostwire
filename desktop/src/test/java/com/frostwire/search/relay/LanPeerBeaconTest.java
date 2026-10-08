/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LanPeerBeaconTest {

  private static int freeUdpPort() throws Exception {
    try (DatagramSocket s = new DatagramSocket(0)) {
      return s.getLocalPort();
    }
  }

  @Test
  void wireFormatRoundTripsAndRejectsEverythingElse() {
    byte[] ok = LanPeerBeacon.encode(6888);
    assertEquals(6888, LanPeerBeacon.decode(ok, ok.length));
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
          "FWIB1 123456"
        }) {
      byte[] data = bad.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
      assertEquals(-1, LanPeerBeacon.decode(data, data.length), "'" + bad + "'");
    }
    byte[] oversized = new byte[65];
    java.util.Arrays.fill(oversized, (byte) 'A');
    assertEquals(-1, LanPeerBeacon.decode(oversized, oversized.length));
    assertEquals(-1, LanPeerBeacon.decode(ok, 0));
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
  void aNodeLearnsTheAddressAndIdentityPortOfAnotherNodeOnTheNetwork() throws Exception {
    int listenerUdp = freeUdpPort();
    int announcerUdp = freeUdpPort();
    InetAddress loopback = InetAddress.getLoopbackAddress();
    try (LanPeerBeacon listener =
            new LanPeerBeacon(
                7000,
                listenerUdp,
                Collections.emptyList(),
                a -> true,
                100,
                System::currentTimeMillis,
                false);
        LanPeerBeacon announcer =
            new LanPeerBeacon(
                6888,
                announcerUdp,
                List.of(new InetSocketAddress(loopback, listenerUdp)),
                a -> true,
                100,
                System::currentTimeMillis,
                false)) {
      listener.start();
      announcer.start();

      List<DiscoveredEndpoint> found = List.of();
      for (int i = 0; i < 50 && found.isEmpty(); i++) {
        Thread.sleep(100);
        found = listener.fetchEndpoints();
      }

      assertEquals(1, found.size(), "the announcement must arrive");
      assertEquals(loopback.getHostAddress(), found.get(0).host);
      assertEquals(6888, found.get(0).port);
      assertTrue(found.get(0).preferred, "same-network peers are tried first");
    }
  }

  @Test
  void sendersThatAreNotOnTheLanAreIgnored() throws Exception {
    int listenerUdp = freeUdpPort();
    int announcerUdp = freeUdpPort();
    InetAddress loopback = InetAddress.getLoopbackAddress();
    try (LanPeerBeacon listener =
            new LanPeerBeacon(
                7000,
                listenerUdp,
                Collections.emptyList(),
                LanPeerBeacon::isPrivateLanAddress,
                100,
                System::currentTimeMillis,
                false);
        LanPeerBeacon announcer =
            new LanPeerBeacon(
                6888,
                announcerUdp,
                List.of(new InetSocketAddress(loopback, listenerUdp)),
                a -> true,
                100,
                System::currentTimeMillis,
                false)) {
      listener.start();
      announcer.start();
      Thread.sleep(1000);
      assertTrue(
          listener.fetchEndpoints().isEmpty(), "a beacon from a non-private address is spoofable");
    }
  }

  @Test
  void entriesExpireAndTheTableIsBounded() {
    AtomicLong now = new AtomicLong(1_000_000);
    LanPeerBeacon beacon =
        new LanPeerBeacon(6888, 0, Collections.emptyList(), a -> true, 100, now::get, false);

    beacon.remember("192.168.1.10", 6888);
    assertEquals(1, beacon.fetchEndpoints().size());
    now.addAndGet(LanPeerBeacon.ENTRY_TTL_MS + 1);
    assertTrue(beacon.fetchEndpoints().isEmpty(), "a node that went away is forgotten");

    for (int i = 0; i < LanPeerBeacon.MAX_ENTRIES + 50; i++) {
      beacon.remember("10.0." + (i / 250) + "." + (i % 250 + 1), 6888);
    }
    assertEquals(LanPeerBeacon.MAX_ENTRIES, beacon.fetchEndpoints().size());
  }

  @Test
  void closedBeaconIsInertAndRejectsBadPorts() {
    LanPeerBeacon beacon =
        new LanPeerBeacon(
            6888, 0, Collections.emptyList(), a -> true, 100, System::currentTimeMillis, false);
    beacon.close();
    beacon.start();
    assertTrue(beacon.fetchEndpoints().isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LanPeerBeacon(
                0, 0, Collections.emptyList(), a -> true, 100, System::currentTimeMillis, false));
  }
}
