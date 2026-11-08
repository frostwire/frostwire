/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.engines;

import com.frostwire.search.frostclick.UserAgent;

/**
 * What a client supplies to the shared search engine definitions (frostwire/frostwire#1002): where
 * it keeps each engine's on/off setting, which engines it offers, and the few values engines need
 * from the client. Desktop and Android each implement it once; {@link SearchEngines} does the rest.
 */
public interface SearchEngineAdapter {
  /** The user's setting for the engine; each client maps ids to the keys it already stores. */
  boolean isEnabled(SearchEngineID id);

  void setEnabled(SearchEngineID id, boolean enabled);

  /**
   * Whether this client offers the engine at all, e.g. the Local test engine only in debug builds.
   * An engine not offered is never listed and never enabled.
   */
  default boolean isOffered(SearchEngineID id) {
    return true;
  }

  /** Identifies the client to FrostClick. */
  UserAgent userAgent();

  /** SoundCloud's client id, fetched from FrostWire's servers; may change while running. */
  String soundCloudClientId();

  /** The SoundCloud web app version that goes with {@link #soundCloudClientId()}. */
  String soundCloudAppVersion();

  /** How this client reaches Telluride; null when it can't. */
  TellurideSearchFactory telluride();
}
