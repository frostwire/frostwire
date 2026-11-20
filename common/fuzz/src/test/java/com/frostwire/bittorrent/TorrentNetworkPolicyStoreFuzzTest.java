/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.Map;

/**
 * Fuzzes {@link TorrentNetworkPolicyStore#parse} and {@link TorrentNetworkPolicyStore#serialize},
 * the reader and writer of the per-torrent network policy file (frostwire/frostwire#1304).
 *
 * <p>Who sends it: nobody remote. The file sits in the user's FrostWire home and is written by this
 * app, but it can be truncated by a crash or a full disk, hand-edited, or written by a newer build
 * with modes this one does not know, and a failure to read it must never keep the engine from
 * starting.
 *
 * <p>The parser documents no exception and promises: never throws, only valid hashes mapped to
 * non-default modes, at most {@code MAX_ENTRIES} entries. A finding is anything thrown, an input
 * running longer than the timeout, or a result that breaks that promise or does not survive {@code
 * serialize} followed by {@code parse}.
 */
class TorrentNetworkPolicyStoreFuzzTest {

  @FuzzTest
  void parse(FuzzedDataProvider data) {
    String text = data.consumeRemainingAsString();
    Map<String, TorrentNetworkMode> parsed = TorrentNetworkPolicyStore.parse(text);
    if (parsed == null || parsed.size() > TorrentNetworkPolicyStore.MAX_ENTRIES) {
      throw new IllegalStateException("bad result size");
    }
    for (Map.Entry<String, TorrentNetworkMode> e : parsed.entrySet()) {
      String key = e.getKey();
      if (TorrentNetworkPolicyStore.normalizeKey(key) == null || !key.equals(key.toLowerCase())) {
        throw new IllegalStateException("invalid or non-canonical key " + key);
      }
      if (e.getValue() == null || e.getValue() == TorrentNetworkMode.DEFAULT) {
        throw new IllegalStateException("null or default mode for " + key);
      }
    }
    Map<String, TorrentNetworkMode> again =
        TorrentNetworkPolicyStore.parse(TorrentNetworkPolicyStore.serialize(parsed));
    if (!parsed.equals(again)) {
      throw new IllegalStateException("serialize/parse does not round trip");
    }
  }
}
