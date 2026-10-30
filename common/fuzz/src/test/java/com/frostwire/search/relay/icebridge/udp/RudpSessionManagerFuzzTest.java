/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import com.frostwire.search.relay.icebridge.IceBridgeMetrics;
import com.frostwire.search.relay.icebridge.IceBridgeTopology;
import com.frostwire.search.relay.icebridge.peer.PeerRecord;
import com.frostwire.search.relay.icebridge.peer.PeerRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Drives {@link RudpSessionManager} the way remote IceBridge peers can: honest peers (real keys,
 * correctly signed handshakes and packets, so the fuzzer gets past the signatures) and the rest of
 * the internet (unsigned, replayed or misaddressed datagrams), in any order, with any connection
 * ids, sequence numbers, ACKs, packet types and payloads, interleaved with local sends, probes,
 * registry updates, a listener that refuses delivery, and retransmit/evict ticks.
 *
 * <p>The manager runs over an {@link EmbeddedChannel} (no socket); its retransmit thread is stopped
 * and its tick is called from the harness so every run is single-threaded. Time is the real clock,
 * so a run never reaches the 15 s retransmit and 120 s idle timeouts.
 *
 * <p>{@code onPacket} and the local API document no exception. A finding is anything escaping them,
 * or a broken bound after any step: more sessions than {@code setMaxSessions} allows, a session
 * over its 512-packet or 1 MiB retention cap, the manager over its 16 384-packet or 16 MiB total,
 * more than 64 reassembly groups, the CID and address maps disagreeing about who owns a session, an
 * authenticated session without a peer key or transcript, or a delivered payload over the 256 KiB
 * assembly limit.
 */
class RudpSessionManagerFuzzTest {

  private static final IdentityKeys LOCAL = keys();
  private static final byte[] LOCAL_PUB = LOCAL.ed25519PubRaw();

  /** Remote identities; index {@code PEERS.length} means "our own key", sent back at us. */
  private static final IdentityKeys[] PEERS = {keys(), keys(), keys()};

  private static final InetSocketAddress LOCAL_ADDRESS = new InetSocketAddress("10.9.9.9", 4000);
  private static final InetSocketAddress[] ADDRESSES = {
    new InetSocketAddress("10.0.0.1", 4000),
    new InetSocketAddress("10.0.0.1", 4001),
    new InetSocketAddress("10.0.0.2", 4000),
    new InetSocketAddress("10.0.1.5", 5000),
    new InetSocketAddress("fd00::1", 4000),
    new InetSocketAddress("127.0.0.1", 62001),
  };
  private static final long[] CIDS = {1, 2, 424242, -2, Long.MAX_VALUE};
  private static final RudpPacket.Type[] TYPES = RudpPacket.Type.values();

  // Bounds RudpSessionManager and FragmentReassembler define privately.
  private static final int MAX_GLOBAL_SESSIONS = 4096;
  private static final int MAX_GLOBAL_PENDING_PACKETS = 16_384;
  private static final long MAX_GLOBAL_PENDING_BYTES = 16L * 1024 * 1024;
  private static final int MAX_REASSEMBLY_GROUPS = 64;

  private static final Field SESSIONS_BY_ID = field("sessionsByRemoteId");
  private static final Field SESSIONS_BY_ADDRESS = field("sessionsByAddress");
  private static final Field REASSEMBLER = field("reassembler");
  /**
   * How far each retransmit/evict tick moves the manager's clock, in turn: past the path-challenge
   * window (5 s), the retransmit and half-open timeout (15 s), fragment-group expiry (30 s) and
   * idle eviction (120 s). The tick operation consumes no input, so seeds keep their meaning.
   */
  private static final long[] TICK_SECONDS = {1, 6, 16, 31, 121};

  /** What one remote peer knows about an association it completed with us. */
  private static final class Association {
    final int key;
    final long cid;
    final byte[] transcript;
    InetSocketAddress address;
    int nextSequence = 1;
    int nextFragment;
    int highestFromLocal;
    byte[] challenge;
    InetSocketAddress challengedAt;

    Association(int key, long cid, byte[] transcript, InetSocketAddress address) {
      this.key = key;
      this.cid = cid;
      this.transcript = transcript;
      this.address = address;
    }
  }

  /** A HELLO, ours or a peer's, waiting for its HELLO_ACK. */
  private record Hello(int key, long cid, byte[] payload, InetSocketAddress peerAddress) {}

  @FuzzTest
  void onPacket(FuzzedDataProvider data) throws Exception {
    // Jazzer calls this method many times on one instance: all state lives in a fresh Run.
    new Run().run(data);
  }

  /** One fuzz input: a fresh manager, channel, registry and model of what the peers know. */
  private static final class Run {
    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final PeerRegistry registry =
        new PeerRegistry(IceBridgeConfig.newBuilder().controlHttpPort(8797).build());
    private final Map<Long, Association> associations = new LinkedHashMap<>();
    private final Map<Long, Hello> peerHellos = new LinkedHashMap<>();
    private final List<Hello> localHellos = new ArrayList<>();
    private final List<RudpPacketEnvelope> sent = new ArrayList<>();
    private boolean listenerFull;
    private int maxSessions;
    private RudpSessionManager manager;
    private long nanos = TimeUnit.HOURS.toNanos(1);
    private int ticks;

    void run(FuzzedDataProvider data) throws Exception {
      // The harness owns time: no scheduler thread, and the clock moves only in tick() (#1397).
      manager =
          new RudpSessionManager(
              LOCAL,
              registry,
              new IceBridgeMetrics(),
              (source, payload) -> {
                if (listenerFull) {
                  throw new IllegalStateException("listener queue full");
                }
                if (payload.length > FragmentReassembler.MAX_ASSEMBLED_SIZE) {
                  throw new AssertionError("delivered " + payload.length + " bytes");
                }
              },
              () -> nanos,
              false);
      manager.setChannel(channel);
      maxSessions = data.consumeInt(1, 6);
      manager.setMaxSessions(maxSessions);
      try {
        int steps = data.consumeInt(1, 64);
        for (int step = 0; step < steps && data.remainingBytes() > 0; step++) {
          step(data);
          drain();
          checkBounds();
        }
      } finally {
        manager.shutdown();
        channel.finishAndReleaseAll();
      }
    }

    private void step(FuzzedDataProvider data) throws Exception {
      switch (data.consumeInt(0, 11)) {
        case 0 -> peerHello(data);
        case 1 -> localConnect(data);
        case 2 -> peerAck(data);
        case 3 -> peerFinish(data);
        case 4 -> peerPacket(data);
        case 5 -> receive(rawPacket(data), address(data));
        case 6 -> localSend(data);
        case 7 -> tick();
        case 8 -> replay(data);
        case 9 -> peerAckAll(data);
        case 10 -> register(data);
        default -> listenerFull = !listenerFull;
      }
    }

    private void peerHello(FuzzedDataProvider data) throws Exception {
      int key = data.consumeInt(0, PEERS.length);
      long cid = cid(data);
      byte[] intended =
          switch (data.consumeInt(0, 2)) {
            case 0 -> LOCAL_PUB;
            case 1 -> null;
            default -> PEERS[(key + 1) % PEERS.length].ed25519PubRaw();
          };
      byte[] hello = RudpAuth.createHelloPayload(identity(key), cid, intended);
      InetSocketAddress from = address(data);
      peerHellos.put(cid, new Hello(key, cid, hello, from));
      receive(new RudpPacket(RudpPacket.Type.HELLO, cid, 0, 0, hello), from);
    }

    private void localConnect(FuzzedDataProvider data) {
      InetSocketAddress to = address(data);
      switch (data.consumeInt(0, 2)) {
        case 0 -> manager.connect(to);
        case 1 -> manager.probe(to.getAddress().getHostAddress(), to.getPort(), false);
        default -> manager.probe(to.getAddress().getHostAddress(), to.getPort(), true);
      }
    }

    private void peerAck(FuzzedDataProvider data) throws Exception {
      if (localHellos.isEmpty()) {
        return;
      }
      Hello hello = localHellos.get(data.consumeInt(0, localHellos.size() - 1));
      int key = data.consumeInt(0, PEERS.length);
      byte[] ack = RudpAuth.createAckPayload(identity(key), hello.cid(), hello.payload());
      InetSocketAddress from = data.consumeBoolean() ? hello.peerAddress() : address(data);
      associations.put(
          hello.cid(),
          new Association(key, hello.cid(), RudpAuth.transcript(hello.payload(), ack), from));
      receive(new RudpPacket(RudpPacket.Type.HELLO_ACK, hello.cid(), 0, 0, ack), from);
    }

    private void peerFinish(FuzzedDataProvider data) throws Exception {
      Association a = association(data);
      if (a == null) {
        return;
      }
      RudpPacket.Type type =
          data.consumeBoolean() ? RudpPacket.Type.HELLO_FINISH : RudpPacket.Type.HELLO_READY;
      receive(signed(a, new RudpPacket(type, a.cid, 0, 0, new byte[0])), from(data, a));
    }

    private void peerPacket(FuzzedDataProvider data) throws Exception {
      Association a = association(data);
      if (a == null) {
        return;
      }
      RudpPacket.Type type = TYPES[data.consumeInt(0, TYPES.length - 1)];
      int sequence =
          switch (data.consumeInt(0, 3)) {
            case 0 -> a.nextSequence++;
            case 1 -> a.nextSequence - 1;
            case 2 -> 0;
            default -> data.consumeInt();
          };
      int ack =
          switch (data.consumeInt(0, 2)) {
            case 0 -> a.highestFromLocal;
            case 1 -> 0;
            default -> data.consumeInt();
          };
      byte[] payload;
      switch (data.consumeInt(0, 4)) {
        case 0 -> {
          // A fragment of a group: header (group, index, total) then the fragment's bytes.
          type = data.consumeBoolean() ? RudpPacket.Type.DATA_END : RudpPacket.Type.DATA_FRAG;
          int group = data.consumeInt(0, 3);
          int index = data.consumeBoolean() ? a.nextFragment++ : data.consumeInt(-1, 300);
          int total = data.consumeInt(-1, 300);
          byte[] body = data.consumeBytes(data.consumeInt(0, RudpPacket.MAX_FRAGMENT_PAYLOAD));
          payload =
              java.nio.ByteBuffer.allocate(12 + body.length)
                  .putInt(group)
                  .putInt(index)
                  .putInt(total)
                  .put(body)
                  .array();
        }
        case 1 -> {
          type = RudpPacket.Type.RELAY;
          byte[] source = data.consumeBoolean() ? identity(a.key).ed25519PubRaw() : LOCAL_PUB;
          byte[] target =
              switch (data.consumeInt(0, 2)) {
                case 0 -> LOCAL_PUB;
                case 1 -> PEERS[data.consumeInt(0, PEERS.length - 1)].ed25519PubRaw();
                default -> java.util.Arrays.copyOf(data.consumeBytes(32), 32);
              };
          int ttl = data.consumeInt(0, IceBridgeTopology.MAX_MESH_HOP_TTL + 1);
          byte[] app = data.consumeBytes(data.consumeInt(0, 1000));
          try {
            payload = RelayFrame.encode(source, target, ttl, app);
          } catch (IllegalArgumentException documented) {
            return; // RelayFrame.encode rejects an empty or oversized payload or a bad TTL.
          }
        }
        case 2 -> {
          type = RudpPacket.Type.PATH_RESPONSE;
          sequence = 0;
          ack = 0;
          payload = a.challenge == null ? new byte[32] : a.challenge;
        }
        default -> payload = data.consumeBytes(data.consumeInt(0, RudpPacket.MAX_WIRE_PAYLOAD));
      }
      InetSocketAddress from =
          type == RudpPacket.Type.PATH_RESPONSE && a.challengedAt != null && data.consumeBoolean()
              ? a.challengedAt
              : from(data, a);
      receive(signed(a, new RudpPacket(type, a.cid, sequence, ack, payload)), from);
    }

    private RudpPacket rawPacket(FuzzedDataProvider data) {
      return new RudpPacket(
          TYPES[data.consumeInt(0, TYPES.length - 1)],
          cid(data),
          data.consumeInt(),
          data.consumeInt(),
          data.consumeBytes(data.consumeInt(0, RudpPacket.MAX_WIRE_PAYLOAD + 1)));
    }

    private void localSend(FuzzedDataProvider data) {
      int variant = data.consumeInt(0, 2);
      InetSocketAddress to = address(data);
      byte[] target = identity(data.consumeInt(0, PEERS.length)).ed25519PubRaw();
      int ttl = data.consumeInt(-1, IceBridgeTopology.MAX_MESH_HOP_TTL + 1);
      // Payloads above one fragment exercise fragmentation; content is irrelevant, size is not.
      byte[] payload = new byte[data.consumeInt(0, 3 * RudpPacket.MAX_FRAGMENT_PAYLOAD + 1)];
      switch (variant) {
        case 0 -> manager.sendData(to, payload);
        case 1 -> manager.deliver(target, payload);
        default -> manager.sendRelay(to, target, payload, ttl);
      }
    }

    private void replay(FuzzedDataProvider data) {
      if (sent.isEmpty()) {
        return;
      }
      RudpPacketEnvelope old = sent.get(data.consumeInt(0, sent.size() - 1));
      receive(old.packet(), data.consumeBoolean() ? old.sender() : address(data));
    }

    private void peerAckAll(FuzzedDataProvider data) throws Exception {
      Association a = association(data);
      if (a == null) {
        return;
      }
      receive(
          signed(a, new RudpPacket(RudpPacket.Type.DATA_ACK, a.cid, 0, a.highestFromLocal, null)),
          from(data, a));
    }

    private void register(FuzzedDataProvider data) {
      InetSocketAddress at = address(data);
      IceBridgeConfig.Role[] roles = IceBridgeConfig.Role.values();
      registry.register(
          new PeerRecord(
              identity(data.consumeInt(0, PEERS.length)).ed25519PubRaw(),
              at.getAddress().getHostAddress(),
              at.getPort(),
              roles[data.consumeInt(0, roles.length - 1)],
              System.currentTimeMillis()));
    }

    private void tick() {
      nanos += TimeUnit.SECONDS.toNanos(TICK_SECONDS[ticks++ % TICK_SECONDS.length]);
      manager.tick();
    }

    private void receive(RudpPacket packet, InetSocketAddress from) {
      RudpPacketEnvelope envelope = new RudpPacketEnvelope(packet, from, LOCAL_ADDRESS);
      if (sent.size() < 64) {
        sent.add(envelope);
      }
      manager.onPacket(envelope);
    }

    /**
     * Reads what the manager wrote, as the peers would: learns CIDs, transcripts and challenges.
     */
    private void drain() {
      RudpPacketEnvelope out;
      while ((out = channel.readOutbound()) != null) {
        RudpPacket packet = out.packet();
        long cid = packet.connectionId();
        if (packet.type() == RudpPacket.Type.HELLO) {
          if (localHellos.size() < 32) {
            localHellos.add(new Hello(-1, cid, packet.payload(), out.recipient()));
          }
          continue;
        }
        if (packet.type() == RudpPacket.Type.HELLO_ACK) {
          Hello hello = peerHellos.get(cid);
          if (hello != null && RudpAuth.verifyAck(cid, hello.payload(), packet.payload())) {
            associations.put(
                cid,
                new Association(
                    hello.key(),
                    cid,
                    RudpAuth.transcript(hello.payload(), packet.payload()),
                    out.recipient()));
          }
          continue;
        }
        Association a = associations.get(cid);
        if (a == null) {
          continue;
        }
        RudpPacket plain =
            RudpAuth.unprotect(LOCAL_PUB, identity(a.key).ed25519PubRaw(), a.transcript, packet);
        if (plain == null) {
          continue;
        }
        if (plain.type() == RudpPacket.Type.PATH_CHALLENGE) {
          a.challenge = plain.payload();
          a.challengedAt = out.recipient();
        } else if (plain.sequence() != 0
            && Integer.compareUnsigned(plain.sequence(), a.highestFromLocal) > 0) {
          a.highestFromLocal = plain.sequence();
        }
      }
    }

    @SuppressWarnings("unchecked")
    private void checkBounds() throws IllegalAccessException {
      Map<Long, RudpSession> byId = (Map<Long, RudpSession>) SESSIONS_BY_ID.get(manager);
      Map<InetSocketAddress, RudpSession> byAddress =
          (Map<InetSocketAddress, RudpSession>) SESSIONS_BY_ADDRESS.get(manager);
      if (byId.size() > Math.min(maxSessions, MAX_GLOBAL_SESSIONS)) {
        throw new IllegalStateException(byId.size() + " sessions, cap " + maxSessions);
      }
      long retained = 0;
      int packets = 0;
      for (Map.Entry<Long, RudpSession> entry : byId.entrySet()) {
        RudpSession session = entry.getValue();
        if (entry.getKey() != session.remoteConnectionId()) {
          throw new IllegalStateException("session filed under another CID");
        }
        if (session.pending().size() > RudpSession.MAX_PENDING_PACKETS
            || session.retainedBytes() > RudpSession.MAX_RETAINED_BYTES) {
          throw new IllegalStateException("session over its pending cap");
        }
        if (session.isAuthenticated()
            && (session.remotePub() == null || session.transcript == null)) {
          throw new IllegalStateException("authenticated session without key or transcript");
        }
        retained += session.retainedBytes();
        packets += session.pending().size();
      }
      if (retained > MAX_GLOBAL_PENDING_BYTES || packets > MAX_GLOBAL_PENDING_PACKETS) {
        throw new IllegalStateException("manager over its global pending cap");
      }
      for (Map.Entry<InetSocketAddress, RudpSession> entry : byAddress.entrySet()) {
        RudpSession session = entry.getValue();
        if (byId.get(session.remoteConnectionId()) != session
            || !entry.getKey().equals(session.remoteAddress())) {
          throw new IllegalStateException("address map disagrees with the CID map");
        }
      }
      if (((FragmentReassembler) REASSEMBLER.get(manager)).pendingGroupCount()
          > MAX_REASSEMBLY_GROUPS) {
        throw new IllegalStateException(
            "more than " + MAX_REASSEMBLY_GROUPS + " reassembly groups");
      }
    }

    private RudpPacket signed(Association a, RudpPacket packet) throws Exception {
      return RudpAuth.protect(identity(a.key), LOCAL_PUB, a.transcript, packet);
    }

    private Association association(FuzzedDataProvider data) {
      if (associations.isEmpty()) {
        return null;
      }
      int index = data.consumeInt(0, associations.size() - 1);
      return new ArrayList<>(associations.values()).get(index);
    }

    private InetSocketAddress from(FuzzedDataProvider data, Association a) {
      return data.consumeBoolean() ? a.address : address(data);
    }

    private long cid(FuzzedDataProvider data) {
      return switch (data.consumeInt(0, 2)) {
        case 0 -> CIDS[data.consumeInt(0, CIDS.length - 1)];
        case 1 -> {
          List<Long> known = new ArrayList<>(associations.keySet());
          for (Hello hello : localHellos) {
            known.add(hello.cid());
          }
          yield known.isEmpty() ? CIDS[0] : known.get(data.consumeInt(0, known.size() - 1));
        }
        default -> data.consumeLong();
      };
    }
  }

  private static InetSocketAddress address(FuzzedDataProvider data) {
    return ADDRESSES[data.consumeInt(0, ADDRESSES.length - 1)];
  }

  private static IdentityKeys identity(int key) {
    return key >= 0 && key < PEERS.length ? PEERS[key] : LOCAL;
  }

  private static IdentityKeys keys() {
    try {
      return IdentityKeys.generate(0);
    } catch (Exception e) {
      throw new IllegalStateException("cannot generate test identity", e);
    }
  }

  private static Field field(String name) {
    try {
      Field field = RudpSessionManager.class.getDeclaredField(name);
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }
}
