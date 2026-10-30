/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Fuzzes {@code LanPeerBeacon.decode(byte[], int)}, which reads the LAN discovery datagrams that
 * arrive on UDP port 6890 (multicast group 239.255.70.88 or unicast). Anyone on the local network
 * can send one, with any content; the receive loop decodes the datagram before it checks the
 * sender's address.
 *
 * <p>The decoder is private, so the harness calls it through a method handle, exactly as the
 * receive loop does: the received bytes and their length. It documents "the advertised rUDP port,
 * or -1 for anything that is not a well-formed beacon". Any exception, a result outside {@code -1}
 * and {@code 1..65535}, or an input that takes seconds is a bug.
 */
class LanPeerBeaconFuzzTest {

  private static final MethodHandle DECODE = decodeHandle();

  @FuzzTest
  void decode(FuzzedDataProvider data) throws Throwable {
    byte[] datagram = data.consumeRemainingAsBytes();
    int port = (int) DECODE.invokeExact(datagram, datagram.length);
    if (port != -1 && (port < 1 || port > 65535)) {
      throw new IllegalStateException("decode returned " + port + ", not -1 or a port");
    }
  }

  private static MethodHandle decodeHandle() {
    try {
      var method = LanPeerBeacon.class.getDeclaredMethod("decode", byte[].class, int.class);
      method.setAccessible(true);
      MethodHandle handle = MethodHandles.lookup().unreflect(method);
      return handle.asType(MethodType.methodType(int.class, byte[].class, int.class));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("LanPeerBeacon.decode(byte[], int) not found", e);
    }
  }
}
