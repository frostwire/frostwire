/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import java.util.Locale;

/**
 * Which networks one torrent may use to find and talk to peers (frostwire/frostwire#1304).
 *
 * <p>{@link #MIXED} is the behavior every torrent had before this choice existed, so it is the
 * default for torrents without a stored choice and for any stored value this build does not
 * understand (a newer build may write modes an older one has never heard of; the older one must
 * fall back to the default, never fail).
 *
 * <p>This is the policy only. Nothing enforces it yet; see {@code
 * I2P_PEX_AND_NETWORK_SELECTION_DESIGN.md} for the enforcement points. Immutable and safe to share
 * between threads.
 */
public enum TorrentNetworkMode {
  /** Clearnet and I2P peers, trackers and discovery, as before. The default. */
  MIXED,
  /** I2P peers and I2P trackers only. Must fail closed: no clearnet peers, trackers, DHT, LSD. */
  I2P_ONLY,
  /** Clearnet peers only; I2P peers are never added for this torrent. */
  CLEARNET_ONLY;

  /** The mode of a torrent that has no stored choice. */
  public static final TorrentNetworkMode DEFAULT = MIXED;

  /** True when this mode lets the torrent talk to clearnet (IP) peers. */
  public boolean allowsClearnet() {
    return this != I2P_ONLY;
  }

  /** True when this mode lets the torrent talk to I2P peers. */
  public boolean allowsI2p() {
    return this != CLEARNET_ONLY;
  }

  /** The name written to disk. Stable: never rename a constant, add a new one instead. */
  public String persistedName() {
    return name();
  }

  /**
   * Parses a stored value. Case and surrounding whitespace are ignored. Never throws: null, empty,
   * or an unknown name is {@link #DEFAULT}.
   */
  public static TorrentNetworkMode fromPersisted(CharSequence value) {
    if (value == null) {
      return DEFAULT;
    }
    // Longer than any constant plus padding: cannot match, skip the copy.
    if (value.length() > 64) {
      return DEFAULT;
    }
    String name = value.toString().trim().toUpperCase(Locale.ROOT);
    for (TorrentNetworkMode mode : values()) {
      if (mode.name().equals(name)) {
        return mode;
      }
    }
    return DEFAULT;
  }
}
