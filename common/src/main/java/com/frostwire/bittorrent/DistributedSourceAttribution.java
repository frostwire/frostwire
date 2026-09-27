/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import java.util.Base64;
import java.util.Map;

/** Encodes verified distributed-search source identity in torrent resume metadata. */
public final class DistributedSourceAttribution {
  public static final String EXTRA_KEY = "distributed_source_peer_pub";

  private DistributedSourceAttribution() {}

  /** Returns null for absent or malformed source metadata. */
  public static byte[] get(Map<String, String> extra) {
    if (extra == null) {
      return null;
    }
    String encoded;
    synchronized (extra) {
      encoded = extra.get(EXTRA_KEY);
    }
    if (encoded == null || encoded.isEmpty()) {
      return null;
    }
    try {
      byte[] pub = Base64.getUrlDecoder().decode(encoded);
      return pub.length == 32 ? pub : null;
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  /** Records a source once; later downloads of the same transfer cannot replace provenance. */
  public static boolean putIfAbsent(Map<String, String> extra, byte[] sourcePeerPub) {
    if (extra == null || sourcePeerPub == null || sourcePeerPub.length != 32) {
      return false;
    }
    synchronized (extra) {
      byte[] current = get(extra);
      if (current != null) {
        return java.util.Arrays.equals(current, sourcePeerPub);
      }
      extra.put(
          EXTRA_KEY,
          Base64.getUrlEncoder().withoutPadding().encodeToString(sourcePeerPub));
      return true;
    }
  }
}
