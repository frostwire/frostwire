/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.PowTestIdentities;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * How much CPU a rUDP relay spends per inbound packet when it holds many authenticated sessions and
 * forwards between them, which is what the live IceBridge relay does all day (Icebase-free profile
 * of the live relay, 2026-11-17: 92 % of its CPU samples on the rUDP worker, ~74 % of them Ed25519,
 * ~15 % scanning every session for each packet).
 *
 * <p>One relay {@link RudpSessionManager}, N simulated peers with real handshakes, real signatures
 * and a test-owned in-memory channel, driven from one thread: nothing is random and no network or
 * scheduler thread is involved, so the thread CPU time spent inside {@code onPacket} is exact and
 * comparable between code versions. The peers' own signing happens outside the timed region.
 *
 * <p>Opt-in, never part of CI: {@code ICEBRIDGE_RUDP_PERF=1 ./gradlew test --tests
 * '*RudpRelayLoadTest*' -i}. Knobs: {@code ICEBRIDGE_RUDP_PEERS} (default 120, at most 127 of the
 * 128 proof-of-work identities), {@code ICEBRIDGE_RUDP_ROUNDS} (default 30, at most 500), {@code
 * ICEBRIDGE_RUDP_SLOW_PERCENT} (peers that never acknowledge, default 10) and {@code
 * ICEBRIDGE_RUDP_BACKLOG} (unacknowledged frames each of them holds, default 500, at most the 512 a
 * session may queue). It prints one line, {@code RUDP_PERF peers=... cpu_us_per_packet=...
 * alloc_bytes_per_packet=...}.
 */
@EnabledIfEnvironmentVariable(named = "ICEBRIDGE_RUDP_PERF", matches = "1")
class RudpRelayLoadTest {

  private static final int WARMUP_ROUNDS = 10;

  /**
   * The relay admits at most 2000 packets a second overall. The test clock moves 0.6 ms per inbound
   * packet (about 1670 packets a second of virtual time, below that limit) whatever the real CPU
   * speed, so the limiter never decides how many packets the relay processes.
   */
  private static final long VIRTUAL_NS_PER_PACKET = 600_000L;

  private static final class Peer {
    final IdentityKeys id;
    final InetSocketAddress address;
    final long cid;
    byte[] transcript;
    int nextSeq = 1;
    boolean slow;

    Peer(int index) {
      id = PowTestIdentities.get(index);
      address = new InetSocketAddress("198.18." + index + ".1", 62000);
      cid = 7000 + index;
    }
  }

  private final com.sun.management.ThreadMXBean threads =
      (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
  private long virtualNowNs = 1_000_000_000L;
  private IdentityKeys relay;
  private RudpSessionManager manager;
  private EmbeddedChannel channel;
  private int delivered;
  private long cpuNs;
  private long allocBytes;
  private long timedPackets;
  private boolean timing;

  @Test
  void relayCpuPerInboundPacket() throws Exception {
    int peerCount = envInt("ICEBRIDGE_RUDP_PEERS", 120, 2, 127);
    int rounds = envInt("ICEBRIDGE_RUDP_ROUNDS", 30, 1, 500);
    int slowPercent = envInt("ICEBRIDGE_RUDP_SLOW_PERCENT", 10, 0, 50);
    int backlog = envInt("ICEBRIDGE_RUDP_BACKLOG", 500, 0, 500);
    relay = PowTestIdentities.get(127);
    channel = new EmbeddedChannel();
    PeerRegistry registry =
        new PeerRegistry(IceBridgeConfig.newBuilder().controlHttpPort(8797).build());
    manager =
        new RudpSessionManager(
            relay,
            registry,
            new IceBridgeMetrics(),
            (pub, payload) -> delivered++,
            () -> virtualNowNs,
            false);
    manager.setChannel(channel);
    try {
      List<Peer> peers = new ArrayList<>();
      for (int i = 0; i < peerCount; i++) {
        Peer peer = new Peer(i);
        handshake(peer);
        peers.add(peer);
      }
      assertEquals(peerCount, manager.sessionCount());
      int slowCount = Math.min(peerCount - 2, peerCount * slowPercent / 100);
      List<Peer> fast = peers.subList(0, peerCount - slowCount);
      for (Peer slow : peers.subList(peerCount - slowCount, peerCount)) {
        slow.slow = true;
      }

      // Slow peers never acknowledge: each is given a backlog of unacknowledged frames that stays
      // queued for the whole run (a phone that went offline, a NAT that drops the relay's packets).
      // The relay's per-packet bookkeeping then has real queues to account for.
      for (Peer slow : peers.subList(peerCount - slowCount, peerCount)) {
        for (int k = 0; k < backlog; k++) {
          receive(fast.get(0), relayFrame(fast.get(0), slow, k));
          readOutbound();
        }
      }

      // Every fast peer relays one frame per round to the next fast peer; all signed up front.
      int total = WARMUP_ROUNDS + rounds;
      RudpPacket[][] frames = new RudpPacket[fast.size()][total];
      for (int r = 0; r < total; r++) {
        for (int i = 0; i < fast.size(); i++) {
          frames[i][r] = relayFrame(fast.get(i), fast.get((i + 1) % fast.size()), r);
        }
      }

      long relayed = 0;
      for (int r = 0; r < total; r++) {
        timing = r >= WARMUP_ROUNDS;
        for (int i = 0; i < fast.size(); i++) {
          receive(fast.get(i), frames[i][r]);
          relayed += pumpOutbound(peers);
        }
      }
      timing = false;

      assertEquals(
          (long) fast.size() * total, relayed, "every relayed frame is forwarded and acknowledged");
      assertEquals(0, delivered, "frames for other peers are not delivered locally");
      assertTrue(timedPackets > 0);
      System.out.printf(
          "RUDP_PERF peers=%d slow=%d backlog=%d rounds=%d inbound_packets=%d cpu_ms_total=%.1f"
              + " cpu_us_per_packet=%.1f alloc_bytes_per_packet=%d%n",
          peerCount,
          slowCount,
          backlog,
          rounds,
          timedPackets,
          cpuNs / 1e6,
          cpuNs / 1e3 / timedPackets,
          allocBytes / timedPackets);
    } finally {
      manager.shutdown();
      channel.finishAndReleaseAll();
    }
  }

  /**
   * Plays the peers' side of what the relay sent: each forwarded frame is acknowledged by its
   * recipient, which the relay must verify. Returns how many forwarded frames were acknowledged.
   */
  private long pumpOutbound(List<Peer> peers) throws Exception {
    long acked = 0;
    for (RudpPacketEnvelope envelope : readOutbound()) {
      RudpPacket packet = envelope.packet();
      if (packet.type() != RudpPacket.Type.RELAY_RESPONSE) {
        continue;
      }
      Peer to = null;
      for (Peer p : peers) {
        if (p.address.equals(envelope.recipient())) {
          to = p;
          break;
        }
      }
      assertNotNull(to, "forwarded to an unknown address " + envelope.recipient());
      if (to.slow) {
        continue;
      }
      RudpPacket ack =
          RudpAuth.protect(
              to.id,
              relay.ed25519PubRaw(),
              to.transcript,
              new RudpPacket(RudpPacket.Type.DATA_ACK, to.cid, 0, packet.sequence(), new byte[0]));
      receive(to, ack);
      acked++;
    }
    return acked;
  }

  private List<RudpPacketEnvelope> readOutbound() {
    List<RudpPacketEnvelope> sent = new ArrayList<>();
    RudpPacketEnvelope out;
    while ((out = channel.readOutbound()) != null) {
      sent.add(out);
    }
    return sent;
  }

  /** A signed RELAY packet from one peer for another, with the sender's next sequence number. */
  private RudpPacket relayFrame(Peer from, Peer to, int tag) throws Exception {
    byte[] app = new byte[96];
    app[0] = (byte) tag;
    byte[] payload = RelayFrame.encode(from.id.ed25519PubRaw(), to.id.ed25519PubRaw(), 2, app);
    return RudpAuth.protect(
        from.id,
        relay.ed25519PubRaw(),
        from.transcript,
        new RudpPacket(RudpPacket.Type.RELAY, from.cid, from.nextSeq++, 0, payload));
  }

  private void handshake(Peer peer) throws Exception {
    byte[] hello = RudpAuth.createHelloPayload(peer.id, peer.cid, relay.ed25519PubRaw());
    receive(peer, new RudpPacket(RudpPacket.Type.HELLO, peer.cid, 0, 0, hello));
    RudpPacket ack = takeFor(peer, RudpPacket.Type.HELLO_ACK);
    peer.transcript = RudpAuth.transcript(hello, ack.payload());
    receive(
        peer,
        RudpAuth.protect(
            peer.id,
            relay.ed25519PubRaw(),
            peer.transcript,
            new RudpPacket(RudpPacket.Type.HELLO_FINISH, peer.cid, 0, 0, new byte[0])));
    takeFor(peer, RudpPacket.Type.HELLO_READY);
  }

  private RudpPacket takeFor(Peer peer, RudpPacket.Type type) {
    RudpPacketEnvelope envelope = channel.readOutbound();
    assertNotNull(envelope, "missing " + type);
    assertEquals(type, envelope.packet().type());
    assertEquals(peer.address, envelope.recipient());
    return envelope.packet();
  }

  private void receive(Peer peer, RudpPacket packet) {
    virtualNowNs += VIRTUAL_NS_PER_PACKET;
    RudpPacketEnvelope envelope =
        new RudpPacketEnvelope(packet, peer.address, new InetSocketAddress("127.0.0.1", 6889));
    if (!timing) {
      manager.onPacket(envelope);
      return;
    }
    long cpu = threads.getCurrentThreadCpuTime();
    long alloc = threads.getCurrentThreadAllocatedBytes();
    manager.onPacket(envelope);
    cpuNs += threads.getCurrentThreadCpuTime() - cpu;
    allocBytes += threads.getCurrentThreadAllocatedBytes() - alloc;
    timedPackets++;
  }

  private static int envInt(String key, int def, int min, int max) {
    String raw = System.getenv(key);
    if (raw == null || raw.isBlank()) {
      return def;
    }
    try {
      return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
    } catch (NumberFormatException e) {
      return def;
    }
  }
}
