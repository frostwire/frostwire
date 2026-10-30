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
import java.util.Arrays;

/**
 * Feeds {@link RudpAuth} the handshake payloads and signed packets a remote peer sends before it is
 * trusted: an inbound HELLO ({@code verifyHello}, then {@code intendedFor} as {@code
 * RudpSessionManager.handleHello} calls it), the HELLO_ACK answering our own HELLO ({@code
 * verifyAck}) and every later packet ({@code unprotect}).
 *
 * <p>Signed inputs expire (a HELLO is only valid for 60 s around its timestamp, an ACK binds a
 * fresh HELLO), so a committed seed can't stay valid. Instead the first byte picks the operation
 * and whether the input is the fuzzer's raw bytes or a payload the harness signs right now with
 * real keys, after which the fuzzer flips bytes, truncates or extends it, or changes the connection
 * id, sequence, ACK or type it is checked against.
 *
 * <p>These methods document no exception: garbage is rejected with {@code false} or {@code null}. A
 * finding is anything thrown, or a forgery: an input accepted although it differs from what the
 * honest key signed.
 */
class RudpAuthFuzzTest {

  private static final IdentityKeys LOCAL = keys();
  private static final IdentityKeys PEER = keys();
  private static final byte[] LOCAL_PUB = LOCAL.ed25519PubRaw();
  private static final byte[] PEER_PUB = PEER.ed25519PubRaw();
  private static final RudpPacket.Type[] TYPES = RudpPacket.Type.values();

  @FuzzTest
  void verify(FuzzedDataProvider data) throws Exception {
    int op = data.consumeInt(0, 5);
    long cid = data.consumeLong();
    switch (op) {
      case 0 -> hello(cid, data.consumeRemainingAsBytes(), null);
      case 1 -> {
        // Our honest HELLO addressed to LOCAL, as PEER sent it, then mutated.
        byte[] signed =
            RudpAuth.createHelloPayload(PEER, cid, data.consumeBoolean() ? LOCAL_PUB : null);
        long checkedCid = data.consumeBoolean() ? cid : data.consumeLong();
        byte[] mutated = mutate(data, signed);
        hello(checkedCid, mutated, checkedCid == cid ? signed : null);
      }
      case 2 -> {
        byte[] hello =
            RudpAuth.createHelloPayload(LOCAL, cid, data.consumeBoolean() ? PEER_PUB : null);
        ack(cid, hello, data.consumeRemainingAsBytes(), null);
      }
      case 3 -> {
        byte[] hello =
            RudpAuth.createHelloPayload(LOCAL, cid, data.consumeBoolean() ? PEER_PUB : null);
        byte[] signed = RudpAuth.createAckPayload(PEER, cid, hello);
        long checkedCid = data.consumeBoolean() ? cid : data.consumeLong();
        ack(checkedCid, hello, mutate(data, signed), checkedCid == cid ? signed : null);
      }
      case 4 -> {
        byte[] transcript = data.consumeBytes(32);
        RudpPacket packet =
            new RudpPacket(
                TYPES[data.consumeInt(0, TYPES.length - 1)],
                cid,
                data.consumeInt(),
                data.consumeInt(),
                data.consumeRemainingAsBytes());
        unprotect(transcript, packet, null);
      }
      default -> {
        byte[] transcript = Arrays.copyOf(data.consumeBytes(32), 32);
        RudpPacket plain =
            new RudpPacket(
                TYPES[data.consumeInt(0, TYPES.length - 1)],
                cid,
                data.consumeInt(),
                data.consumeInt(),
                data.consumeBytes(data.consumeInt(0, RudpPacket.MAX_FRAGMENT_PAYLOAD)));
        RudpPacket signed = RudpAuth.protect(PEER, LOCAL_PUB, transcript, plain);
        RudpPacket received =
            new RudpPacket(
                data.consumeBoolean() ? signed.type() : TYPES[data.consumeInt(0, TYPES.length - 1)],
                data.consumeBoolean() ? cid : data.consumeLong(),
                data.consumeBoolean() ? signed.sequence() : data.consumeInt(),
                data.consumeBoolean() ? signed.ackThrough() : data.consumeInt(),
                mutate(data, signed.payload()));
        RudpPacket opened = unprotect(transcript, received, signed);
        if (opened != null && !opened.equals(plain)) {
          throw new IllegalStateException("unprotect returned a packet other than the one signed");
        }
      }
    }
  }

  /** As handleHello: verifyHello first, intendedFor only on a HELLO that verified. */
  private static void hello(long cid, byte[] payload, byte[] honest) {
    if (!RudpAuth.verifyHello(cid, payload)) {
      return;
    }
    if (honest == null || !Arrays.equals(honest, payload)) {
      throw new IllegalStateException("verifyHello accepted a HELLO no key signed for this cid");
    }
    RudpAuth.intendedFor(payload, LOCAL_PUB);
  }

  private static void ack(long cid, byte[] hello, byte[] ack, byte[] honest) {
    if (RudpAuth.verifyAck(cid, hello, ack) && (honest == null || !Arrays.equals(honest, ack))) {
      throw new IllegalStateException(
          "verifyAck accepted a HELLO_ACK no key signed for this HELLO");
    }
  }

  private static RudpPacket unprotect(byte[] transcript, RudpPacket received, RudpPacket honest) {
    RudpPacket opened = RudpAuth.unprotect(PEER_PUB, LOCAL_PUB, transcript, received);
    if (opened != null && (honest == null || !honest.equals(received))) {
      throw new IllegalStateException("unprotect accepted a packet no key signed");
    }
    return opened;
  }

  /** Flips bytes, then truncates or extends, as the fuzzer chooses. */
  private static byte[] mutate(FuzzedDataProvider data, byte[] signed) {
    byte[] bytes = signed.clone();
    int flips = data.consumeInt(0, 4);
    for (int i = 0; i < flips && bytes.length > 0; i++) {
      int at = data.consumeInt(0, bytes.length - 1);
      bytes[at] ^= data.consumeByte();
    }
    return switch (data.consumeInt(0, 2)) {
      case 1 -> Arrays.copyOf(bytes, data.consumeInt(0, bytes.length));
      case 2 -> {
        byte[] tail = data.consumeRemainingAsBytes();
        byte[] longer = Arrays.copyOf(bytes, bytes.length + tail.length);
        System.arraycopy(tail, 0, longer, bytes.length, tail.length);
        yield longer;
      }
      default -> bytes;
    };
  }

  private static IdentityKeys keys() {
    try {
      return IdentityKeys.generate(0);
    } catch (Exception e) {
      throw new IllegalStateException("cannot generate test identity", e);
    }
  }
}
