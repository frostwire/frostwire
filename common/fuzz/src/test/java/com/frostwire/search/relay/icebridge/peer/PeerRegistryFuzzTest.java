/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.peer;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.relay.icebridge.IceBridgeConfig;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Drives one {@link PeerRegistry} with a sequence of what peers can make an IceBridge servent do to
 * it: register a {@link PeerRecord} (the endpoint, role, version and freshness a peer claims for
 * itself through {@code /register} or {@code /route}), have an endpoint learned from an
 * authenticated HELLO, refresh liveness on inbound traffic, and the lookups and eviction the
 * servent runs on top of that.
 *
 * <p>Who sends it: the record's fields come from peers (the {@code RegisterRequest} body, the
 * observed source address of a datagram). The harness only builds records the {@link PeerRecord}
 * constructor accepts (a 32-byte key, a non-blank host, a port in 1..65535), as the control API
 * does before it calls {@code register}; everything else (host length, version length, ports on the
 * observed path, key lengths, lookup sizes, TTLs, timestamps) is the fuzzer's. Six fixed keys make
 * refreshes and conflicts common.
 *
 * <p>No method documents an exception. A finding is anything thrown, or a broken bound after any
 * step: more peers than {@code maxPeers}, a stored record with a blank or over-253-character host,
 * a port outside 1..65535, a version over 64 characters or another key than it is stored under; an
 * accepted registration that is not stored, or that a fresher-or-equal record did not replace; a
 * lookup returning more records than asked for, the same peer twice, or a non-forwarder where only
 * forwarders were asked for.
 */
class PeerRegistryFuzzTest {

  private static final byte[][] KEYS = new byte[6][32];
  private static final IceBridgeConfig.Role[] ROLES = IceBridgeConfig.Role.values();

  static {
    for (int i = 0; i < KEYS.length; i++) {
      Arrays.fill(KEYS[i], (byte) (i + 1));
    }
  }

  @FuzzTest
  void operations(FuzzedDataProvider data) {
    int maxPeers = data.consumeInt(1, 8);
    // A low per-key rate makes the limiter reject refreshes; a high one never does.
    double qps = data.consumeBoolean() ? 1.0 : 1000.0;
    IceBridgeConfig config =
        new IceBridgeConfig(
            "127.0.0.1", 0, 1, IceBridgeConfig.Role.BOTH, null, maxPeers, 3600, qps, false);
    PeerRegistry registry = new PeerRegistry(config);
    long now = System.currentTimeMillis();

    while (data.remainingBytes() > 0) {
      switch (data.consumeInt(0, 6)) {
        case 0 -> {
          byte[] pub = KEYS[data.consumeInt(0, KEYS.length - 1)];
          String host = data.consumeString(300);
          if (host.isBlank()) {
            host = "192.0.2.1";
          }
          int port = data.consumeInt(1, 65535);
          IceBridgeConfig.Role role = ROLES[data.consumeInt(0, ROLES.length - 1)];
          long lastSeen = now + data.consumeLong(-7_200_000L, 7_200_000L);
          String version = data.consumeBoolean() ? null : data.consumeString(100);
          PeerRecord before = registry.lookup(pub);
          long registrations = registry.registrations();
          PeerRecord record = new PeerRecord(pub, host, port, role, lastSeen, version);
          if (registry.register(record)) {
            PeerRecord stored = registry.lookup(pub);
            if (stored == null) {
              throw new IllegalStateException("accepted registration was not stored");
            }
            if (registry.registrations() != registrations + 1) {
              throw new IllegalStateException("accepted registration was not counted");
            }
            if ((before == null || lastSeen >= before.lastSeenMs())
                && (!stored.host().equals(host)
                    || stored.rudpPort() != port
                    || stored.role() != role
                    || stored.lastSeenMs() != lastSeen)) {
              throw new IllegalStateException("fresher registration did not replace the record");
            }
          }
        }
        case 1 ->
            registry.learnObservedEndpoint(
                pubOrJunk(data), data.consumeString(300), data.consumeInt(-1, 70_000));
        case 2 -> registry.touch(pubOrJunk(data));
        case 3 -> {
          int max = data.consumeInt(-1, 10);
          List<PeerRecord> found = registry.lookupForwarders(max, new Random(data.consumeLong()));
          checkLookup(found, max, true, registry);
        }
        case 4 -> {
          int max = data.consumeInt(-1, 10);
          boolean forwardersOnly = data.consumeBoolean();
          List<PeerRecord> found =
              registry.lookupPeers(max, forwardersOnly, new Random(data.consumeLong()));
          checkLookup(found, max, forwardersOnly, registry);
        }
        case 5 -> registry.lookup(pubOrJunk(data));
        default -> registry.evictStale(data.consumeLong(-1, 7_200_000L));
      }
      checkRegistry(registry, maxPeers);
    }
  }

  private static byte[] pubOrJunk(FuzzedDataProvider data) {
    int pick = data.consumeInt(0, KEYS.length + 1);
    if (pick < KEYS.length) {
      return KEYS[pick];
    }
    return pick == KEYS.length ? null : data.consumeBytes(40);
  }

  private static void checkLookup(
      List<PeerRecord> found, int max, boolean forwardersOnly, PeerRegistry registry) {
    if (found.size() > Math.max(0, max) || found.size() > registry.size()) {
      throw new IllegalStateException(found.size() + " records for a lookup of " + max);
    }
    Set<String> seen = new HashSet<>();
    for (PeerRecord r : found) {
      if (!seen.add(r.ed25519PubHex())) {
        throw new IllegalStateException("lookup returned " + r.ed25519PubHex() + " twice");
      }
      if (forwardersOnly && !r.canForward()) {
        throw new IllegalStateException("forwarder lookup returned a " + r.role());
      }
    }
  }

  private static void checkRegistry(PeerRegistry registry, int maxPeers) {
    if (registry.size() > maxPeers) {
      throw new IllegalStateException(registry.size() + " peers over maxPeers " + maxPeers);
    }
    for (byte[] pub : KEYS) {
      PeerRecord r = registry.lookup(pub);
      if (r == null) {
        continue;
      }
      if (!Arrays.equals(r.ed25519Pub(), pub)) {
        throw new IllegalStateException("record stored under another key");
      }
      if (r.host() == null || r.host().isBlank() || r.host().length() > 253) {
        throw new IllegalStateException("stored host of length " + r.host().length());
      }
      if (r.rudpPort() < 1 || r.rudpPort() > 65535) {
        throw new IllegalStateException("stored port " + r.rudpPort());
      }
      if (r.icebridgeVersion().length() > 64) {
        throw new IllegalStateException(
            "stored version of length " + r.icebridgeVersion().length());
      }
    }
  }
}
