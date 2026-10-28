/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.udp;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Feeds {@link FragmentReassembler} the fragment sequences a remote peer could send: any keys,
 * indexes, totals and payloads, in any order, with releases in between. Its promises, checked after
 * every step: it never throws, never holds more than 64 pending groups, and a COMPLETE result is
 * exactly the group's accepted fragments in index order.
 */
class FragmentReassemblerFuzzTest {

  private static final int MAX_PENDING_GROUPS = 64;

  @FuzzTest
  void accept(FuzzedDataProvider data) {
    FragmentReassembler reassembler = new FragmentReassembler();
    // What each group should assemble to: its accepted fragments, by index.
    Map<String, Map<Integer, byte[]>> accepted = new HashMap<>();
    int steps = data.consumeInt(1, 400);
    for (int step = 0; step < steps && data.remainingBytes() > 0; step++) {
      String key = "s" + data.consumeInt(0, 80);
      if (data.consumeInt(0, 15) == 0) {
        reassembler.release(key);
        accepted.remove(key);
        continue;
      }
      int total = data.consumeInt(-1, FragmentReassembler.MAX_FRAGMENTS_PER_GROUP + 1);
      int index = data.consumeInt(-1, Math.max(0, total));
      boolean last = data.consumeBoolean();
      byte[] payload = data.consumeBytes(data.consumeInt(0, RudpPacket.MAX_FRAGMENT_PAYLOAD + 1));
      FragmentReassembler.Result result = reassembler.accept(key, index, total, last, payload);
      if (result.state != FragmentReassembler.State.REJECTED) {
        accepted.computeIfAbsent(key, k -> new HashMap<>()).putIfAbsent(index, payload);
      }
      if (result.state == FragmentReassembler.State.COMPLETE) {
        byte[] expected = joined(accepted.get(key), total);
        if (!java.util.Arrays.equals(expected, result.payload)) {
          throw new IllegalStateException("assembled payload differs from its fragments, key " + key);
        }
      }
      if (reassembler.pendingGroupCount() > MAX_PENDING_GROUPS) {
        throw new IllegalStateException("more than " + MAX_PENDING_GROUPS + " pending groups");
      }
    }
  }

  private static byte[] joined(Map<Integer, byte[]> fragments, int total) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 0; i < total; i++) {
      byte[] fragment = fragments.get(i);
      if (fragment == null) {
        throw new IllegalStateException("COMPLETE with fragment " + i + " missing");
      }
      out.write(fragment, 0, fragment.length);
    }
    return out.toByteArray();
  }
}
