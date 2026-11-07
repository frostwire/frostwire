/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The version 1 karma manifest, as {@link KarmaChainPublisher} built it up to FrostWire 7.1.3 and
 * 3.2.3: the newest tail that fits in 950 bytes, starting at an epoch commitment, with hex and
 * base64 fields. Released clients still publish it, so readers must keep verifying it; tests use
 * this copy of the old builder to make one.
 */
final class KarmaManifestV1 {

  static final int MAX_MANIFEST_BYTES = 950;

  private KarmaManifestV1() {}

  /** The manifest of {@code chain}'s newest tail, or null if no EC + EN tail fits. */
  static Entry build(List<KarmaChainEntry> chain, byte[] ownerPub) {
    if (chain == null || chain.isEmpty()) {
      return null;
    }
    List<KarmaChainEntry> included = new ArrayList<>(chain);
    while (included.size() >= 2) {
      Entry entry = manifest(included, chain, ownerPub);
      if (entry.bencode().length <= MAX_MANIFEST_BYTES) {
        return entry;
      }
      included.remove(0);
      while (included.size() >= 2
          && included.get(0).kind() != KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
        included.remove(0);
      }
    }
    return null;
  }

  private static Entry manifest(
      List<KarmaChainEntry> tail, List<KarmaChainEntry> fullChain, byte[] ownerPub) {
    List<Entry> entries = new ArrayList<>(tail.size());
    for (KarmaChainEntry e : tail) {
      entries.add(entry(e));
    }
    Map<String, Object> map = new TreeMap<>();
    map.put("v", new Entry(1L));
    map.put("pub", new Entry(Base64.getUrlEncoder().withoutPadding().encodeToString(ownerPub)));
    map.put("len", new Entry((long) fullChain.size()));
    map.put(
        "head",
        new Entry(com.frostwire.util.Hex.encode(fullChain.get(fullChain.size() - 1).entryHash())));
    map.put("entries", Entry.fromList(entries));
    KarmaChainEntry first = tail.get(0);
    if (first.seq() != 0
        || !java.util.Arrays.equals(first.prevHash(), KarmaChainEntry.GENESIS_PREV_HASH)) {
      Map<String, Object> base = new TreeMap<>();
      base.put("seq", new Entry(first.seq()));
      base.put("ph", new Entry(com.frostwire.util.Hex.encode(first.prevHash())));
      map.put("base", Entry.fromMap(base));
    }
    map.put("ts", new Entry(1_790_000_000L));
    return Entry.fromMap(map);
  }

  private static Entry entry(KarmaChainEntry e) {
    Map<String, Object> m = new TreeMap<>();
    m.put("k", new Entry(e.kind().code()));
    m.put("seq", new Entry(e.seq()));
    m.put("bh", new Entry(e.blockHeight()));
    m.put("bkh", new Entry(com.frostwire.util.Hex.encode(e.blockHash())));
    m.put("ph", new Entry(com.frostwire.util.Hex.encode(e.prevHash())));
    if (e.epoch() != null) {
      m.put("ep", new Entry(e.epoch()));
    }
    if (e.energy() != null) {
      m.put("en", new Entry(String.format(java.util.Locale.ROOT, "%.3f", e.energy())));
    }
    // Not in 7.1.3's manifests (which never had format 2 commitments); readers accept it.
    if (e.formatVersion() != KarmaChainEntry.FORMAT_DECAY_PER_COMMITMENT) {
      m.put("fv", new Entry((long) e.formatVersion()));
    }
    if (e.peerPub() != null) {
      m.put("pp", new Entry(Base64.getUrlEncoder().withoutPadding().encodeToString(e.peerPub())));
    }
    if (e.infoHash() != null) {
      m.put("ih", new Entry(com.frostwire.util.Hex.encode(e.infoHash())));
    }
    if (e.scoreDelta() != null && e.scoreDelta() != 1) {
      m.put("sd", new Entry(e.scoreDelta().longValue()));
    }
    m.put("s", new Entry(Base64.getUrlEncoder().withoutPadding().encodeToString(e.signature())));
    return Entry.fromMap(m);
  }
}
